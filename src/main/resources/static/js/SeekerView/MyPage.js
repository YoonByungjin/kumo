document.addEventListener("DOMContentLoaded", function () {
    const modal = document.getElementById('profileModal');
    const btnOpenModal = document.getElementById('btnOpenModal');
    const btnCloseX = document.querySelector('.close-btn');
    const btnCancel = document.getElementById('btnCancel');
    const btnSave = document.getElementById('btnSaveImage');

    const fileInput = document.getElementById('fileInput');
    const modalPreview = document.getElementById('modalPreview');
    const currentProfileImg = document.getElementById('currentProfileImg');
    const fileNameSpan = document.getElementById('fileName');

    // [1] 프로필 모달 열기 & 초기화 (Seeker 로직 유지: 현재 이미지 미리보기에 반영)
    if (btnOpenModal) {
        btnOpenModal.addEventListener('click', function () {
            modal.style.display = "flex";
            if (currentProfileImg && modalPreview) {
                modalPreview.src = currentProfileImg.src;
            }
            fileInput.value = '';
            if (fileNameSpan) {
                fileNameSpan.innerText = typeof msg !== 'undefined' ? msg.fileNone : '선택된 파일 없음';
                fileNameSpan.style.color = "#888";
            }
        });
    }

    const closeModal = () => {
        modal.classList.add('closing');
        setTimeout(() => {
            modal.style.display = "none";
            modal.classList.remove('closing');
        }, 250);
    };

    if (btnCloseX) btnCloseX.addEventListener('click', closeModal);
    if (btnCancel) btnCancel.addEventListener('click', closeModal);

    // 바깥 클릭 시 닫기
    window.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // [2] 파일 선택 시 (미리보기 & 파일명 표시)
    if (fileInput) {
        fileInput.addEventListener('change', function (e) {
            const file = e.target.files[0];
            if (file) {
                if (fileNameSpan) {
                    fileNameSpan.innerText = file.name;
                    fileNameSpan.style.color = "#333";
                }
                const reader = new FileReader();
                reader.onload = function (evt) {
                    if (modalPreview) modalPreview.src = evt.target.result;
                };
                reader.readAsDataURL(file);
            }
        });
    }

    // [3] 서버 전송 (저장 버튼 - Recruiter의 Swal UI 적용)
    if (btnSave) {
        btnSave.addEventListener('click', function () {
            const file = fileInput.files[0];
            if (!file) {
                Swal.fire({
                    icon: 'warning',
                    title: typeof msg !== 'undefined' ? msg.selectPhoto : '사진을 선택해주세요.'
                });
                return;
            }

            const formData = new FormData();
            formData.append("profileImage", file);

            fetch('/api/profileImage', {
                method: 'POST',
                body: formData
            })
            .then(response => {
                if (response.ok) return response.text();
                throw new Error('FAILED');
            })
            .then(newImageUrl => {
                Swal.fire({
                    icon: 'success',
                    title: typeof msg !== 'undefined' ? msg.uploadSuccess : '프로필 사진이 변경되었습니다.'
                }).then(() => {
                    if (newImageUrl && currentProfileImg) {
                        currentProfileImg.src = newImageUrl;
                    }
                    closeModal();
                });
            })
            .catch(err => {
                Swal.fire({
                    icon: 'error',
                    title: typeof msg !== 'undefined' ? msg.error : '에러 발생',
                    text: err.message
                });
            });
        });
    }

    // [4] 소셜 연동 알림 (LINE, Google)
    document.querySelectorAll('.sns-toggle').forEach(toggle => {
        toggle.addEventListener('click', (e) => {
            e.preventDefault();
            const isDark = document.documentElement.classList.contains('dark-mode');
            Swal.fire({
                title: typeof snsMsg !== 'undefined' ? snsMsg.snsTitle : 'Service Notice',
                text: typeof snsMsg !== 'undefined' ? snsMsg.snsText : '아직 서비스 준비 중입니다.',
                icon: 'info',
                confirmButtonColor: '#7db4e6',
                background: isDark ? '#2b2b2b' : '#fff',
                color: isDark ? '#fff' : '#333'
            });
        });
    });
});
