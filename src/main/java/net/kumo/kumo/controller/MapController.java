package net.kumo.kumo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kumo.kumo.domain.dto.ApplicationDTO;
import net.kumo.kumo.domain.dto.JobDetailDTO;
import net.kumo.kumo.domain.dto.JobSummaryDTO;
import net.kumo.kumo.domain.dto.ReportDTO;
import net.kumo.kumo.domain.entity.UserEntity;
import net.kumo.kumo.domain.entity.Enum;
import net.kumo.kumo.repository.UserRepository;
import net.kumo.kumo.service.MapService;
import net.kumo.kumo.service.ScrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("map")
@RequiredArgsConstructor
public class MapController {

    // Key
    @Value("${GOOGLE_MAPS_KEY}")
    private String googleMapKey;

    private final MapService mapService;
    private final ScrapService scrapService; // 🌟 추가: 찜하기 여부 확인용
    private final UserRepository userRepo;

    // --- 화면 반환 (View) ---

    /**
     * 지도 메인페이지 연결
     * 
     * @return 메인 페이지
     */
    @GetMapping("main")
    public String mainMap(Model model) {
        log.debug("메인화면 연결");

        model.addAttribute("googleMapsKey", googleMapKey);
        return "mainView/main";
    }

    /**
     * [VIEW] 구인 리스트 페이지 반환
     * 파일 위치: resources/templates/mapView/job_list.html
     */
    @GetMapping("/job-list-view")
    public String jobListPage() {
        return "mapView/job_list";
    }

    /**
     * 공고 상세 페이지 이동
     */
    @GetMapping("/jobs/detail")
    public String jobDetailPage(
            @RequestParam Long id,
            @RequestParam String source,
            @RequestParam(defaultValue = "kr") String lang,
            Principal principal,
            Model model) {

        // 1. 서비스에서 상세 데이터 조회
        JobDetailDTO job = mapService.getJobDetail(id, source, lang);
        boolean isOwner = false;
        boolean isSeeker = false;
        boolean isScraped = false; // 수정된 로직 Spring Security 기반 스크랩(찜하기) 여부 확인
        UserEntity user;

        // ★ [추가] 채팅방 연결을 위해 '로그인한 내 ID'를 저장할 공간
        Long loginUserId = null;

        // principal이 null이 아니면 로그인된 상태
        if (principal != null) {
            // principal.getName()은 보통 유저의 로그인 ID(email)를 반환합니다.
            String loginEmail = principal.getName();
            user = userRepo.findByEmail(loginEmail).orElse(null);

            if (user != null) {
                // ★ [추가] 내 진짜 ID 꺼내기!
                loginUserId = user.getUserId();

                isScraped = scrapService.checkIsScraped(user.getUserId(), id, source);
                // 공고 작성 id와 user의 id 를 비교하여 공고 작성자 동일 여부를 확인
                // geocoded 테이블 수정 후 코드 사용
                // isOwner = user.getUserId().equals(job.getUserId());
                isSeeker = (user.getRole() == Enum.UserRole.SEEKER);
            }
        }

        model.addAttribute("isScraped", isScraped);
        model.addAttribute("job", job);
        model.addAttribute("googleMapsKey", googleMapKey);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("isSeeker", isSeeker);
        model.addAttribute("lang", lang);

        // ★ [추가] HTML(뷰)로 내 ID 쏴주기!
        model.addAttribute("loginUserId", loginUserId);

        return "mapView/job_detail";
    }

    /**
     * 구인 신청 메서드
     */
    @PostMapping("/api/apply")
    @ResponseBody
    public ResponseEntity<String> applyForJob(@RequestBody ApplicationDTO.ApplyRequest dto, Principal principal) {
        // ★ 타입이 ApplicationDTO.ApplyRequest 로 변경됨!

        // 1. 로그인 검증
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 유저 정보 조회
        String loginEmail = principal.getName();
        UserEntity user = userRepo.findByEmail(loginEmail).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 사용자입니다.");
        }

