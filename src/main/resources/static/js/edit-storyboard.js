
    // ─────────────────────────────────────────────
    // Storyboard + Script Manager (single-file)
    // ─────────────────────────────────────────────
    const board = document.getElementById("board");
    const tpl = document.getElementById("pairTpl");
    const addTpl = document.getElementById("addCardTpl");
    const scriptEditor = document.getElementById("scriptEditor");
    const sceneTabs = document.getElementById("sceneTabs");
    const sceneNumberDisplay = document.getElementById("sceneNumberDisplay");
    const sceneTitleInput = document.getElementById("sceneTitleInput");
    const deleteSceneBtn = document.getElementById("deleteSceneBtn");
    const movieTitleInput = document.getElementById("movieTitleInput");
    const pdfSaveBtn = document.getElementById("pdfSaveBtn");
    const cancelStoryboardBtn = document.getElementById("cancelStoryboardBtn");
    const saveStoryboardBtn = document.getElementById("saveStoryboardBtn");
    const backToProfileLink = document.getElementById("backToProfileLink");
    const scriptSceneLabel = document.getElementById("scriptSceneLabel");

    let notoFontBase64 = null;

    const state = {
        scenes: [], // {clientId, sceneId, title, script, items, seq}
        sceneSeq: 0,
        activeSceneId: null,
        movieTitle: "",
        publicId: null,
        projectId: null,
        dirty: false,
        initialized: false
    };

    const authFetch = window.OnfilmCommon.authFetch;

    function updateSaveState(){
        if (!saveStoryboardBtn || !movieTitleInput) return;
        const title = movieTitleInput.value.trim();
        const ready = title.length > 0 && title !== "새 스토리보드";
        saveStoryboardBtn.disabled = !ready;
    }


    function setDirty(value = true){
        state.dirty = value;
    }

    function stripHtml(value){
        return String(value || "").replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
    }

    function hasAnyInput(){
        const title = movieTitleInput ? movieTitleInput.value.trim() : state.movieTitle.trim();
        if (title.length > 0 && title !== "새 스토리보드") return true;
        const hasScript = state.scenes.some(scene => stripHtml(scene.script).length > 0);
        if (hasScript) return true;
        const hasImage = state.scenes.some(scene =>
            Array.isArray(scene.items) && scene.items.some(it => it.imageKey || it.imageUrl || it.image)
        );
        return hasImage;
    }

    function handleLeaveAttempt(){
        if (!hasAnyInput()) {
            window.location.href = "/storyboard.html";
            return;
        }
        const ok = window.confirm("이 페이지를 벗어나면 수정된 내용은 저장되지 않습니다.");
        if (ok) window.location.href = "/storyboard.html";
    }


    function nextSceneId(){
        state.sceneSeq += 1;
        return "scene_" + state.sceneSeq + "_" + Date.now().toString(36);
    }

    function nextItemId(scene){
        scene.seq += 1;
        return "sb_" + scene.seq + "_" + Date.now().toString(36);
    }

    function getActiveScene(){
        return state.scenes.find(s => s.clientId === state.activeSceneId) || null;
    }

    function getNextOrder(){
        const scene = getActiveScene();
        if (!scene || scene.items.length === 0) return 1;
        const max = Math.max(...scene.items.map(it => Number(it.order) || 0));
        return max + 1;
    }

    function formatBytes(bytes){
        if (!bytes && bytes !== 0) return "";
        const units = ["B","KB","MB","GB"];
        let n = bytes;
        let i = 0;
        while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
        return `${n.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
    }

    function ensureAtLeastOne(){
        const scene = getActiveScene();
        if (scene && scene.items.length === 0) addItem();
    }

    function addItem(){
        const scene = getActiveScene();
        if (!scene) return;
        const item = {
            clientId: nextItemId(scene),
            cardId: null,
            order: getNextOrder(),
            imageKey: null,
            imageUrl: null,
            image: null
        };
        scene.items.push(item);
        setDirty();
        renderBoard();
    }

    function removeItem(id){
        const scene = getActiveScene();
        if (!scene) return;
        scene.items = scene.items.filter(it => it.clientId !== id);
        setDirty();
        renderBoard();
        ensureAtLeastOne();
    }

    function setOrder(id, value){
        const scene = getActiveScene();
        if (!scene) return;
        const it = scene.items.find(x => x.clientId === id);
        if (!it) return;
        it.order = value;
        // 표시 갱신만
        syncTitles();
    }

    async function setImage(id, file){
        try{
            const scene = getActiveScene();
            if (!scene) return;
            const it = scene.items.find(x => x.clientId === id);
            if (!it) return;
            if (it.imageUrl && it.imageUrl.startsWith("blob:")) {
                URL.revokeObjectURL(it.imageUrl);
            }
            let previewUrl = null;
            try{
                previewUrl = await readFileAsDataURL(file);
            }catch{
                previewUrl = URL.createObjectURL(file);
            }
            it.imageUrl = previewUrl;
            it.image = {
                name: file.name,
                size: file.size,
                type: file.type
            };
            setDirty();
            renderBoard();

            const upload = await uploadStoryboardImage(file);
            if (it.imageUrl && it.imageUrl.startsWith("blob:")) {
                URL.revokeObjectURL(it.imageUrl);
            }
            it.imageKey = upload?.key || null;
            it.imageUrl = upload?.url || previewUrl;
            renderBoard();
        }catch(e){
            console.error(e);
            alert(`이미지를 처리하는 중 문제가 발생했어요. ${e?.message || ""}`);
        }
    }

    function removeImage(id){
        const scene = getActiveScene();
        if (!scene) return;
        const it = scene.items.find(x => x.clientId === id);
        if (!it) return;
        if (it.imageUrl && it.imageUrl.startsWith("blob:")) {
            URL.revokeObjectURL(it.imageUrl);
        }
        it.image = null;
        it.imageKey = null;
        it.imageUrl = null;
        setDirty();
        renderBoard();
    }

    function readFileAsDataURL(file){
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    }

    function validateImageFile(file){
        if (!file) return "파일이 없습니다.";
        const ok = ["image/png","image/jpeg","image/jpg","image/webp"];
        // 일부 브라우저는 jpg를 image/jpeg로 줌
        if (!ok.includes(file.type)) return "PNG/JPEG/WebP 이미지 파일만 업로드할 수 있어요.";
        return null;
    }

    function syncTitles(){
        const scene = getActiveScene();
        if (!scene) return;
        // DOM 상에서 order 변경 반영
        board.querySelectorAll(".pair").forEach((el, idx) => {
            const id = el.dataset.id;
            const it = scene.items.find(x => x.clientId === id);
            if (!it) return;

            const badge = el.querySelector("[data-badge]");
            const title = el.querySelector("[data-title]");
            // badge는 "카드 번호(고정 인덱스)" 느낌, order는 "씬 순서"
            it.order = idx + 1;
            badge.textContent = String(idx + 1);
            title.textContent = "";
        });
    }

    function wireRowEvents(rowEl){
        const id = rowEl.dataset.id;

        const dropzone = rowEl.querySelector("[data-dropzone]");
        const fileInput = rowEl.querySelector("[data-file]");
        const pickBtn = rowEl.querySelector("[data-pick-file]");
        const removeImgBtn = rowEl.querySelector("[data-remove-image]");
        const removeCardBtn = rowEl.querySelector("[data-remove-card]");
        // order input removed


        // file picker
        pickBtn.addEventListener("click", () => fileInput.click());
        fileInput.addEventListener("change", async () => {
            const file = fileInput.files && fileInput.files[0];
            fileInput.value = ""; // same file reselect 가능
            const err = validateImageFile(file);
            if (err) return alert(err);

            await setImage(id, file);
        });

        // click on dropzone = open picker
        dropzone.addEventListener("click", () => fileInput.click());
        dropzone.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                fileInput.click();
            }
        });

        // drag & drop
        const prevent = (e) => { e.preventDefault(); e.stopPropagation(); };

        ["dragenter","dragover"].forEach(evt => {
            dropzone.addEventListener(evt, (e) => {
                prevent(e);
                dropzone.classList.add("dragover");
            });
        });
        ["dragleave","drop"].forEach(evt => {
            dropzone.addEventListener(evt, (e) => {
                prevent(e);
                dropzone.classList.remove("dragover");
            });
        });

        dropzone.addEventListener("drop", async (e) => {
            prevent(e);
            const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
            const err = validateImageFile(file);
            if (err) return alert(err);

            await setImage(id, file);
        });

        // remove image
        removeImgBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            removeImage(id);
        });

        // remove card
        removeCardBtn?.addEventListener("click", (e) => {
            e.stopPropagation();
            removeItem(id);
        });

    }

    function renderBoard(){
        board.innerHTML = "";
        const scene = getActiveScene();
        if (!scene) return;
        if (scene.items.length === 0){
            scene.items.push({
                clientId: nextItemId(scene),
                cardId: null,
                order: 1,
                imageKey: null,
                imageUrl: null,
                image: null
            });
        }

        scene.items.forEach((it, idx) => {
            if (!it.clientId) it.clientId = nextItemId(scene);
            const node = tpl.content.firstElementChild.cloneNode(true);
            node.dataset.id = it.clientId;
            node.draggable = true;

            // set header labels
            const badge = node.querySelector("[data-badge]");
            const title = node.querySelector("[data-title]");
            badge.textContent = String(idx + 1);
            title.textContent = "";

            // set image preview
            const dz = node.querySelector("[data-dropzone]");
            const preview = node.querySelector("[data-preview]");
            const fileinfo = node.querySelector("[data-fileinfo]");

            if (it.imageUrl){
                dz.classList.add("has-image");
                preview.src = it.imageUrl;
                if (it.image && it.image.name) {
                    fileinfo.textContent = `${it.image.name} · ${formatBytes(it.image.size)}`;
                } else {
                    fileinfo.textContent = "업로드된 이미지";
                }
            }else{
                dz.classList.remove("has-image");
                preview.removeAttribute("src");
                fileinfo.textContent = "업로드된 이미지 없음";
            }

            board.appendChild(node);
            wireRowEvents(node);
        });

        const addNode = addTpl.content.firstElementChild.cloneNode(true);
        const addBtn = addNode.querySelector("#addCardBtn");
        addBtn.addEventListener("click", addItem);
        board.appendChild(addNode);

        syncTitles();
    }

    scriptEditor?.addEventListener("input", () => {
        const scene = getActiveScene();
        if (!scene) return;
        scene.script = scriptEditor.innerHTML;
        setDirty();
    });

    scriptEditor?.addEventListener("blur", () => {
        const scene = getActiveScene();
        if (!scene) return;
        scene.script = scriptEditor.innerHTML;
        setDirty();
    });

    scriptEditor?.addEventListener("keydown", (e) => {
        if (e.key !== "Tab") return;
        e.preventDefault();
        document.execCommand("insertText", false, "\t");
        setDirty();
    });


    // drag & drop reorder
    let draggedPair = null;

    function reorderFromDOM() {
        const scene = getActiveScene();
        if (!scene) return;
        const pairs = Array.from(board.querySelectorAll(".pair")).filter(el => !el.classList.contains("add-card"));
        const ordered = pairs.map(el => scene.items.find(it => it.clientId === el.dataset.id)).filter(Boolean);
        if (ordered.length === scene.items.length) scene.items = ordered;
        syncTitles();
        setDirty();
    }

    function onDragOver(e) {
        e.preventDefault();
        if (!draggedPair) return;
        const target = e.target.closest(".pair");
        if (!target || target === draggedPair || target.classList.contains("add-card")) return;
        const rect = target.getBoundingClientRect();
        const after = e.clientY > rect.top + rect.height / 2;
        if (after) target.after(draggedPair);
        else target.before(draggedPair);
    }

    board.addEventListener("dragover", onDragOver);
    board.addEventListener("drop", (e) => {
        e.preventDefault();
        if (!draggedPair) return;
        draggedPair.classList.remove("dragging");
        draggedPair = null;
        reorderFromDOM();
    });

    board.addEventListener("dragend", () => {
        if (!draggedPair) return;
        draggedPair.classList.remove("dragging");
        draggedPair = null;
        reorderFromDOM();
    });

    board.addEventListener("dragstart", (e) => {
        const pair = e.target.closest(".pair");
        if (!pair || pair.classList.contains("add-card")) return;
        draggedPair = pair;
        pair.classList.add("dragging");
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", pair.dataset.id);
    });

    function renderScenes(){
        if (!sceneTabs) return;
        sceneTabs.innerHTML = "";
        state.scenes.forEach((scene, idx) => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "scene-tab" + (scene.clientId === state.activeSceneId ? " active" : "");
            btn.textContent = `#${idx + 1}`;
            btn.dataset.sceneId = scene.clientId;
            btn.draggable = true;
            btn.addEventListener("click", () => {
                if (scene.clientId === state.activeSceneId) return;
                state.activeSceneId = scene.clientId;
                renderAll();
            });
            sceneTabs.appendChild(btn);
        });

        const addBtn = document.createElement("button");
        addBtn.type = "button";
        addBtn.className = "scene-tab scene-add";
        addBtn.textContent = "+ 씬";
        addBtn.addEventListener("click", addScene);
        sceneTabs.appendChild(addBtn);
    }

    function normalizeSceneTitle(value){
        const raw = String(value || "");
        const stripped = raw.replace(/^#\d+\s*/, "");
        return stripped;
    }

    function renderSceneTitle(){
        const scene = getActiveScene();
        const idx = scene ? state.scenes.indexOf(scene) : -1;
        const numberText = idx >= 0 ? `#${idx + 1}` : "#";
        const rawTitle = scene ? normalizeSceneTitle(scene.title) : "";
        const titleForLabel = rawTitle.trim() || "";
        if (sceneNumberDisplay) sceneNumberDisplay.textContent = numberText;
        if (sceneTitleInput) sceneTitleInput.value = rawTitle;
        if (scriptSceneLabel) {
            scriptSceneLabel.textContent = `${numberText} ${titleForLabel}`.trim();
        }
    }

    function renderAll(){
        renderScenes();
        renderSceneTitle();
        renderBoard();
        const scene = getActiveScene();
        if (scene && scriptEditor) scriptEditor.innerHTML = scene.script || "";
        if (movieTitleInput) movieTitleInput.value = state.movieTitle;
    }

    function addScene(){
        const sceneNumber = state.scenes.length + 1;
        const scene = {
            clientId: nextSceneId(),
            sceneId: null,
            title: "",
            script: "",
            items: [],
            seq: 0
        };
        state.scenes.push(scene);
        state.activeSceneId = scene.clientId;
        setDirty();
        renderAll();
    }

    sceneTitleInput?.addEventListener("input", (e) => {
        const scene = getActiveScene();
        if (!scene) return;
        const text = e.target.value || "";
        scene.title = text;
        const idx = state.scenes.indexOf(scene);
        const numberText = idx >= 0 ? `#${idx + 1}` : "#";
        const titleForLabel = text.trim() || "";
        if (scriptSceneLabel) {
            scriptSceneLabel.textContent = `${numberText} ${titleForLabel}`.trim();
        }
        setDirty();
    });

    movieTitleInput?.addEventListener("input", (e) => {
        state.movieTitle = e.target.value;
        updateSaveState();
        setDirty();
    });

    function applyCommand(cmd, value){
        if (!scriptEditor) return;
        scriptEditor.focus();
        document.execCommand(cmd, false, value);
    }


    function arrayBufferToBase64(buffer){
        const bytes = new Uint8Array(buffer);
        const chunkSize = 0x8000;
        let binary = "";
        for (let i = 0; i < bytes.length; i += chunkSize) {
            const chunk = bytes.subarray(i, i + chunkSize);
            binary += String.fromCharCode.apply(null, chunk);
        }
        return btoa(binary);
    }

    async function ensureKoreanFont(pdf){
        if (!notoFontBase64) {
            const resp = await fetch("vendor/NotoSansKR-Regular.ttf");
            if (!resp.ok) throw new Error(`font fetch failed: ${resp.status}`);
            const buf = await resp.arrayBuffer();
            notoFontBase64 = arrayBufferToBase64(buf);
        }
        pdf.addFileToVFS("NotoSansKR-Regular.ttf", notoFontBase64);
        pdf.addFont("NotoSansKR-Regular.ttf", "NotoSansKR", "normal");
        pdf.setFont("NotoSansKR", "normal");
    }

    document.querySelectorAll(".toolbar [data-block]").forEach((btn) => {
        btn.addEventListener("click", () => {
            const tag = btn.getAttribute("data-block");
            if (!tag) return;
            applyCommand("formatBlock", tag.toUpperCase());
        });
    });

    document.querySelectorAll(".toolbar [data-cmd]").forEach((btn) => {
        btn.addEventListener("click", () => {
            const cmd = btn.getAttribute("data-cmd");
            if (!cmd) return;
            applyCommand(cmd);
        });
    });

    pdfSaveBtn?.addEventListener("click", async () => {
        const scene = getActiveScene();
        const movieTitle = (state.movieTitle || "작품").trim();
        const sceneTitle = (scene?.title || "씬").trim();
        const title = `${movieTitle}_#${sceneTitle.replace(/^#/, "")}`;
        if (!scriptEditor || !window.jspdf) return alert("PDF 저장 모듈을 불러오지 못했습니다.");

        try{
            const { jsPDF } = window.jspdf;
            const pdf = new jsPDF({ orientation: "p", unit: "pt", format: "a4" });
            await ensureKoreanFont(pdf);
            const pageWidth = pdf.internal.pageSize.getWidth();
            const pageHeight = pdf.internal.pageSize.getHeight();
            const marginX = 32;
            const titleY = 36;
            const textTop = 64;
            const textWidth = pageWidth - marginX * 2;
            const text = scriptEditor.innerText.trim();

            pdf.setFontSize(14);
            pdf.text(`${movieTitle} / ${sceneTitle}`, marginX, titleY);

            pdf.setFontSize(12);
            const lines = pdf.splitTextToSize(text || " ", textWidth);
            let y = textTop;

            lines.forEach((line) => {
                if (y > pageHeight - 40) {
                    pdf.addPage();
                    pdf.setFontSize(14);
                    pdf.text(`${movieTitle} / ${sceneTitle}`, marginX, titleY);
                    pdf.setFontSize(12);
                    y = textTop;
                }
                pdf.text(line, marginX, y);
                y += 18;
            });

            pdf.save(`${title}.pdf`);
        }catch(e){
            console.error(e);
            alert(`PDF 저장 중 문제가 발생했습니다. ${e?.message || ""}`);
        }
    });

    async function uploadStoryboardImage(file){
        const fd = new FormData();
        fd.append("file", file);
        const res = await authFetch("/api/files/person/me/storyboard", { method: "POST", body: fd });
        if (!res.ok) {
            const msg = await res.text().catch(() => "");
            throw new Error(`UPLOAD_FAILED ${res.status} ${msg}`);
        }
        return await res.json();
    }

    function toScenePayload(scene){
        return {
            sceneId: scene.sceneId,
            title: scene.title,
            scriptHtml: scene.script || "",
            cards: scene.items.map((it) => ({
                cardId: it.cardId,
                imageKey: it.imageKey
            }))
        };
    }

    function applySavedScene(scene, saved){
        scene.sceneId = saved.sceneId;
        if (Array.isArray(saved.cards)) {
            saved.cards.forEach((card, idx) => {
                const it = scene.items[idx];
                if (!it) return;
                it.cardId = card.cardId;
                if (card.imageKey) it.imageKey = card.imageKey;
                if (card.imageUrl) it.imageUrl = card.imageUrl;
            });
        }
    }

    async function persistSceneOrder(){
        if (!state.publicId || !state.projectId) return;
        const allSaved = state.scenes.every(scene => !!scene.sceneId);
        if (!allSaved) return;
        const payload = { sceneIds: state.scenes.map(scene => scene.sceneId) };
        await authFetch(`/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(state.projectId)}/scenes/order`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
    }

    const fetchPublicIdByUsername = window.OnfilmCommon.fetchPublicIdByUsername;

    async function loadProjectFromApi(projectId){
        const res = await authFetch(`/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(projectId)}`, {
            headers: { "Accept": "application/json" }
        });
        if (!res.ok) throw new Error("LOAD_FAILED");
        const data = await res.json().catch(() => null);
        if (!data) return false;
        state.projectId = data.projectId || projectId;
        const rawTitle = (data.title || "").trim();
        state.movieTitle = (rawTitle === "새 스토리보드") ? "" : rawTitle;
        if (movieTitleInput) movieTitleInput.value = state.movieTitle;
        const scenes = Array.isArray(data.scenes) ? data.scenes : [];
        if (scenes.length === 0) return false;
        const sorted = [...scenes].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
        state.scenes = sorted.map((scene, idx) => {
            const rawTitle = (scene.title || "").trim();
            const title = normalizeSceneTitle(rawTitle);
            return {
            clientId: nextSceneId(),
            sceneId: scene.sceneId,
            title,
            script: scene.scriptHtml || "",
            items: Array.isArray(scene.cards) ? scene.cards.map((card, cidx) => ({
                clientId: "card_" + idx + "_" + cidx + "_" + Date.now().toString(36),
                cardId: card.cardId,
                order: cidx + 1,
                imageKey: card.imageKey,
                imageUrl: card.imageUrl,
                image: null
            })) : [],
            seq: (scene.cards || []).length
        };
        });
        state.activeSceneId = state.scenes[0]?.clientId || null;
        return true;
    }

    cancelStoryboardBtn?.addEventListener("click", () => {
        handleLeaveAttempt();
    });

    saveStoryboardBtn?.addEventListener("click", async () => {
        const scene = getActiveScene();
        if (!scene) return;
        if (!state.publicId) return alert("프로필 정보를 불러오지 못했습니다.");
        const title = movieTitleInput ? movieTitleInput.value.trim() : "";
        if (!title) return alert("작품 제목을 입력해주세요.");
        try {
            if (!state.projectId) {
                const res = await authFetch(`/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json", "Accept": "application/json" },
                    body: JSON.stringify({ title })
                });
                if (!res.ok) throw new Error("PROJECT_CREATE_FAILED");
                const project = await res.json().catch(() => null);
                state.projectId = project?.projectId || project?.id || null;
            } else {
                await authFetch(`/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(state.projectId)}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ title })
                });
            }

            for (const targetScene of state.scenes) {
                const payload = toScenePayload(targetScene);
                const url = targetScene.sceneId
                        ? `/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(state.projectId)}/scenes/${targetScene.sceneId}`
                        : `/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(state.projectId)}/scenes`;
                const method = targetScene.sceneId ? "PUT" : "POST";
                const res = await authFetch(url, {
                    method,
                    headers: { "Content-Type": "application/json", "Accept": "application/json" },
                    body: JSON.stringify(payload)
                });
                if (!res.ok) throw new Error("SAVE_FAILED");
                const saved = await res.json();
                applySavedScene(targetScene, saved);
            }

            await persistSceneOrder();
            setDirty(false);
            try{
                if (state.projectId) {
                    localStorage.setItem(`onfilm.storyboard.updated.${state.projectId}`, new Date().toISOString());
                }
            }catch(_){}
            window.location.href = `/storyboard-view.html?projectId=${encodeURIComponent(state.projectId)}`;
        } catch (e) {
            console.error(e);
            alert("저장 중 문제가 발생했습니다.");
        }
    });

    deleteSceneBtn?.addEventListener("click", async () => {
        if (state.scenes.length <= 1) return alert("최소 1개의 씬이 필요합니다.");
        const scene = getActiveScene();
        if (!scene) return;
        const idx = state.scenes.findIndex(s => s.clientId === scene.clientId);
        if (idx === -1) return;
        try{
            if (scene.sceneId && state.publicId && state.projectId) {
                const res = await authFetch(`/api/people/${encodeURIComponent(state.publicId)}/storyboard/projects/${encodeURIComponent(state.projectId)}/scenes/${scene.sceneId}`, {
                    method: "DELETE"
                });
                if (!res.ok) throw new Error("DELETE_FAILED");
            }
            state.scenes.splice(idx, 1);
            const next = state.scenes[Math.max(0, idx - 1)];
            state.activeSceneId = next ? next.clientId : null;
            if (!state.activeSceneId) addScene();
            renderAll();
            setDirty();
        }catch(e){
            console.error(e);
            alert("삭제 중 문제가 발생했습니다.");
        }
    });

    // scene drag & drop reorder
    let draggedSceneBtn = null;

    function reorderScenesFromDOM(){
        const buttons = Array.from(sceneTabs.querySelectorAll(".scene-tab")).filter(btn => !btn.classList.contains("scene-add"));
        const ordered = buttons.map(btn => state.scenes.find(scene => scene.clientId === btn.dataset.sceneId)).filter(Boolean);
        if (ordered.length === state.scenes.length) state.scenes = ordered;
        renderAll();
        setDirty();
        persistSceneOrder();
    }

    sceneTabs?.addEventListener("dragstart", (e) => {
        const btn = e.target.closest(".scene-tab");
        if (!btn || btn.classList.contains("scene-add")) return;
        draggedSceneBtn = btn;
        btn.classList.add("active");
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", btn.dataset.sceneId || "");
    });

    sceneTabs?.addEventListener("dragover", (e) => {
        e.preventDefault();
        if (!draggedSceneBtn) return;
        const target = e.target.closest(".scene-tab");
        if (!target || target === draggedSceneBtn || target.classList.contains("scene-add")) return;
        const rect = target.getBoundingClientRect();
        const after = e.clientX > rect.left + rect.width / 2;
        if (after) target.after(draggedSceneBtn);
        else target.before(draggedSceneBtn);
    });

    sceneTabs?.addEventListener("drop", (e) => {
        e.preventDefault();
        if (!draggedSceneBtn) return;
        draggedSceneBtn = null;
        reorderScenesFromDOM();
    });

    sceneTabs?.addEventListener("dragend", () => {
        if (!draggedSceneBtn) return;
        draggedSceneBtn = null;
        reorderScenesFromDOM();
    });

    document.addEventListener("DOMContentLoaded", async () => {
        if (state.initialized) return;
        state.initialized = true;
        window.addEventListener("dragover", (e) => e.preventDefault());
        window.addEventListener("drop", (e) => e.preventDefault());

        if (!window.OnfilmAuth?.restoreSession) return;
        const result = await window.OnfilmAuth.restoreSession();
        if (!result?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }
        const me = result.me;
        const uname = me?.username ? String(me.username).trim() : "";
        if (!uname) {
            alert("사용자 정보를 불러오지 못했습니다.");
            return;
        }
        if (backToProfileLink) {
            backToProfileLink.href = "/storyboard.html";
        }
        try{
            state.publicId = await fetchPublicIdByUsername(uname);
            const params = new URLSearchParams(location.search);
            const projectId = params.get("projectId");
            if (projectId) {
                const loaded = await loadProjectFromApi(projectId);
                if (!loaded) {
                    state.scenes = [];
                    state.activeSceneId = null;
                    addScene();
                }
                renderAll();
                updateSaveState();
                setDirty(false);
                return;
            }

            state.projectId = null;
            state.movieTitle = "";
            if (movieTitleInput) movieTitleInput.value = "";
            state.scenes = [];
            state.activeSceneId = null;
            addScene();
            renderAll();
            updateSaveState();
            setDirty(false);
        }catch(e){
            console.error(e);
            state.scenes = [];
            state.activeSceneId = null;
            addScene();
            renderAll();
            updateSaveState();
            setDirty(false);
        }
    });

    backToProfileLink?.addEventListener("click", (e) => {
        e.preventDefault();
        handleLeaveAttempt();
    });

    window.addEventListener("beforeunload", (e) => {
        if (!hasAnyInput() || !state.dirty) return;
        e.preventDefault();
        e.returnValue = "";
    });
