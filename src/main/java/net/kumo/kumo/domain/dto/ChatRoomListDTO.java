package net.kumo.kumo.domain.dto;

import lombok.*;

/**
 * 사용자의 채팅방 목록(Lobby) 화면을 렌더링하기 위해 필요한
 * 개별 채팅방의 요약 정보를 담는 DTO 클래스입니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomListDTO {

    private Long roomId;
    private String opponentNickname;
    private String opponentProfileImg;
    private String lastMessage;
    private String lastTime;
    private boolean hasUnread;
    private boolean pinned;
    /** 어떤 공고/스카우트로 시작된 방인지 표시할 서브타이틀 (예: "[스카우트]", "편의점 아르바이트") */
    private String jobContext;
    /** 최신순 정렬 기준 — 마지막 메시지 발신 시각 (메시지 없으면 null) */
    private java.time.LocalDateTime lastMessageAt;
    /** 이 방에서 본인이 읽지 않은 메시지 수 (뱃지 표시용) */
    private int roomUnreadCount;

}