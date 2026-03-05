/* --- src/main/resources/static/js/recruiter/rightSidebar.js --- */

// 🌟 사이드바 전역 채팅 목록 열기 함수
function openGlobalChatList() {
    // 1. 플로팅 컨테이너와 iframe 요소 가져오기
    const chatContainer = document.getElementById('floatingChatContainer');
    const chatFrame = document.getElementById('floatingChatFrame');

    // 요소가 없으면 에러 처리 (공통 레이아웃에 플로팅 코드가 빠져있을 경우 대비)
    if (!chatContainer || !chatFrame) {
        console.error("🚨 플로팅 채팅창 HTML 요소가 현재 페이지에 없습니다.");
        alert("채팅창을 열 수 없습니다. (현재 페이지에 채팅 모듈이 없습니다.)");
        return;
    }

    // 2. iframe에 채팅 목록 URL 연결
    // (컨트롤러가 현재 로그인된 사용자의 세션을 통해 자동으로 userId를 찾도록 세팅됨)
    chatFrame.src = "/chat/list";

    // 3. 플로팅 모달 화면에 띄우기
    chatContainer.style.display = 'flex';
    chatContainer.classList.remove('minimized');
}