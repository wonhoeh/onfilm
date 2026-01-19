
    const heroArea = document.getElementById("heroArea");
    const heroThumb = document.getElementById("heroThumb");
    const muteBtn = document.getElementById("muteBtn");

    /* --------------------------
       HERO MUTE
    -------------------------- */
    const heroVideo = document.getElementById("heroVideo");
    document.getElementById("muteBtn").onclick = () => {
        heroVideo.muted = !heroVideo.muted;
        muteBtn.textContent = heroVideo.muted ? "🔇" : "🔊";
    };

    /* HERO HOVER */
    heroArea.addEventListener("mouseenter", () => {
        heroThumb.style.opacity = "0";
        heroVideo.play();
    });
    heroArea.addEventListener("mouseleave", () => {
        heroVideo.pause();
        heroThumb.style.opacity = "1";
    });



    /* --------------------------
       SLIDER (GENERIC)
    -------------------------- */
    function setupSlider(sliderId, prevId, nextId){
        const slider = document.getElementById(sliderId);
        const prev = document.getElementById(prevId);
        const next = document.getElementById(nextId);

        let idx = 0; // 현재 "첫 카드 인덱스"

        function cardWidth(){
            const c = slider.children[0];
            if (!c) return 0;
            return c.offsetWidth + 16; // 카드 간 gap 16px
        }

        // 현재 화면에서 몇 개가 보이는지 계산
        function getVisibleCount() {
            const wrapper = slider.parentElement; // .slider-wrapper
            const cw = cardWidth();
            if (!cw) return 1;
            const count = Math.floor(wrapper.offsetWidth / cw);
            return Math.max(1, count);
        }

        // 실제 이동 처리
        function goTo(newIdx) {
            const visible = getVisibleCount();
            const maxIdx = Math.max(0, slider.children.length - visible);
            idx = Math.min(Math.max(newIdx, 0), maxIdx);
            slider.style.transform = `translateX(${-idx * cardWidth()}px)`;
        }

        next.onclick = () => {
            const visible = getVisibleCount();
            goTo(idx + visible); // 👉 n개만큼 이동
        };

        prev.onclick = () => {
            const visible = getVisibleCount();
            goTo(idx - visible); // 👉 n개만큼 이동
        };

        prev.addEventListener("mouseenter", hidePopup);
        next.addEventListener("mouseenter", hidePopup);

        // 창 크기 변경 시에도 위치 재계산
        window.addEventListener("resize", () => {
            goTo(idx);
        });
    }

    // 호출부도 살짝 변경 (visible 파라미터 제거)
    setupSlider("movieSlider", "moviePrev", "movieNext");
    setupSlider("actorSlider", "actorPrev", "actorNext");



    /* --------------------------
       MOVIE POPUP (NETFLIX A안)
    -------------------------- */
    let popup = null;
    let hideTimer = null;
    let activeCard = null;

    function createPopup() {
        popup = document.createElement("div");
        popup.className = "movie-popup";

        popup.innerHTML = `
        <div class="popup-video-box">
          <video muted loop></video>

          <div class="popup-mute-btn">
            <span class="mute-icon">🔇</span>
          </div>
        </div>

        <div class="popup-info">
          <div class="popup-title"></div>
          <div class="popup-meta"></div>

          <div class="popup-action-row">
            <button class="popup-play-btn-circle"><span>▶</span></button>
            <button class="popup-like-btn"><span>👍</span></button>
          </div>
        </div>
        `;

        document.body.appendChild(popup);

        popup.addEventListener("mouseenter", () => {
            if (hideTimer) clearTimeout(hideTimer);
        });

        popup.addEventListener("mouseleave", hidePopup);

        popup.querySelector(".popup-mute-btn").onclick = (e) => {
            e.stopPropagation();
            const v = popup.querySelector("video");
            v.muted = !v.muted;
            popup.querySelector(".mute-icon").textContent = v.muted ? "🔇" : "🔊";
        };

        popup.querySelector(".popup-play-btn-circle").onclick = (e) => {
            e.stopPropagation();
            sessionStorage.setItem("allowSoundPlay", "true");
            window.location.href = "video-player-temp.html";
        };

        /* 👍 좋아요 버튼 */
        const likeBtn = popup.querySelector(".popup-like-btn");
        likeBtn.onclick = (e) => {
            e.stopPropagation();
            likeBtn.classList.toggle("liked");

            if (likeBtn.classList.contains("liked")) {
                likeBtn.style.transform = "scale(1.25)";
                setTimeout(() => {
                    likeBtn.style.transform = "scale(1)";
                }, 250);
            }
        };

        /* 팝업 전체 클릭 → 상세 페이지 이동 */
        popup.addEventListener("click", () => {
            window.location.href = "movie-detail.html";
        });
    }


    createPopup();



    function showPopup(card){
        if (hideTimer) clearTimeout(hideTimer);
        activeCard = card;

        const rect = card.getBoundingClientRect();
        const videoEl = popup.querySelector("video");

        popup.querySelector(".popup-title").textContent = card.dataset.title;
        popup.querySelector(".popup-meta").innerHTML = `
        ${card.dataset.genre} · ${card.dataset.runtime}
        <span class="popup-age">${card.dataset.age}</span>
        `;

        videoEl.src = card.dataset.video;

        // 💡 화면이 좁으면 팝업 폭을 화면에 맞춰 조절
        const popupWidth = Math.min(340, window.innerWidth - 20); // 좌우 10px 여백
        popup.style.width = popupWidth + "px";

        popup.style.opacity = "0";
        popup.style.transform = "scale(0.7) translateY(20px)";

        let left = rect.left + (rect.width / 2) - (popupWidth / 2);
        let top = rect.top - 10;

        // 좌우 화면 밖으로 나가지 않게 보정
        if (left < 10) left = 10;
        if (left + popupWidth > window.innerWidth - 10) {
            left = window.innerWidth - popupWidth - 10;
        }

        // 위로 너무 나가면 카드 아래쪽에 붙이기
        if (top < 10) {
            top = rect.bottom + 10;
        }

        popup.style.left = `${left}px`;
        popup.style.top = `${top}px`;

        popup.classList.add("show");

        requestAnimationFrame(() => {
            popup.style.opacity = "1";
            popup.style.transform = "scale(1) translateY(0)";
        });

        videoEl.currentTime = 0;
        videoEl.play();
    }

    function hidePopup(){
        hideTimer = setTimeout(() => {
            popup.style.opacity = "0";
            popup.style.transform = "scale(0.85) translateY(10px)";
            popup.querySelector("video").pause();

            setTimeout(() => popup.classList.remove("show"), 180);

            activeCard = null;
        }, 120);
    }

    let popupDelayTimer = null;

    /* 카드 이벤트 등록 */
    document.querySelectorAll(".movie-card").forEach(card => {
        card.addEventListener("mouseenter", () => {
            popupDelayTimer = setTimeout(() => {
                showPopup(card);
            }, 350); // ← 350ms 지연 (추천값)
        });

        card.addEventListener("mouseleave", () => {
            clearTimeout(popupDelayTimer); // ← 마우스 떠나면 팝업 예약 취소
            hidePopup();                   // ← 이미 떠 있는 팝업은 즉시 닫기
        });
    });

    /* ==========================
    MOVIE CARD CLICK → 상세 페이지 이동
    ========================== */
    document.querySelectorAll(".movie-card").forEach(card => {
        card.addEventListener("click", () => {
            // 1) 이동할 상세페이지의 파일명
            const detailUrl = "movie-detail.html";

            // 2) 추후 영화 id 기반 라우팅
            // const movieId = card.dataset.id;
            // location.href = `/movies/${movieId}`;

            window.location.href = detailUrl;
        });
    });

    /* ==========================
    HEADER NAVIGATION
    ========================== */

    // 홈 클릭 → main
    document.querySelector("nav ul li:nth-child(1)").addEventListener("click", () => {
        window.location.href = "main.html";
    });

    // 배우 클릭 → actor-list
    document.querySelector("nav ul li:nth-child(2)").addEventListener("click", () => {
        window.location.href = "actors.html";
    });

    // 영화 클릭 → movie-list
    document.querySelector("nav ul li:nth-child(3)").addEventListener("click", () => {
        window.location.href = "movies.html";
    });

    /* ==========================
       ACTOR CARD CLICK
    ========================== */
    document.querySelectorAll(".actor-card").forEach(card => {
        card.addEventListener("click", () => {
            window.location.href = "actor-detail.html";
        });
    });

    <!-- 메인 트레일러 재생 버튼 -->
    document.querySelector(".btn-play").addEventListener("click", () => {
        sessionStorage.setItem("allowSoundPlay", "true");
        window.location.href = "video-player.html";
    });

    <!-- 메인 트레일러 자세히 보기 버튼 -->
    document.querySelector(".btn-info").addEventListener("click", () => {
        window.location.href = "movie-detail.html";
    })



