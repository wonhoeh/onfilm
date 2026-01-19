(function () {
  function escapeHtml(str) {
    return String(str ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function normalizeMe(me) {
    const username = me?.username ? String(me.username).trim() : "";
    const displayName = (me?.displayName || me?.name || username || me?.email || "내 계정");
    const avatarUrl = me?.avatarUrl || me?.profileImageUrl || me?.profileImage || "";
    const myActorId = me?.actorId || me?.actor?.id || me?.actorProfileId || null;
    return { username, displayName, avatarUrl, myActorId };
  }

  function getInitial(username, displayName) {
    const base = (username || displayName || "A").trim();
    return (base.charAt(0) || "A").toUpperCase();
  }

  function authFetch(url, options = {}) {
    if (window.OnfilmAuth?.apiFetchWithAutoRefresh) {
      return window.OnfilmAuth.apiFetchWithAutoRefresh(url, options);
    }
    return fetch(url, options);
  }

  async function fetchPublicIdByUsername(username) {
    const res = await authFetch(`/api/person/${encodeURIComponent(username)}`, {
      headers: { "Accept": "application/json" }
    });
    if (!res.ok) throw new Error("PUBLIC_ID_NOT_FOUND");
    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) {
      const data = await res.json().catch(() => null);
      if (typeof data === "string") return data.trim();
      return data?.publicId || data?.id || data?.personPublicId || data?.personId || "";
    }
    return (await res.text().catch(() => "")).trim();
  }

  async function fetchPersonByPublicId(publicId) {
    const res = await authFetch(`/api/people/${encodeURIComponent(publicId)}`, {
      headers: { "Accept": "application/json" }
    });
    if (!res.ok) throw new Error("PERSON_NOT_FOUND");
    return res.json().catch(() => null);
  }

  function personHasAnyContent(p) {
    return !!(p?.oneLineIntro && String(p.oneLineIntro).trim())
      || !!(p?.birthDate && String(p.birthDate).trim())
      || !!(p?.birthPlace && String(p.birthPlace).trim())
      || !!(p?.profileImageUrl && String(p.profileImageUrl).trim())
      || (Array.isArray(p?.snsList) && p.snsList.length > 0)
      || (Array.isArray(p?.rawTags) && p.rawTags.length > 0);
  }

  async function resolveActorPageUrl(me) {
    const uname = (me?.username ? String(me.username).trim() : "");
    if (!uname) return "/edit-profile.html";
    try {
      const publicId = await fetchPublicIdByUsername(uname);
      if (!publicId) return "/edit-profile.html";
      const p = await fetchPersonByPublicId(publicId);
      if (!p || !personHasAnyContent(p)) return "/edit-profile.html";
      return "/onfilm/" + encodeURIComponent(uname);
    } catch (e) {
      return "/edit-profile.html";
    }
  }

  function buildProfileMenuHtml(displayName, avatarUrl, initial) {
    const safeName = escapeHtml(displayName);
    const safeInitial = escapeHtml(initial);
    return `
      <div class="profile-menu">
        <button type="button" class="profile-avatar-btn" id="profileAvatarBtn" aria-label="계정 메뉴 열기">
          ${avatarUrl ? `<img src="${avatarUrl}" alt="${safeName} 프로필" />`
            : `<span class="profile-avatar-initial">${safeInitial}</span>`}
        </button>

        <div class="profile-dropdown" id="profileDropdown">
          <div class="profile-group-title">${safeName}</div>

          <button type="button" class="profile-item" data-action="actor-page">
            프로필
            <span>프로필 보기</span>
          </button>

          <div class="profile-divider"></div>
          <div class="profile-group-title">편집</div>

          <button type="button" class="profile-item" data-action="edit-profile">
            프로필 편집
            <span>기본정보</span>
          </button>

          <button type="button" class="profile-item" data-action="edit-filmography">
            필모그래피 편집
            <span>작품/역할</span>
          </button>

          <button type="button" class="profile-item" data-action="edit-gallery">
            갤러리 편집
            <span>사진/영상</span>
          </button>

          <div class="profile-divider"></div>
          <div class="profile-group-title">스토리보드</div>

          <button type="button" class="profile-item" data-action="storyboard-projects">
            프로젝트 목록
            <span>스토리보드 보관함</span>
          </button>

          <button type="button" class="profile-item" data-action="storyboard-edit">
            스토리보드 편집
            <span>대본/컷 작업</span>
          </button>

          <div class="profile-divider"></div>

          <button type="button" class="profile-item" data-action="account-settings">
            계정 설정
            <span>비밀번호/연동</span>
          </button>

          <button type="button" class="profile-item" data-action="logout">
            로그아웃
          </button>
        </div>
      </div>
    `;
  }

  function attachProfileMenuHandlers({ me, onLogout, onRequireLogin } = {}) {
    const avatarBtn = document.getElementById("profileAvatarBtn");
    const dropdown = document.getElementById("profileDropdown");
    if (!avatarBtn || !dropdown) return;

    const close = () => dropdown.classList.remove("open");

    avatarBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      dropdown.classList.toggle("open");
    });

    dropdown.querySelectorAll(".profile-item").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const action = btn.dataset.action;
        close();

        if (action === "actor-page") {
          if (!me?.id && typeof onRequireLogin === "function") {
            onRequireLogin();
            return;
          }
          const target = await resolveActorPageUrl(me);
          window.location.href = target;
          return;
        }

        if (action === "edit-profile") { window.location.href = "/edit-profile.html"; return; }
        if (action === "edit-filmography") { window.location.href = "/edit-filmography.html"; return; }
        if (action === "edit-gallery") { window.location.href = "/edit-gallery.html"; return; }
        if (action === "storyboard-projects") { window.location.href = "/storyboard.html"; return; }
        if (action === "storyboard-edit") { window.location.href = "/edit-storyboard.html"; return; }
        if (action === "account-settings") { window.location.href = "/account-settings.html"; return; }

        if (action === "logout") {
          if (typeof onLogout === "function") {
            await onLogout();
            return;
          }
          await window.OnfilmAuth?.logout?.();
          window.location.href = "/index.html";
        }
      });
    });

    document.addEventListener("click", (e) => {
      if (!dropdown.contains(e.target) && e.target !== avatarBtn) close();
    });
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") close();
    });
  }

  window.OnfilmCommon = {
    authFetch,
    fetchPublicIdByUsername,
    fetchPersonByPublicId,
    escapeHtml,
    normalizeMe,
    getInitial,
    buildProfileMenuHtml,
    attachProfileMenuHandlers,
    resolveActorPageUrl
  };
})();
