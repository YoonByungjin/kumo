package net.kumo.kumo.service;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.kumo.kumo.domain.entity.CompanyEntity;
import net.kumo.kumo.domain.entity.UserEntity;
import net.kumo.kumo.repository.CompanyRepository;
import net.kumo.kumo.repository.OsakaGeocodedRepository;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final OsakaGeocodedRepository osakaGeocodedRepository;

    @Autowired
    private final MessageSource messageSource;

    // 회사 목록 조회
    public List<CompanyEntity> getCompanyList(UserEntity user) {
        return companyRepository.findAllByUser(user);
    }

    // 단일 회사 조회
    public CompanyEntity getCompany(Long id) {
        return companyRepository.findById(id).orElse(null);
    }

    // 저장 및 수정
    @Transactional
    public void saveCompany(CompanyEntity company, UserEntity user) {
        company.setUser(user); // 어떤 유저의 회사인지 연결
        companyRepository.save(company);
    }

    // 삭제
    public void deleteCompany(Long companyId) {
        long count = osakaGeocodedRepository.countByCompany_CompanyId(companyId);

        if (count > 0) {
            // 🌟 1. 현재 브라우저(사용자)의 언어 설정 가져오기
            Locale currentLocale = LocaleContextHolder.getLocale();

            // 🌟 2. MessageSource로 번역본 가져오기 (count를 배열에 담아서 전달!)
            String errorMessage = messageSource.getMessage(
                    "error.company.delete.inUse",
                    new Object[] { count }, // {0} 자리에 들어갈 숫자!
                    currentLocale);

            throw new IllegalStateException(errorMessage);
        }

        companyRepository.deleteById(companyId);
    }
}