        // 3. 구직자 권한(SEEKER) 확인
        if (user.getRole() != Enum.UserRole.SEEKER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("구직자(SEEKER) 계정만 지원할 수 있습니다.");
        }

        // 4. 서비스 호출 및 예외 처리
        try {
            mapService.applyForJob(user, dto);
            return ResponseEntity.ok("구인 신청이 완료되었습니다.");

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("지원 처리 중 오류 발생: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("지원 처리 중 서버 오류가 발생했습니다.");
        }
    }

    /**
     * [NEW] 공고 삭제 API (작성자 전용)
     */
    @DeleteMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<String> deleteJob(
            @RequestParam Long id,
            @RequestParam String source,
            Principal principal) {

        // 1. 로그인 검증
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 유저 정보 조회
        String loginEmail = principal.getName();
        UserEntity user = userRepo.findByEmail(loginEmail).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 사용자입니다.");
        }

        // 3. 서비스 호출 및 삭제 실행
        try {
            mapService.deleteJobPost(id, source, user);
            return ResponseEntity.ok("공고가 성공적으로 삭제되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {

            // 본인 공고가 아니거나, 공고가 존재하지 않을 때
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            log.error("공고 삭제 중 오류 발생: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("공고 삭제 중 오류가 발생했습니다.");
        }
    }

    // [NEW] 검색 리스트 관련 매핑
    /**
     * 1. [VIEW] 검색 리스트 페이지 반환
     */
    @GetMapping("/search_list")
    public String searchListPage() {
        // resources/templates/mapView/search_job_list.html 을 반환한다고 가정
        return "mapView/search_job_list";
    }

    // --- 데이터 반환 (API) ---

    // ★ [복구] 스프링 3.2 500 에러 폭탄 방지 이름표 달기!
    @GetMapping("/api/jobs")
    @ResponseBody
    public List<JobSummaryDTO> getJobListApi(
            @RequestParam("minLat") Double minLat,
            @RequestParam("maxLat") Double maxLat,
            @RequestParam("minLng") Double minLng,
            @RequestParam("maxLng") Double maxLng,
            @RequestParam(value = "lang", defaultValue = "kr") String lang) {
        // 서비스가 이미 정제된(JobResponse) 데이터를 줍니다.
        return mapService.getJobListInMap(minLat, maxLat, minLng, maxLng, lang);
    }

    /**
     * [NEW] 신고 접수 API
     * URL: /map/api/reports (주의: 클래스 매핑 "map" + 메소드 매핑 "/api/reports")
     * 프론트엔드 fetch 주소 수정 필요: fetch('/map/api/reports', ...)
     */
    @PostMapping("/api/reports")
    @ResponseBody
    public ResponseEntity<String> submitReport(@RequestBody ReportDTO reportDTO, Principal principal) {
        // ★ HttpSession 교체

        // 1. 로그인 체크
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 신고자 정보 조회
        String loginEmail = principal.getName();
        UserEntity user = userRepo.findByEmail(loginEmail).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 사용자입니다.");
        }

        // 3. 신고자 ID 설정 후 서비스 호출
        reportDTO.setReporterId(user.getUserId());
        mapService.createReport(reportDTO);

        return ResponseEntity.ok("신고가 정상적으로 접수되었습니다.");
    }

    /**
     * 2. [API] 검색 조건에 맞는 공고 리스트 데이터 반환
     * URL: /map/api/jobs/search
     */
    // ★ [복구] 스프링 3.2 500 에러 폭탄 방지 이름표 달기!
    @GetMapping("/api/jobs/search")
    @ResponseBody
    public List<JobDetailDTO> searchJobsApi(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "mainRegion", required = false) String mainRegion,
            @RequestParam(value = "subRegion", required = false) String subRegion,
            @RequestParam(value = "lang", defaultValue = "kr") String lang) {
        log.info("검색 API 호출됨 - keyword: {}, mainRegion: {}, subRegion: {}", keyword, mainRegion, subRegion);
        return mapService.searchJobsList(keyword, mainRegion, subRegion, lang);
    }
}