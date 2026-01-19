
  /* 요소 참조 */
  const video = document.getElementById("video");
  const playPause = document.getElementById("playPause");
  const back10 = document.getElementById("back10");
  const forward10 = document.getElementById("forward10");
  const volumeBtn = document.getElementById("volumeBtn");
  const fullscreenBtn = document.getElementById("fullscreenBtn");
  const controls = document.getElementById("controls");
  const topLeftInfo = document.querySelector(".top-left-info");
  const timeDisplay = document.getElementById("timeDisplay");
  const speedBtn = document.getElementById("speedBtn");
  const speedMenu = document.getElementById("speedMenu");
  const timeline = document.getElementById("timeline");
  const timelineProgress = document.getElementById("timelineProgress");
  const backBtn = document.getElementById("backBtn");
  const centerIndicator = document.getElementById("centerIndicator");
  const skipLeft = document.getElementById("skipLeft");
  const skipRight = document.getElementById("skipRight");
  const titleEl = document.querySelector(".video-title");

  function toPublicMediaUrl(value) {
    const raw = String(value || "").trim();
    if (!raw) return "";
    if (raw.toLowerCase() === "pending") return "";
    if (/^https?:\/\//i.test(raw)) return raw;
    if (raw.startsWith("/files/")) return raw;
    if (raw.startsWith("files/")) return "/" + raw;
    return "/files/" + raw.replace(/^\/+/, "");
  }

  function initPlayerData() {
    let data = null;
    const raw = sessionStorage.getItem("onfilm.player");
    if (raw) {
      try { data = JSON.parse(raw); } catch (_) { data = null; }
    }

    const params = new URLSearchParams(window.location.search);
    const qsTitle = params.get("title");
    const qsSrc = params.get("src");

    const title = (data?.title || qsTitle || "").toString().trim();
    const src = (data?.movieUrl || qsSrc || "").toString().trim();

    if (title) {
      titleEl.textContent = title;
      document.title = title;
    }

    if (src) {
      video.src = toPublicMediaUrl(src);
    }
  }

  initPlayerData();

  /* 뒤로가기 */
  backBtn.onclick = () => history.back();

  /* 시간 포맷 */
  const fmt = (t) =>
          String(Math.floor(t / 60)).padStart(2, "0") +
          ":" +
          String(Math.floor(t % 60)).padStart(2, "0");

  /* 영상 로드 & 자동 재생 플래그 */
  const allowSound = sessionStorage.getItem("allowSoundPlay");

  video.onloadedmetadata = () => {

    // 기존 timeDisplay 업데이트 코드 유지
    timeDisplay.textContent = `${fmt(video.currentTime)} / ${fmt(video.duration)}`;

    // 🔥 메인에서 재생 버튼 누른 경우 → 소리 ON + 자동 재생
    if (allowSound === "true") {
      video.muted = false;
      video.volume = 1.0;     // 기본 볼륨
      video.play();

      playPause.textContent = "⏸"; // UI도 재생 상태로 갱신

      // 🔥 볼륨 UI 상태 갱신 추가
      volumeVertical.value = video.volume;
      volumeFill.style.height = (video.volume * 100) + "%";

      // 음소거 아이콘 표시도 맞춰주기
      iconSoundOn.style.display = "block";
      iconSoundOff.style.display = "none";
    }
  };

  /* 영상 재생 중 타임라인 / 시간 업데이트 */
  video.ontimeupdate = () => {
    if (!isNaN(video.duration) && video.duration > 0) {
      timelineProgress.style.width =
              (video.currentTime / video.duration * 100) + "%";
    }
    timeDisplay.textContent =
            `${fmt(video.currentTime)} / ${fmt(video.duration)}`;
  };

  /* 재생 / 일시정지 */
  function togglePlay() {
    if (video.paused) {
      video.play();
      playPause.textContent = "⏸";
      showCenterIndicator(true);   // 🔥 재생 아이콘
    } else {
      video.pause();
      playPause.textContent = "▶";
      showCenterIndicator(false);  // 🔥 일시정지 아이콘
    }
  }
  playPause.onclick = togglePlay;
  video.onclick = togglePlay;

  /* 키보드 */
  document.onkeydown = (e) => {
    if (e.code === "Space") {
      e.preventDefault();
      togglePlay();
    }

    if (e.key === "ArrowLeft") {
      e.preventDefault();
      video.currentTime = Math.max(0, video.currentTime - 5);
      showSkipIndicator("left");
    }

    if (e.key === "ArrowRight") {
      e.preventDefault();
      if (!isNaN(video.duration)) {
        video.currentTime = Math.min(video.duration, video.currentTime + 5);
      }
      showSkipIndicator("right");
    }

    /* 🔥 F 키 → 전체 화면 토글 */
    if (e.key === "f" || e.key === "F" || e.key === "ㄹ") {
      if (!document.fullscreenElement) {
        playerContainer.requestFullscreen();
      } else {
        document.exitFullscreen();
      }
    }

    /* 🔥 M 키 → 음소거 토글 */
    if (e.key === "m" || e.key === "M" || e.key === "ㅡ") {
      if (!video.muted) {
        // 음소거 ON
        lastVolume = video.volume;
        video.muted = true;
        video.volume = 0;
        volumeVertical.value = 0;
        volumeFill.style.height = "0%";
        iconSoundOn.style.display = "none";
        iconSoundOff.style.display = "block";
      } else {
        // 음소거 OFF
        video.muted = false;
        video.volume = lastVolume;
        volumeVertical.value = lastVolume;
        volumeFill.style.height = lastVolume * 100 + "%";
        iconSoundOn.style.display = "block";
        iconSoundOff.style.display = "none";
      }
    }
  };

  /* 10초 이동 */
  back10.onclick = () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
    showSkipIndicator("left");
  }
  forward10.onclick = () => {
    if (!isNaN(video.duration)) {
      video.currentTime = Math.min(video.duration, video.currentTime + 10);
      showSkipIndicator("right");
    }
  };

  /* ──────────────── 볼륨 팝업 ──────────────── */
  const volumePopup = document.getElementById("volumePopup");
  const volumeVertical = document.getElementById("volumeVertical");

  /* 기본 볼륨값 */
  video.volume = 0.7;
  volumeVertical.value = video.volume;

  /* 팝업 위치: 스피커 아이콘 “정중앙 위” */
  function updateVolumePopupPosition() {
    const rect = volumeBtn.getBoundingClientRect();
    const popupWidth = 50;
    const popupHeight = 160;

    const x = rect.left + rect.width / 2 - (popupWidth / 2);
    const y = rect.top - popupHeight - 8; // 아이콘 위로 8px 띄우기

    volumePopup.style.left = x + "px";
    volumePopup.style.top = y + "px";
  }

  /* 팝업 열기 */
  function openVolumePopup() {
    updateVolumePopupPosition();
    volumePopup.classList.add("show");
  }

  /* 팝업 닫기 */
  function closeVolumePopup() {
    volumePopup.classList.remove("show");
  }

  /* hover 진입/이탈 상태 체크용 플래그 */
  let overVolumeBtn = false;
  let overVolumePopup = false;
  let volumeHideTimer = null;

  function scheduleVolumeHide() {
    if (volumeHideTimer) clearTimeout(volumeHideTimer);
    volumeHideTimer = setTimeout(() => {
      if (!overVolumeBtn && !overVolumePopup) {
        closeVolumePopup();
      }
    }, 120); // 짧게 딜레이 줘서 자연스러운 느낌
  }

  volumeBtn.addEventListener("mouseenter", () => {
    overVolumeBtn = true;
    openVolumePopup();
  });

  volumeBtn.addEventListener("mouseleave", () => {
    overVolumeBtn = false;
    scheduleVolumeHide();
  });

  volumePopup.addEventListener("mouseenter", () => {
    overVolumePopup = true;
  });

  volumePopup.addEventListener("mouseleave", () => {
    overVolumePopup = false;
    scheduleVolumeHide();
  });

  const iconSoundOn = document.getElementById("icon-sound-on");
  const iconSoundOff = document.getElementById("icon-sound-off");

  /* 세로 슬라이더로 볼륨 조절 */
  const volumeFill = document.getElementById("volumeFill");

  volumeVertical.addEventListener("input", () => {
    const v = Number(volumeVertical.value);
    video.volume = v;

    volumeFill.style.height = (v * 100) + "%";

    if (v === 0) {
      // 볼륨 0 → 자동 음소거
      video.muted = true;
      // volumeBtn.textContent = "🔇";
      iconSoundOn.style.display = "none";
      iconSoundOff.style.display = "block";
    } else {
      // 볼륨이 0보다 크면 자동 음소거 해제
      video.muted = false;
      // volumeBtn.textContent = "🔊";
      iconSoundOn.style.display = "block";
      iconSoundOff.style.display = "none";
    }
  });

  /* 볼륨 드래그 조절 */
  let draggingVolume = false;

  // 마우스를 눌렀을 때 드래그 시작
  volumeVertical.addEventListener("mousedown", (e) => {
    draggingVolume = true;
  });

  // 마우스를 떼면 드래그 종료
  document.addEventListener("mouseup", () => {
    draggingVolume = false;
  });

  // 드래그 중 마우스 움직일 때 계속 볼륨 반영
  document.addEventListener("mousemove", (e) => {
    if (!draggingVolume) return;

    const rect = volumeVertical.getBoundingClientRect();

    // 슬라이더가 회전돼 있어서 X축 기준으로 계산해야 함
    const offsetX = e.clientX - rect.left;
    let percent = offsetX / rect.width;  // 0 ~ 1

    // 범위 제한
    percent = Math.max(0, Math.min(1, percent));

    // 값 반영
    volumeVertical.value = percent;
    video.volume = percent;

    // 파란 줄 채우기
    volumeFill.style.height = (percent * 100) + "%";

    // 아이콘 상태 업데이트
    if (percent === 0) {
      video.muted = true;
      // volumeBtn.textContent = "🔇";
      iconSoundOn.style.display = "none";
      iconSoundOff.style.display = "block";
    } else {
      video.muted = false;
      // volumeBtn.textContent = "🔊";
      iconSoundOn.style.display = "block";
      iconSoundOff.style.display = "none";
    }
  });



  /* 음소거 버튼 (클릭) */
  let lastVolume = video.volume;

  volumeBtn.addEventListener("click", () => {
    if (!video.muted) {
      // 음소거 ON
      lastVolume = video.volume;     // 현재 볼륨 저장
      video.muted = true;
      video.volume = 0;
      volumeVertical.value = 0;      // 슬라이더도 바닥으로
      volumeFill.style.height = "0%";
      // volumeBtn.textContent = "🔇";
      iconSoundOn.style.display = "none";
      iconSoundOff.style.display = "block";
    }
    else {
      // 음소거 OFF
      video.muted = false;
      video.volume = lastVolume;     // 이전 볼륨 복원
      volumeVertical.value = lastVolume;
      volumeFill.style.height = lastVolume * 100 + "%";
      // volumeBtn.textContent = "🔊";
      iconSoundOn.style.display = "block";
      iconSoundOff.style.display = "none";
    }
  });

  /* 전체 화면 */
  const playerContainer = document.querySelector(".player-container");

  fullscreenBtn.onclick = () => {
    if (!document.fullscreenElement) {
      playerContainer.requestFullscreen();   // ← video 가 아니라 playerContainer 전체!
    } else {
      document.exitFullscreen();
    }
  };

  /* 전체 화면 변경 시 볼륨 팝업 위치 재계산 */
  document.addEventListener("fullscreenchange", () => {
    showUI();

    if (volumePopup.classList.contains("show")) {
      updateVolumePopupPosition();
    }
  });

  /* 윈도우 리사이즈 시 위치 재조정 */
  window.addEventListener("resize", () => {
    if (volumePopup.classList.contains("show")) {
      updateVolumePopupPosition();
    }
  });

  /* 타임라인 클릭 */
  timeline.onclick = (e) => {
    const r = timeline.getBoundingClientRect();
    const p = (e.clientX - r.left) / r.width;
    if (!isNaN(video.duration)) {
      video.currentTime = video.duration * Math.min(Math.max(p, 0), 1);
    }
  };

  /* 배속 메뉴 */
  speedBtn.onclick = () => {
    speedMenu.style.display =
            speedMenu.style.display === "block" ? "none" : "block";
  };

  document.querySelectorAll(".speed-option").forEach((o) => {
    o.onclick = () => {
      const rate = Number(o.dataset.speed);
      video.playbackRate = rate;
      speedBtn.textContent = rate + "x";
      speedMenu.style.display = "none";
    };
  });

  /* UI 자동 숨김 */
  let uiTimer = null;
  function showUI() {
    controls.classList.remove("hide-ui");
    timeDisplay.classList.remove("hide-ui");
    timeline.classList.remove("hide-ui");
    topLeftInfo.classList.remove("hide-ui");

    clearTimeout(uiTimer);

    uiTimer = setTimeout(() => {
      controls.classList.add("hide-ui");
      timeDisplay.classList.add("hide-ui");
      timeline.classList.add("hide-ui");
      topLeftInfo.classList.add("hide-ui");
    }, 2500);
  }

  document.onmousemove = showUI;
  document.ontouchstart = showUI;
  showUI();

  /* 모바일 더블탭 → 10초 이동 */
  let lastTap = 0;
  document.ontouchend = (e) => {
    const now = Date.now();
    if (now - lastTap < 300) {
      const x = e.changedTouches[0].clientX;
      if (x < window.innerWidth / 2) video.currentTime -= 10;
      else video.currentTime += 10;
    }
    lastTap = now;
  };

  // 가운데 재생/일시정지 인디케이터
  let indicatorTimer = null;

  function showCenterIndicator(isPlaying) {
    if (!centerIndicator) return;

    // 기존 타이머 있으면 초기화
    if (indicatorTimer) clearTimeout(indicatorTimer);

    // 아이콘 모양 변경
    centerIndicator.textContent = isPlaying ? "▶" : "⏸";

    // 보여주기
    centerIndicator.classList.add("show");

    // 0.4초 뒤에 다시 숨기기
    indicatorTimer = setTimeout(() => {
      centerIndicator.classList.remove("show");
    }, 400);
  }

  // 🔥 좌우 5초 이동 인디케이터
  let skipLeftTimer = null;
  let skipRightTimer = null;

  function showSkipIndicator(direction) {
    if (direction === "left") {
      if (!skipLeft) return;
      if (skipLeftTimer) clearTimeout(skipLeftTimer);

      skipLeft.classList.add("show");
      skipLeftTimer = setTimeout(() => {
        skipLeft.classList.remove("show");
      }, 400);
    } else if (direction === "right") {
      if (!skipRight) return;
      if (skipRightTimer) clearTimeout(skipRightTimer);

      skipRight.classList.add("show");
      skipRightTimer = setTimeout(() => {
        skipRight.classList.remove("show");
      }, 400);
    }
  }


