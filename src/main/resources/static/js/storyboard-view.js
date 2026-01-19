
    const projectTitle = document.getElementById("projectTitle");
    const editBtn = document.getElementById("editBtn");
    const imageColumn = document.getElementById("imageColumn");
    const scriptColumn = document.getElementById("scriptColumn");
    const sceneTabs = document.getElementById("sceneTabs");

    let projectScenes = [];
    let activeSceneId = null;

    const authFetch = window.OnfilmCommon.authFetch;
    const fetchPublicIdByUsername = window.OnfilmCommon.fetchPublicIdByUsername;
    const escapeHtml = window.OnfilmCommon.escapeHtml;

    function renderSceneTabs(scenes){
        if (!sceneTabs) return;
        if (!Array.isArray(scenes) || scenes.length === 0) {
            sceneTabs.innerHTML = "";
            return;
        }
        sceneTabs.innerHTML = scenes.map((scene, idx) => {
            const id = scene.sceneId;
            const label = `#${idx + 1}`;
            const active = id === activeSceneId ? "active" : "";
            return `<button type="button" class="scene-tab ${active}" data-scene-id="${id}">${label}</button>`;
        }).join("");
    }

    function renderSceneBlocks(scenes){
        imageColumn.innerHTML = "";
        scriptColumn.innerHTML = "";
        if (!Array.isArray(scenes) || scenes.length === 0) {
            imageColumn.innerHTML = `<div class="empty">등록된 스토리보드가 없습니다.</div>`;
            scriptColumn.innerHTML = `<div class="empty">등록된 대본이 없습니다.</div>`;
            return;
        }
        const sorted = [...scenes].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
        const filtered = activeSceneId
            ? sorted.filter(scene => String(scene.sceneId) === String(activeSceneId))
            : sorted;
        filtered.forEach((scene, idx) => {
            const rawTitle = (scene.title || "").trim();
            const title = rawTitle || `#${idx + 1} 새 씬`;

            const imageBlock = document.createElement("section");
            imageBlock.className = "scene-block";
            imageBlock.innerHTML = `<div class="scene-title">${escapeHtml(title)}</div>`;
            const cardStack = document.createElement("div");
            cardStack.className = "card-stack";
            const cards = Array.isArray(scene.cards) ? scene.cards : [];
            if (cards.length === 0) {
                const empty = document.createElement("div");
                empty.className = "empty";
                empty.textContent = "등록된 이미지가 없습니다.";
                cardStack.appendChild(empty);
            } else {
                cards.forEach(card => {
                    const img = document.createElement("img");
                    img.src = card.imageUrl || "";
                    img.alt = "스토리보드 컷";
                    cardStack.appendChild(img);
                });
            }
            imageBlock.appendChild(cardStack);
            imageColumn.appendChild(imageBlock);

            const scriptBlock = document.createElement("section");
            scriptBlock.className = "scene-block";
            scriptBlock.innerHTML = `<div class="scene-title">${escapeHtml(title)}</div>`;
            const scriptPage = document.createElement("div");
            scriptPage.className = "script-page";
            scriptPage.innerHTML = scene.scriptHtml || "<span class='empty'>대본이 없습니다.</span>";
            scriptBlock.appendChild(scriptPage);
            scriptColumn.appendChild(scriptBlock);
        });
    }

    async function init(){
        if (!window.OnfilmAuth?.restoreSession) return;
        const result = await window.OnfilmAuth.restoreSession();
        if (!result?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }
        const params = new URLSearchParams(location.search);
        const projectId = params.get("projectId");
        if (!projectId) {
            location.href = "/storyboard.html";
            return;
        }
        editBtn.addEventListener("click", () => {
            location.href = `/edit-storyboard.html?projectId=${encodeURIComponent(projectId)}`;
        });

        const me = result.me;
        const uname = me?.username ? String(me.username).trim() : "";
        if (!uname) return;
        const publicId = await fetchPublicIdByUsername(uname);
        const res = await authFetch(`/api/people/${encodeURIComponent(publicId)}/storyboard/projects/${encodeURIComponent(projectId)}`, {
            headers: { "Accept": "application/json" }
        });
        if (!res.ok) throw new Error("LOAD_FAILED");
        const project = await res.json().catch(() => null);
        if (projectTitle) projectTitle.textContent = project?.title || "스토리보드";
        projectScenes = project?.scenes || [];
        if (Array.isArray(projectScenes) && projectScenes.length > 0) {
            const sorted = [...projectScenes].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
            activeSceneId = sorted[0]?.sceneId || null;
        }
        renderSceneTabs(projectScenes);
        renderSceneBlocks(projectScenes);

        sceneTabs?.addEventListener("click", (e) => {
            const btn = e.target.closest(".scene-tab");
            if (!btn) return;
            activeSceneId = btn.dataset.sceneId || null;
            renderSceneTabs(projectScenes);
            renderSceneBlocks(projectScenes);
        });
    }

    document.addEventListener("DOMContentLoaded", init);
