let currentAppId = null;
let currentSeekerId = null;
let currentJobId = null;
let currentJobSource = null;

// =========================================================================
// 1. 모달창 데이터 불러오기 (이력서 렌더링)
// =========================================================================
async function loadResumeData(btn) {
    currentAppId = btn.getAttribute('data-app-id');
    currentSeekerId = btn.getAttribute('data-seeker-id');
    currentJobId = btn.getAttribute('data-job-id');
    currentJobSource = btn.getAttribute('data-source');

    document.getElementById('modalName').innerText = btn.getAttribute('data-name');
    document.getElementById('modalContact').innerText = btn.getAttribute('data-contact') || '-';
    document.getElementById('modalEmail').innerText = btn.getAttribute('data-email') || '-';
    document.getElementById('modalJob').innerText = btn.getAttribute('data-job');

    const modalBody = document.getElementById('dynamicResumeBody');
    modalBody.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary" role="status"></div><p class="mt-2 text-muted">이력서 데이터를 불러오는 중입니다...</p></div>`;

    try {
        const response = await fetch(`/Recruiter/api/resume/${currentSeekerId}`);
        if (!response.ok) throw new Error('데이터 로드 실패');
        const data = await response.json();
        setTimeout(() => { renderResume(data, modalBody); }, 300);
    } catch (error) {
        modalBody.innerHTML = `<div class="text-center text-danger py-5"><i class="bi bi-exclamation-triangle fs-1"></i><p class="mt-2">이력서를 불러오지 못했습니다.</p></div>`;
    }
}

function renderResume(data, container) {
    let html = '';
    if (data.profile) {
        html += `<div class="resume-section"><h5><i class="bi bi-person-lines-fill me-2"></i>자기소개 (<span class="text-primary">${data.profile.careerType || '미입력'}</span>)</h5><p class="text-secondary mb-0" style="line-height: 1.6; white-space: pre-wrap;">${data.profile.selfPr || '작성된 내용이 없습니다.'}</p></div>`;
    }
    if (data.condition) {
        html += `<div class="resume-section"><h5><i class="bi bi-check2-square me-2"></i>희망 근무 조건</h5><div class="spec-item"><span class="label">희망 직무</span> ${data.condition.desiredJob || '미입력'}</div><div class="spec-item"><span class="label">희망 급여</span> ${data.condition.salaryType || ''} ${data.condition.desiredSalary || '미입력'}</div></div>`;
    }
    html += `<div class="resume-section"><h5><i class="bi bi-briefcase-fill me-2"></i>경력 사항</h5>`;
    if (data.careers && data.careers.length > 0) {
        data.careers.forEach(c => {
            html += `<div class="border-bottom pb-2 mb-2 last-no-border"><div class="fw-bold fs-6">${c.companyName} <span class="text-muted small fw-normal ms-2">(${c.department || '-'})</span></div><div class="text-muted small">${c.startDate || ''} ~ ${c.endDate || '재직중'}</div><div class="mt-1 text-secondary" style="font-size: 0.9rem; white-space: pre-wrap;">${c.description || ''}</div></div>`;
        });
    } else { html += `<p class="text-muted small">등록된 경력이 없습니다.</p>`; }
    html += `</div><div class="row">`;

    html += `<div class="col-md-6"><div class="resume-section h-100"><h5><i class="bi bi-mortarboard-fill me-2"></i>최종 학력</h5>`;
    if (data.education) {
        let e = data.education;
        let statusKr = e.status === 'GRADUATED' ? '졸업' : (e.status === 'ATTENDING' ? '재학' : (e.status === 'EXPECTED' ? '졸업예정' : '중퇴'));
        html += `<div class="spec-item mb-2"><span class="fw-bold d-block">${e.schoolName}</span> <span class="text-muted small">${e.major || ''} (${statusKr})</span></div>`;
    } else { html += `<p class="text-muted small">등록된 학력이 없습니다.</p>`; }
    html += `</div></div>`;

    html += `<div class="col-md-6"><div class="resume-section h-100"><h5><i class="bi bi-award-fill me-2"></i>자격 및 어학</h5>`;
    let certsExist = false;
    if (data.certificates && data.certificates.length > 0) {
        data.certificates.forEach(c => { html += `<div class="spec-item mb-2"><span class="fw-bold d-block">${c.certName}</span> <span class="text-muted small">${c.issuer || '-'} (${c.acquisitionYear || '-'}년)</span></div>`; });
        certsExist = true;
    }
    if (data.languages && data.languages.length > 0) {
        data.languages.forEach(l => { html += `<div class="spec-item mb-2"><span class="fw-bold d-block">${l.language}</span> <span class="text-muted small">Level: ${l.level || '-'}</span></div>`; });
        certsExist = true;
    }
    if (!certsExist) { html += `<p class="text-muted small">등록된 자격증이나 어학 정보가 없습니다.</p>`; }
    html += `</div></div></div>`;

    if (data.documents && data.documents.length > 0) {
        html += `<div class="resume-section mt-3"><h5><i class="bi bi-file-earmark-pdf-fill me-2"></i>첨부 파일</h5>`;
        data.documents.forEach(d => { html += `<a href="${d.fileUrl}" class="btn btn-sm btn-light border text-primary me-2 mb-2" target="_blank"><i class="bi bi-download me-1"></i> ${d.fileName}</a>`; });
        html += `</div>`;
    }
    container.innerHTML = html;
}

