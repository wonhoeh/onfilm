
    function renderLoggedOut(container) {
        container.innerHTML = `<button type="button" class="login-btn" id="loginBtn">로그인</button>`;
        container.querySelector("#loginBtn")?.addEventListener("click", () => {
            const next = encodeURIComponent(location.pathname + location.search);
            window.location.href = `/login.html?next=${next}`;
        });
    }

    function renderLoggedIn(container, me) {
        const { username, displayName, avatarUrl } = window.OnfilmCommon.normalizeMe(me);
        const initial = window.OnfilmCommon.getInitial(username, displayName);
        container.innerHTML = window.OnfilmCommon.buildProfileMenuHtml(displayName, avatarUrl, initial);
        window.OnfilmCommon.attachProfileMenuHandlers({ me });
    }
    const projectList = document.getElementById("projectList");
    const createProjectBtn = document.getElementById("createProjectBtn");
    const backToProfileLink = document.getElementById("backToProfileLink");

    const authFetch = window.OnfilmCommon.authFetch;
    const fetchPublicIdByUsername = window.OnfilmCommon.fetchPublicIdByUsername;

    function renderEmpty(){
        projectList.classList.add("empty-mode");
        projectList.innerHTML = `
            <div class="empty-cta">
                <a class="empty-cta-icon" href="/edit-storyboard.html" aria-label="스토리보드 등록하기">
                    <span class="empty-cta-ring">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M6 4.5h8.2a2.3 2.3 0 0 1 2.3 2.3v12.7H8.5A2.5 2.5 0 0 0 6 22V4.5Z"/>
                        <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 7.2H18a2 2 0 0 1 2 2v10.6H8.5A2.5 2.5 0 0 0 6 22h11.5"/>
                        <path stroke-linecap="round" stroke-linejoin="round" d="M8.5 8.6h6.2M8.5 11.6h6.2M8.5 14.6h6.2"/>
                      </svg>
                    </span>
                </a>

                <div class="empty-cta-title">스토리보드 업로드</div>
                <div class="empty-cta-desc">스토리보드를 등록하면 보관함에 표시됩니다</div>
                <a class="empty-cta-link" href="/edit-storyboard.html">첫 스토리보드 등록하기</a>
            </div>
        `;
    }

    function renderProjects(projects){
        if (!Array.isArray(projects) || projects.length === 0) {
            renderEmpty();
            return;
        }
        projectList.classList.remove("empty-mode");
        projectList.innerHTML = projects.map(project => {
            const updatedIso = localStorage.getItem(`onfilm.storyboard.updated.${project.projectId}`) || "";
            const updatedText = formatUpdatedAt(updatedIso);
            return `
                <div class="project-item">
                    <article class="project-card" data-project-id="${project.projectId}">
                        <h3>${escapeHtml(project.title || "제목 없음")}</h3>
                    </article>
                    <div class="project-updated">${escapeHtml(updatedText)}</div>
                </div>
            `;
        }).join("");
    }

    function escapeHtml(value){
        return String(value || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function formatUpdatedAt(iso){
        if (!iso) return "";
        const dt = new Date(iso);
        if (Number.isNaN(dt.getTime())) return "";
        const y = dt.getFullYear();
        const m = dt.getMonth() + 1;
        const d = dt.getDate();
        const hh = String(dt.getHours()).padStart(2, "0");
        const mm = String(dt.getMinutes()).padStart(2, "0");
        return `수정한 날짜: ${y}·${m}·${d}·${hh}:${mm}`;
    }


    async function init(){
        if (!window.OnfilmAuth?.restoreSession) return;
        const result = await window.OnfilmAuth.restoreSession();
        if (!result?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }
        const headerActions = document.getElementById("headerActions");
        if (headerActions) {
            renderLoggedIn(headerActions, result.me);
        }
        const me = result.me;
        const uname = me?.username ? String(me.username).trim() : "";
        if (!uname) {
            renderEmpty();
            return;
        }
        if (backToProfileLink) {
            backToProfileLink.href = "/onfilm/" + encodeURIComponent(uname);
        }

        const publicId = await fetchPublicIdByUsername(uname);
        const res = await authFetch(`/api/people/${encodeURIComponent(publicId)}/storyboard/projects`, {
            headers: { "Accept": "application/json" }
        });
        if (!res.ok) throw new Error("LOAD_FAILED");
        const projects = await res.json().catch(() => []);
        renderProjects(projects);

        projectList.addEventListener("click", async (e) => {
            const card = e.target.closest(".project-card");
            if (!card) return;
            const projectId = card.dataset.projectId;
            if (!projectId) return;
            location.href = `/storyboard-view.html?projectId=${encodeURIComponent(projectId)}`;
        });

        createProjectBtn?.addEventListener("click", () => {
            location.href = "/edit-storyboard.html";
        });
    }

    document.addEventListener("DOMContentLoaded", init);
