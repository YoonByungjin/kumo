package net.kumo.kumo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 애플리케이션의 전역 웹 설정 및 리소스 경로를 지정하는 Configuration 클래스입니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.upload.chat}")
    private String chatUploadDir;

    /**
     * 외부 디렉토리에 업로드된 파일(일반 이미지, 채팅 이미지)을
     * 웹 URL을 통해 접근할 수 있도록 가상 경로와 물리적 경로를 매핑합니다.
     *
     * @param registry ResourceHandlerRegistry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 구인 공고 이미지: /images/uploadFile/** → {uploadDir}/
        registry.addResourceHandler("/images/uploadFile/**")
                .addResourceLocations("file:" + uploadDir);

        // 구직자 프로필 이미지 + 포트폴리오: /uploads/** → {uploadDir}/
        // (profileImage/, evidenceFiles/ 등 하위 폴더 포함)
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);

        // 리크루터 프로필 이미지: /upload/profiles/** → {uploadDir}/profiles/
        registry.addResourceHandler("/upload/profiles/**")
                .addResourceLocations("file:" + uploadDir + "profiles/");

        // 채팅방 첨부파일: /chat_images/** → {uploadDir}/chat_uploads/
        registry.addResourceHandler("/chat_images/**")
                .addResourceLocations("file:" + chatUploadDir);
    }
}