// =========================================================================
// 2. 합격/불합격 처리 (비동기)
// =========================================================================
async function updateAppStatus(status) {
    if (!currentAppId) return;

    const btnApprove = document.getElementById('btnApprove');
    const btnReject = document.getElementById('btnReject');
    btnApprove.disabled = true; btnReject.disabled = true;

    try {
        const res = await fetch(`/Recruiter/api/application/${currentAppId}/status?status=${status}`, {
            method: 'POST'
        });

        if (res.ok) {
            const modalEl = document.getElementById('resumeModal');
            const modal = bootstrap.Modal.getInstance(modalEl);
            modal.hide();

            const row = document.getElementById(`app-row-${currentAppId}`);
            const tbody = row.parentNode;

            const badgeContainer = row.querySelector('.status-cell');
            if (status === 'PASSED') {
                badgeContainer.innerHTML = '<span class="badge bg-success">합격</span>';
            } else {
                badgeContainer.innerHTML = '<span class="badge bg-secondary">불합격</span>';
            }
            row.classList.add('processed-row');
            tbody.appendChild(row);

        } else {
            alert("상태 업데이트에 실패했습니다.");
        }
    } catch (e) {
        console.error(e);
        alert("서버 통신 중 오류가 발생했습니다.");
    } finally {
        btnApprove.disabled = false; btnReject.disabled = false;
    }
}

// =========================================================================
// 3. 플로팅 채팅방 관련 로직
// =========================================================================
function actionChat() {
    if (!currentSeekerId || !currentJobId || !currentJobSource) {
        Swal.fire({ icon: 'error', title: '오류', text: '채팅방 생성에 필요한 대상 정보가 없습니다.' });
        return;
    }

    const chatUrl = `/chat/create?seekerId=${currentSeekerId}&jobPostId=${currentJobId}&jobSource=${currentJobSource}`;
    const chatContainer = document.getElementById('floatingChatContainer');
    const chatFrame = document.getElementById('floatingChatFrame');

    chatFrame.src = chatUrl;
    chatContainer.style.display = 'flex';
    chatContainer.classList.remove('minimized');

    const resumeModalEl = document.getElementById('resumeModal');
    if (resumeModalEl) {
        const resumeModal = bootstrap.Modal.getInstance(resumeModalEl);
        if (resumeModal) resumeModal.hide();
    }
}

function toggleMinimizeChat() {
    const container = document.getElementById('floatingChatContainer');
    container.classList.toggle('minimized');
}

function closeFloatingChat() {
    const container = document.getElementById('floatingChatContainer');
    const chatFrame = document.getElementById('floatingChatFrame');

    container.style.display = 'none';
    chatFrame.src = ''; // 웹소켓 연결 해제
}

// 플로팅 창 마우스 드래그 이벤트 등록
document.addEventListener('DOMContentLoaded', () => {
    const chatContainer = document.getElementById('floatingChatContainer');
    const chatHeader = document.getElementById('floatingChatHeader');

    if (!chatContainer || !chatHeader) return;

    let isDragging = false;
    let dragOffsetX, dragOffsetY;

    chatHeader.addEventListener('mousedown', (e) => {
        isDragging = true;
        const rect = chatContainer.getBoundingClientRect();
        dragOffsetX = e.clientX - rect.left;
        dragOffsetY = e.clientY - rect.top;

        chatContainer.style.transition = 'none';
    });

    document.addEventListener('mousemove', (e) => {
        if (!isDragging) return;

        let newX = e.clientX - dragOffsetX;
        let newY = e.clientY - dragOffsetY;

        chatContainer.style.bottom = 'auto';
        chatContainer.style.right = 'auto';
        chatContainer.style.left = newX + 'px';
        chatContainer.style.top = newY + 'px';
    });

    document.addEventListener('mouseup', () => {
        if (isDragging) {
            isDragging = false;
            chatContainer.style.transition = 'height 0.3s ease';
        }
    });
});