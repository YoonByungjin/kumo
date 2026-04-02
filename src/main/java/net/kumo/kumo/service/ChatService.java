package net.kumo.kumo.service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.kumo.kumo.domain.dto.ChatMessageDTO;
import net.kumo.kumo.domain.dto.ChatRoomListDTO;
import net.kumo.kumo.domain.entity.ChatMessageEntity;
import net.kumo.kumo.domain.entity.ChatRoomEntity;
import net.kumo.kumo.domain.entity.Enum.MessageType;
import net.kumo.kumo.domain.entity.UserEntity;
import net.kumo.kumo.repository.ChatMessageRepository;
import net.kumo.kumo.repository.ChatRoomRepository;
import net.kumo.kumo.repository.OsakaGeocodedRepository;
import net.kumo.kumo.repository.TokyoGeocodedRepository;
import net.kumo.kumo.repository.UserRepository;

/**
 * 1:1 실시간 채팅과 관련된 핵심 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 채팅방 세션 생성, 대화 기록 조회, 소켓 메시지 영속화 및 읽음 처리 신호 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final OsakaGeocodedRepository osakaGeocodedRepository;
    private final TokyoGeocodedRepository tokyoGeocodedRepository;

    /**
     * 구직자와 구인자 간의 채팅방 세션을 생성하거나,
     * 동일 공고에 대해 이미 열려 있는 기존 채팅방을 반환합니다.
     *
     * @param seekerId     참여하는 구직자 계정 식별자
     * @param recruiterId  참여하는 구인자 계정 식별자
     * @param targetPostId 대화의 기준이 되는 연관 공고 식별자
     * @param targetSource 연관 공고의 데이터 출처 지역
     * @return 생성 혹은 조회된 채팅방 엔티티
     * @throws IllegalArgumentException 참여 사용자를 찾을 수 없을 때 발생
     */
    public ChatRoomEntity createOrGetChatRoom(Long seekerId, Long recruiterId, Long targetPostId, String targetSource) {

        Optional<ChatRoomEntity> existingRoom = chatRoomRepository
                .findBySeeker_UserIdAndRecruiter_UserIdAndTargetPostIdAndTargetSource(
                        seekerId, recruiterId, targetPostId, targetSource);

        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        UserEntity seeker = userRepository.findById(seekerId)
                .orElseThrow(() -> new IllegalArgumentException("구직자를 찾을 수 없습니다."));

        UserEntity recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new IllegalArgumentException("구인자를 찾을 수 없습니다."));

        ChatRoomEntity newRoom = ChatRoomEntity.builder()
                .seeker(seeker)
                .recruiter(recruiter)
                .targetPostId(targetPostId)
                .targetSource(targetSource)
                .build();

        return chatRoomRepository.save(newRoom);
    }

    /**
     * 식별자를 기반으로 특정 채팅방 세션의 상세 정보를 조회합니다.
     *
     * @param roomId 조회할 채팅방 식별자
     * @return 조회된 채팅방 엔티티
     * @throws IllegalArgumentException 채팅방이 존재하지 않을 때 발생
     */
    @Transactional(readOnly = true)
    public ChatRoomEntity getChatRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));
    }

    /**
     * 웹소켓을 통해 실시간으로 수신된 발신자의 개별 채팅 메시지를
     * 데이터베이스에 영속화(저장)합니다.
     *
     * @param dto 저장할 메시지 페이로드 정보가 담긴 DTO
     * @return 저장이 완료되어 식별자가 부여된 메시지 DTO
     */
    public ChatMessageDTO saveMessage(ChatMessageDTO dto) {
        ChatRoomEntity room = getChatRoom(dto.getRoomId());

        UserEntity sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .room(room)
                .sender(sender)
                .content(dto.getContent())
                .messageType(MessageType.valueOf(dto.getMessageType()))
                .isRead(false)
                .build();

        ChatMessageEntity saved = chatMessageRepository.save(entity);
        return convertToDTO(saved, dto.getLang());
    }

    /**
     * 특정 채팅방 내부의 누적된 과거 메시지 대화 기록을 전체 조회합니다.
     * 기록 조회 시, 해당 사용자가 읽지 않은 이전 메시지들에 대해 일괄 읽음 처리를 수행합니다.
     *
     * @param roomId 채팅방 식별자
     * @param userId 기록을 요청한 사용자(본인) 식별자
     * @param lang   날짜 구분선 생성에 적용할 사용자 언어 코드
     * @return 포맷팅이 적용된 과거 메시지 DTO 리스트
     */
    public List<ChatMessageDTO> getMessageHistory(Long roomId, Long userId, String lang) {
        chatMessageRepository.markMessagesAsRead(roomId, userId);

        return chatMessageRepository.findByRoom_IdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(entity -> convertToDTO(entity, lang))
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자가 참여하고 있는 모든 채팅방 목록을 조회합니다.
     * 상대방의 정보 및 가장 최근 송수신된 메시지, 미확인 메시지 존재 여부를 함께 제공합니다.
     *
     * @param userId 조회를 요청한 사용자 식별자
     * @return 로비 화면에 렌더링될 채팅방 요약 DTO 리스트
     */
    @Transactional(readOnly = true)
    public List<ChatRoomListDTO> getChatRoomsForUser(Long userId) {

        List<ChatRoomEntity> rooms = chatRoomRepository.findChatRoomsByUserId(userId);

        return rooms.stream().map(room -> {
            UserEntity opponent = room.getSeeker().getUserId().equals(userId) ? room.getRecruiter() : room.getSeeker();

            String profileImg = "/images/common/default_profile.png";
            if (opponent.getProfileImage() != null && opponent.getProfileImage().getFileUrl() != null) {
                profileImg = opponent.getProfileImage().getFileUrl();
            }

            ChatMessageEntity lastMsg = chatMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room);
            boolean hasUnreadFlag = chatMessageRepository.existsByRoomAndSender_UserIdNotAndIsReadFalse(room, userId);

            boolean isPinned = room.getSeeker().getUserId().equals(userId)
                    ? room.isSeekerPinned() : room.isRecruiterPinned();

            String jobContext = resolveJobContext(room.getTargetSource(), room.getTargetPostId());

            java.time.LocalDateTime lastMsgAt = lastMsg != null ? lastMsg.getCreatedAt() : null;
            int roomUnreadCount = chatMessageRepository.countUnreadByRoomForUser(room.getId(), userId);

            return ChatRoomListDTO.builder()
                    .roomId(room.getId())
                    .opponentNickname(opponent.getNickname())
                    .opponentProfileImg(profileImg)
                    .lastMessage(lastMsg != null ? lastMsg.getContent()
                            : messageSource.getMessage("chat.last.message.empty", null,
                            LocaleContextHolder.getLocale()))
                    .lastTime(lastMsg != null ? lastMsg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")) : "")
                    .hasUnread(hasUnreadFlag)
                    .pinned(isPinned)
                    .jobContext(jobContext)
                    .lastMessageAt(lastMsgAt)
                    .roomUnreadCount(roomUnreadCount)
                    .build();
        })
        // 핀고정 우선, 동일 그룹 내에서는 최신 메시지 순 (null은 맨 뒤)
        .sorted(Comparator.comparing(ChatRoomListDTO::isPinned).reversed()
                .thenComparing(Comparator.comparing(ChatRoomListDTO::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))))
        .collect(Collectors.toList());
    }

    /**
     * 웹소켓 연결 중 상대방이 내 메시지를 열람했을 때 발생하는
     * 실시간 읽음 처리(Read Receipt) 신호를 반영합니다.
     *
     * @param roomId   상태를 갱신할 채팅방 식별자
     * @param readerId 메시지를 읽은 사용자(수신자) 식별자
     */
    public void processLiveReadSignal(Long roomId, Long readerId) {
        chatMessageRepository.markMessagesAsRead(roomId, readerId);
    }

    /**
     * 요청한 사용자가 해당 채팅방의 참여자인지 검증한 뒤, 메시지를 먼저 삭제하고 채팅방을 삭제합니다.
     *
     * @param roomId 삭제할 채팅방 식별자
     * @param userId 삭제를 요청한 사용자 식별자
     * @throws SecurityException 해당 방의 참여자가 아닌 경우
     */
    /**
     * 삭제 후 상대방에게 WebSocket 알림을 보낼 수 있도록 상대방 userId를 반환합니다.
     */
    @Transactional
    public Long deleteRoom(Long roomId, Long userId) {
        ChatRoomEntity room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        boolean isParticipant = room.getSeeker().getUserId().equals(userId)
                || room.getRecruiter().getUserId().equals(userId);
        if (!isParticipant) {
            throw new SecurityException("채팅방 삭제 권한이 없습니다.");
        }

        Long opponentId = room.getSeeker().getUserId().equals(userId)
                ? room.getRecruiter().getUserId()
                : room.getSeeker().getUserId();

        chatMessageRepository.deleteByRoom(room);
        chatRoomRepository.delete(room);
        return opponentId;
    }

    /**
     * 특정 사용자의 채팅방 고정(핀) 상태를 토글합니다.
     *
     * @param roomId 대상 채팅방 식별자
     * @param userId 요청한 사용자 식별자
     * @return 토글 후 고정 상태 (true: 고정됨)
     * @throws SecurityException 해당 방의 참여자가 아닌 경우
     */
    @Transactional
    public boolean togglePin(Long roomId, Long userId) {
        ChatRoomEntity room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        if (room.getSeeker().getUserId().equals(userId)) {
            room.setSeekerPinned(!room.isSeekerPinned());
            return room.isSeekerPinned();
        } else if (room.getRecruiter().getUserId().equals(userId)) {
            room.setRecruiterPinned(!room.isRecruiterPinned());
            return room.isRecruiterPinned();
        } else {
            throw new SecurityException("채팅방 접근 권한이 없습니다.");
        }
    }

    /**
     * 글로벌 네비게이션 헤더 뱃지 등에 표시하기 위해,
     * 특정 사용자가 수신한 전체 채팅방의 미확인 메시지 총합을 계산합니다.
     *
     * @param userId 확인할 사용자 식별자
     * @return 미확인 채팅 메시지 총 개수
     */
    @Transactional(readOnly = true)
    public int getUnreadMessageCount(Long userId) {
        return chatMessageRepository.countUnreadMessagesForUser(userId);
    }

    /**
     * DB에서 조회된 메시지 엔티티를 클라이언트 통신 규격에 맞는 DTO로 변환합니다.
     * 채팅 화면의 날짜 구분선(Divider) 출력을 위해 다국어 일자 포맷을 적용합니다.
     *
     * @param entity 변환할 원본 메시지 엔티티
     * @param lang   적용할 다국어 언어 코드
     * @return 변환이 완료된 메시지 DTO
     */
    private ChatMessageDTO convertToDTO(ChatMessageEntity entity, String lang) {
        java.util.Locale locale = "ja".equals(lang) ? java.util.Locale.JAPANESE : java.util.Locale.KOREAN;
        String datePattern = "ja".equals(lang) ? "yyyy年 M月 d日 EEEE" : "yyyy년 M월 d일 EEEE";

        return ChatMessageDTO.builder()
                .roomId(entity.getRoom().getId())
                .senderId(entity.getSender() != null ? entity.getSender().getUserId() : null)
                .senderNickname(entity.getSender() != null ? entity.getSender().getNickname()
                        : messageSource.getMessage("chat.sender.unknown", null, LocaleContextHolder.getLocale()))
                .content(entity.getContent())
                .messageType(entity.getMessageType().name())
                .createdAt(entity.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")))
                .createdDate(
                        entity.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern(datePattern, locale)))
                .isRead(entity.getIsRead())
                .build();
    }

    /**
     * targetSource와 targetPostId를 기반으로 채팅 목록 서브타이틀용 공고명을 반환합니다.
     * SCOUT이면 고정 레이블, OSAKA/TOKYO면 해당 공고의 제목을 조회합니다.
     */
    /**
     * 메시지 전송 후 /sub/chat/user/ 채널에 브로드캐스팅할 DTO를
     * 트랜잭션 내에서 안전하게 생성합니다. (LazyInitializationException 방지)
     *
     * @param savedMessage 저장된 메시지 DTO
     * @return key=수신자 userId, value=해당 수신자용 ChatMessageDTO
     */
    @Transactional(readOnly = true)
    public Map<Long, ChatMessageDTO> buildUserChannelPayloads(ChatMessageDTO savedMessage) {
        ChatRoomEntity room = chatRoomRepository.findById(savedMessage.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        UserEntity seeker    = room.getSeeker();
        UserEntity recruiter = room.getRecruiter();
        Long seekerId    = seeker.getUserId();
        Long recruiterId = recruiter.getUserId();

        String seekerImg = (seeker.getProfileImage() != null && seeker.getProfileImage().getFileUrl() != null)
                ? seeker.getProfileImage().getFileUrl() : "/images/common/default_profile.png";
        String recruiterImg = (recruiter.getProfileImage() != null && recruiter.getProfileImage().getFileUrl() != null)
                ? recruiter.getProfileImage().getFileUrl() : "/images/common/default_profile.png";

        String jobContext = resolveJobContext(room.getTargetSource(), room.getTargetPostId());

        Map<Long, ChatMessageDTO> result = new HashMap<>();

        Long roomId = savedMessage.getRoomId();

        // 시커 채널: 상대방은 리크루터
        result.put(seekerId, ChatMessageDTO.builder()
                .roomId(roomId).senderId(savedMessage.getSenderId())
                .content(savedMessage.getContent()).messageType(savedMessage.getMessageType())
                .createdAt(savedMessage.getCreatedAt())
                .unreadCount(chatMessageRepository.countUnreadMessagesForUser(seekerId))
                .roomUnreadCount(chatMessageRepository.countUnreadByRoomForUser(roomId, seekerId))
                .opponentNickname(recruiter.getNickname())
                .opponentProfileImg(recruiterImg)
                .jobContext(jobContext)
                .build());

        // 리크루터 채널: 상대방은 시커
        result.put(recruiterId, ChatMessageDTO.builder()
                .roomId(roomId).senderId(savedMessage.getSenderId())
                .content(savedMessage.getContent()).messageType(savedMessage.getMessageType())
                .createdAt(savedMessage.getCreatedAt())
                .unreadCount(chatMessageRepository.countUnreadMessagesForUser(recruiterId))
                .roomUnreadCount(chatMessageRepository.countUnreadByRoomForUser(roomId, recruiterId))
                .opponentNickname(seeker.getNickname())
                .opponentProfileImg(seekerImg)
                .jobContext(jobContext)
                .build());

        return result;
    }

    public String resolveJobContext(String source, Long postId) {
        java.util.Locale locale = LocaleContextHolder.getLocale();
        boolean isJa = "ja".equals(locale.getLanguage());

        if ("SCOUT".equalsIgnoreCase(source)) {
            return isJa ? "スカウト専用チャットルーム" : "스카우트 전용 채팅방";
        }

        String titlePrefix = isJa ? "求人タイトル : " : "게시글 제목 : ";

        try {
            if ("OSAKA".equalsIgnoreCase(source)) {
                return osakaGeocodedRepository.findById(postId)
                        .map(e -> titlePrefix + (isJa ? e.getTitleJp() : e.getTitle())).orElse("");
            } else if ("TOKYO".equalsIgnoreCase(source)) {
                return tokyoGeocodedRepository.findById(postId)
                        .map(e -> titlePrefix + (isJa ? e.getTitleJp() : e.getTitle())).orElse("");
            }
        } catch (Exception ignored) {}
        return "";
    }
}