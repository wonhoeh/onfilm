
  // ===== 유틸: 로그인 필요한 페이지로 이동 (next 포함) =====
  function goRequireLogin(path) {
    const next = encodeURIComponent(path);
    window.location.href = `login.html?next=${next}`;
  }

  // ===== 헤더 렌더 =====
  function renderLoggedOut(container) {
    container.innerHTML = `
      <div class="header-nav-links">
        <a href="#page2" class="header-link">기능 소개</a>
        <a href="#page3" class="header-link">사용 대상</a>
      </div>
      <button type="button" class="login-btn" id="loginBtn">로그인</button>
    `;

    container.querySelector("#loginBtn")?.addEventListener("click", () => {
      // 로그인 후 다시 index로 돌아오게 next를 index로 잡아도 됨
      goRequireLogin("index.html");
    });
  }

  function renderLoggedIn(container, me) {
    const { username, displayName, avatarUrl } = window.OnfilmCommon.normalizeMe(me);
    const initial = window.OnfilmCommon.getInitial(username, displayName);

    container.innerHTML = `
      <div class="header-nav-links">
        <a href="#page2" class="header-link">기능 소개</a>
        <a href="#page3" class="header-link">사용 대상</a>
      </div>
      ${window.OnfilmCommon.buildProfileMenuHtml(displayName, avatarUrl, initial)}
    `;

    window.OnfilmCommon.attachProfileMenuHandlers({
      me,
      onRequireLogin: () => openLoginOverlay?.()
    });
  }

  async function renderHeaderActions() {
    const container = document.getElementById("headerActions");
    if (!container) return;

    // 기본은 비로그인 UI
    renderLoggedOut(container);

    // ✅ 자동 복구: me -> refresh -> me
    const { ok, me } = await window.OnfilmAuth.restoreSession();

    if (!ok || !me) {
      renderLoggedOut(container);
      return;
    }

    renderLoggedIn(container, me);
  }

  // ===== 도트 / 스크롤 =====
  function setupPageDots(){
    const root   = document.getElementById("fullpageRoot");
    const panels = document.querySelectorAll(".panel");
    const dots   = document.querySelectorAll(".page-dot");

    dots.forEach(dot => {
      dot.addEventListener("click", () => {
        const section = document.querySelector(dot.dataset.target);
        if (section) section.scrollIntoView({ behavior: "smooth" });
      });
    });

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        const id = "#" + entry.target.id;
        dots.forEach(dot => dot.classList.toggle("active", dot.dataset.target === id));
      });
    }, { root, threshold: 0.6 });

    panels.forEach(p => observer.observe(p));
  }

  // ===== 가입 인사 오버레이 =====
  function setupWelcomeOverlay(){
    const welcomeOverlay = document.getElementById("welcomeOverlay");
    const goSetupBtn     = document.getElementById("goSetupBtn");
    const welcomeLater   = document.getElementById("welcomeLaterBtn");
    if (!welcomeOverlay) return;

    const params = new URLSearchParams(window.location.search);
    const fromSignup = params.get("welcome") === "1";

    if (fromSignup) {
      welcomeOverlay.classList.add("show");

      const url = new URL(window.location.href);
      url.searchParams.delete("welcome");
      window.history.replaceState({}, "", url);
    }

    welcomeOverlay.addEventListener("click", (e) => {
      if (e.target === welcomeOverlay) welcomeOverlay.classList.remove("show");
    });

    goSetupBtn?.addEventListener("click", () => {
      // 로그인 상태면 바로 편집, 아니면 로그인으로
      const me = window.OnfilmAuth.getMe();
      if (!me) return goRequireLogin("edit-profile.html");
      window.location.href = "edit-profile.html";
    });

    welcomeLater?.addEventListener("click", () => {
      welcomeOverlay.classList.remove("show");
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && welcomeOverlay.classList.contains("show")) {
        welcomeOverlay.classList.remove("show");
      }
    });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    await renderHeaderActions();
    setupPageDots();
    setupWelcomeOverlay();
  });
