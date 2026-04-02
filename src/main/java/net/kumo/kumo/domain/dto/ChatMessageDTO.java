package net.kumo.kumo.domain.dto;

import lombok.*;

/**
 * 채팅방 내부에서 송수신되는 개별 메시지(텍스트, 이미지, 파일 등)의
 * 상세 데이터와 메타 정보를 담아 전달하기 위한 DTO 클래스입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {

    private Long roomId;
    private Long senderId;

    /** 화면에 출력할 발신자의 닉네임 */
    private String senderNickname;

    /** 메시지 본문 (텍스트 내용 또는 파일/이미지 URL) */
    private String content;

    /** 메시지의 유형 (예: TEXT, IMAGE, FILE, READ 등) */
    private String messageType;

    private String fileName;

    /** 메시지 포맷팅에 사용될 언어 코드 (예: "kr", "ja") */
    private String lang;

    /** 시/분 단위로 포맷팅된 메시지 발송 시간 (예: "17:05") */
    private String createdAt;

    /** 채팅창 내 날짜 변경선(Divider) 렌더링을 위한 포맷팅된 날짜 정보 */
    private String createdDate;

    /** 상대방의 메시지 수신(읽음) 여부 */
    private boolean isRead;

    /** /sub/chat/user/ 채널 broadcast 시 수신자의 전체 미읽음 개수 (사이드바 뱃지용) */
    private Integer unreadCount;

    /** /sub/chat/user/ 채널 broadcast 시 해당 채팅방의 미읽음 개수 (리스트 뱃지용) */
    private Integer roomUnreadCount;

    // --- /sub/chat/user/ 채널 전용: 채팅 목록 신규 아이템 생성에 필요한 상대방 정보 ---

    /** 수신자 입장에서의 상대방 닉네임 */
    private String opponentNickname;

    /** 수신자 입장에서의 상대방 프로필 이미지 URL */
    private String opponentProfileImg;

    /** 채팅방에 연결된 공고명 또는 스카우트 레이블 */
    private String jobContext;

}