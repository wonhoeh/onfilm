
    /* =========================================================
       0) AUTH READY (토큰/ME 캐시 준비 보장)
    ========================================================= */
    window.__ONFILM_AUTH_READY_PROMISE__ = (async () => {
        try {
            if (!window.OnfilmAuth?.restoreSession) return { ok:false, me:null };
            return await window.OnfilmAuth.restoreSession();
        } catch (e) {
            console.error("restoreSession failed:", e);
            return { ok:false, me:null };
        }
    })();

    /* =========================================================
       1) API HELPER (username -> publicId -> person)
       - 너가 바꾼 엔드포인트: /api/person/{username}
    ========================================================= */
    async function getUsernameSafe() {
        let me = window.OnfilmAuth?.getMe?.() || null;
        if (!me || !me.username) {
            const r = await window.OnfilmAuth?.restoreSession?.().catch(() => null);
            me = r?.me || me;
        }
        const uname = me?.username ? String(me.username).trim() : "";
        return uname || null;
    }

    // ✅ /api/person/{username} -> publicId
    const fetchPublicIdByUsername = async (username) => {
        try {
            const id = await window.OnfilmCommon.fetchPublicIdByUsername(username);
            return id ? String(id) : null;
        } catch (_) {
            return null;
        }
    };

    // ✅ /api/people/{publicId} -> person
    const fetchPersonByPublicId = async (publicId) => {
        try {
            return await window.OnfilmCommon.fetchPersonByPublicId(publicId);
        } catch (_) {
            return null;
        }
    };

    function personHasAnyContent(p) {
        return !!(p?.name && String(p.name).trim())
            || !!(p?.oneLineIntro && String(p.oneLineIntro).trim())
            || !!(p?.birthDate && String(p.birthDate).trim())
            || !!(p?.birthPlace && String(p.birthPlace).trim())
            || !!(p?.profileImageUrl && String(p.profileImageUrl).trim())
            || (Array.isArray(p?.snsList) && p.snsList.length > 0)
            || (Array.isArray(p?.rawTags) && p.rawTags.length > 0);
    }

    /* =========================================================
       2) ELEMENTS / COMMON UI
    ========================================================= */
    const form = document.getElementById("editProfileForm");
    const saveBtn = form?.querySelector('button[type="submit"]');

    const profileFileInput = document.getElementById("profileFileInput");
    const profilePreview   = document.getElementById("profilePreview");
    const photoSection     = document.querySelector(".photo-section");
    const deleteProfileBtn = document.getElementById("deleteProfileBtn");

    const snsList   = document.getElementById("snsList");
    const addSnsBtn = document.getElementById("addSnsBtn");

    const tagListEl = document.getElementById("tagList");
    const tagInput  = document.getElementById("tagInput");
    const addTagBtn = document.getElementById("addTagBtn");

    const yearInput  = document.getElementById("birthYear");
    const monthInput = document.getElementById("birthMonth");
    const dayInput   = document.getElementById("birthDay");
    const birthDateHidden = document.getElementById("birthDate");

    const PLACEHOLDER_SRC = "/videos/profile-placeholder.png";

    function setSaving(isSaving) {
        if (!saveBtn) return;
        saveBtn.disabled = isSaving;
        saveBtn.textContent = isSaving ? "저장 중..." : "저장";
    }

    function setupAutoMove(current, next) {
        if (!current) return;
        current.addEventListener("input", () => {
            current.value = current.value.replace(/\D/g, "");
            const max = current.getAttribute("maxlength");
            if (max && current.value.length >= parseInt(max, 10) && next) next.focus();
        });
    }
    setupAutoMove(yearInput, monthInput);
    setupAutoMove(monthInput, dayInput);

    /* =========================================================
       3) PHOTO: preview + drag/drop
    ========================================================= */
    function loadProfileImage(file) {
        if (!file) return;

        if (!file.type.startsWith("image/")) {
            alert("이미지 파일만 업로드할 수 있어요. (JPG, PNG 등)");
            return;
        }

        const MAX_SIZE = 10 * 1024 * 1024;
        if (file.size > MAX_SIZE) {
            alert("최대 10MB까지 업로드할 수 있어요.");
            return;
        }

        const reader = new FileReader();
        reader.onload = (ev) => { profilePreview.src = ev.target.result; };
        reader.readAsDataURL(file);

        // input.files 세팅(가능한 브라우저에서)
        try {
            const dt = new DataTransfer();
            dt.items.add(file);
            profileFileInput.files = dt.files;
        } catch (e) {
            console.warn("DataTransfer 미지원 브라우저일 수 있습니다.", e);
        }
    }

    profileFileInput?.addEventListener("change", (e) => {
        const file = e.target.files[0];
        loadProfileImage(file);
    });

    deleteProfileBtn?.addEventListener("click", async () => {
        if (!confirm("프로필 사진을 삭제할까요?")) return;

        const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
        if (!authReady?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }

        const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
                ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
                : fetch;

        const res = await fetcher("/api/files/person/me/profile", { method: "DELETE" });
        if (!res.ok && res.status !== 404) {
            const msg = await res.text().catch(() => "");
            alert(`삭제 실패: ${res.status} ${msg}`);
            return;
        }

        profilePreview.src = PLACEHOLDER_SRC;
        profilePreview.onerror = null;
        profileFileInput.value = "";
        window.__EXISTING_PROFILE_IMAGE_URL__ = null;
        window.__EXISTING_PROFILE_IMAGE_KEY__ = null;
    });

    if (photoSection) {
        ["dragenter", "dragover"].forEach(evtName => {
            photoSection.addEventListener(evtName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                profilePreview.classList.add("drop-hover");
            });
        });

        ["dragleave", "dragend", "drop"].forEach(evtName => {
            photoSection.addEventListener(evtName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                if (evtName !== "drop") profilePreview.classList.remove("drop-hover");
            });
        });

        photoSection.addEventListener("drop", (e) => {
            e.preventDefault();
            e.stopPropagation();
            profilePreview.classList.remove("drop-hover");
            const files = e.dataTransfer.files;
            if (!files || files.length === 0) return;
            loadProfileImage(files[0]);
        });
    }

    /* =========================================================
       4) SNS: add/remove rows
    ========================================================= */
    function createSnsRow(initial = {type:"instagram", url:""}) {
        const row = document.createElement("div");
        row.className = "sns-row";

        const select = document.createElement("select");
        select.className = "select-input";
        select.innerHTML = `
    <option value="instagram">Instagram</option>
    <option value="youtube">YouTube</option>
    <option value="tiktok">TikTok</option>
    <option value="etc">기타</option>
  `;
        select.value = initial.type || "instagram";

        const input = document.createElement("input");
        input.className = "text-input";
        input.type = "url";
        input.placeholder = "www.example.com/...";
        input.value = initial.url || "";

        const removeBtn = document.createElement("button");
        removeBtn.type = "button";
        removeBtn.className = "sns-remove-btn";
        removeBtn.textContent = "✕";
        removeBtn.addEventListener("click", () => {
            snsList.removeChild(row);
            if (snsList.children.length === 0) addSnsRow();
        });

        row.appendChild(select);
        row.appendChild(input);
        row.appendChild(removeBtn);
        snsList.appendChild(row);
    }

    function addSnsRow() { createSnsRow({}); }
    addSnsBtn?.addEventListener("click", addSnsRow);

    /* =========================================================
       5) TAGS: add/remove + drag reorder
    ========================================================= */
    let draggedChip = null;
    function handleTagDragStart(e) { draggedChip = e.target; draggedChip.classList.add("dragging"); }
    function handleTagDragEnd() { if (draggedChip) draggedChip.classList.remove("dragging"); draggedChip = null; }

    function addTag(text) {
        const trimmed = String(text || "").trim();
        if (!trimmed) return;

        const label = trimmed.startsWith("#") ? trimmed : `#${trimmed}`;
        const chip = document.createElement("span");
        chip.className = "tag-chip";
        chip.textContent = label;

        chip.draggable = true;
        chip.addEventListener("dragstart", handleTagDragStart);
        chip.addEventListener("dragend", handleTagDragEnd);

        chip.addEventListener("click", () => {
            if (chip.classList.contains("dragging")) return;
            tagListEl.removeChild(chip);
        });

        tagListEl.appendChild(chip);
    }

    addTagBtn?.addEventListener("click", () => {
        addTag(tagInput.value);
        tagInput.value = "";
        tagInput.focus();
    });

    tagInput?.addEventListener("keydown", (e) => {
        if (e.isComposing || e.keyCode === 229) return;
        if (e.key === "Enter") {
            e.preventDefault();
            addTag(tagInput.value);
            tagInput.value = "";
        }
    });

    tagListEl?.addEventListener("dragover", (e) => {
        e.preventDefault();
        if (!draggedChip) return;

        const target = e.target.closest(".tag-chip");
        if (!target || target === draggedChip) return;

        const rect = target.getBoundingClientRect();
        const isAfter = e.clientX > rect.left + rect.width / 2;
        if (isAfter) target.after(draggedChip);
        else target.before(draggedChip);
    });

    /* =========================================================
       6) SERVER DATA MAPPING (prefill)
    ========================================================= */
    function mapServerSnsTypeToUi(typeUpper) {
        const t = (typeUpper || "").toUpperCase();
        if (t === "INSTAGRAM") return "instagram";
        if (t === "YOUTUBE") return "youtube";
        if (t === "TIKTOK") return "tiktok";
        return "etc";
    }

    function normalizeRawTags(rawTags) {
        const arr = Array.isArray(rawTags) ? rawTags : [];
        return arr
            .map(t => (typeof t === "string" ? t : (t?.rawTag || "")))
            .map(s => String(s || "").trim())
            .filter(Boolean);
    }

    function getServerProfileImageField(personObj) {
        // 서버가 key 기반 DTO를 내려주면 그걸 우선 사용
        // (없으면 기존 url 기반으로 동작)
        if (personObj && Object.prototype.hasOwnProperty.call(personObj, "profileImageKey")) return "profileImageKey";
        return "profileImageUrl";
    }

    /* =========================================================
       7) UPLOAD (profile image) + SAVE (POST/PUT)
       - 프로필 이미지 업로드 API 적용:
         POST /{personId}/profile  (보통 @RequestMapping("/files") 아래라서 /files/{personId}/profile)
         응답: { key, url }
    ========================================================= */

    async function ensureEditingPersonId() {
        let pid = window.__EDITING_PERSON_ID__;
        if (pid != null) return pid;

        // publicId로 한번 더 조회해서 id 확보
        const pub = (window.__EDITING_PUBLIC_ID__ || "").toString().trim();
        if (pub) {
            const p = await fetchPersonByPublicId(pub);
            pid = p?.id ?? p?.personId ?? null;
            if (pid != null) {
                window.__EDITING_PERSON_ID__ = pid;
                return pid;
            }
        }

        // 마지막 fallback: username -> publicId -> person
        const uname = await getUsernameSafe();
        if (uname) {
            const publicId = pub || await fetchPublicIdByUsername(uname);
            if (publicId) {
                window.__EDITING_PUBLIC_ID__ = publicId;
                const p2 = await fetchPersonByPublicId(publicId);
                pid = p2?.id ?? p2?.personId ?? null;
                if (pid != null) {
                    window.__EDITING_PERSON_ID__ = pid;
                    return pid;
                }
            }
        }
        return null;
    }

    async function uploadProfileImageIfAny() {
        const file = profileFileInput.files && profileFileInput.files[0];
        if (!file) return null;

        const buildFormData = () => {
            const fd = new FormData();
            fd.append("file", file);
            return fd;
        };

        // ✅ /me 기반 업로드 (personId 필요 없음)
        const candidates = [
            `/api/files/person/me/profile`,
            // 혹시 서버에 /files로 붙어있던 레거시가 있으면 백업:
            `/files/person/me/profile`,
        ];

        let res = null;
        for (const u of candidates) {
            res = await window.OnfilmAuth.apiFetchWithAutoRefresh(u, {
                method: "POST",
                body: buildFormData(),
            });
            if (res.status !== 404) break;
        }

        if (!res || !res.ok) {
            const msg = res ? await res.text().catch(() => "") : "";
            throw new Error(`이미지 업로드 실패: ${res?.status || "ERR"} ${msg}`);
        }

        const data = await res.json().catch(() => null); // { key, url }
        const key = data?.key != null ? String(data.key) : null;
        const url = data?.url != null ? String(data.url) : null;

        // 전역 캐시 업데이트(미리보기/유지용)
        if (key) window.__EXISTING_PROFILE_IMAGE_KEY__ = key;
        if (url) window.__EXISTING_PROFILE_IMAGE_URL__ = url;

        // 업로드 결과 URL로 미리보기 갱신
        if (url) {
            profilePreview.src = url;
            profilePreview.onerror = () => {
                profilePreview.onerror = null;
                profilePreview.src = PLACEHOLDER_SRC;
            };
        }

        return { key, url };
    }


    function buildBirthDateOrThrow() {
        if (!yearInput.value || !monthInput.value || !dayInput.value) {
            throw new Error("출생일을 모두 입력해 주세요.");
        }
        const y = yearInput.value.padStart(4, "0");
        const m = monthInput.value.padStart(2, "0");
        const d = dayInput.value.padStart(2, "0");
        const iso = `${y}-${m}-${d}`;
        birthDateHidden.value = iso;
        return iso;
    }

    function collectSnsPayload() {
        const snsData = [];
        snsList.querySelectorAll(".sns-row").forEach(row => {
            const type = row.querySelector("select").value; // instagram/youtube/tiktok/etc
            const raw  = row.querySelector("input").value.trim();
            if (!raw) return;

            let finalUrl = raw;

            // scheme 없으면 보정
            if (!/^https?:\/\//i.test(raw)) {
                if (type === "instagram") {
                    const handle = raw.replace(/^@/, "");
                    finalUrl = `https://www.instagram.com/${handle}`;
                } else if (type === "youtube") {
                    finalUrl = `https://www.youtube.com/${raw}`;
                } else if (type === "tiktok") {
                    const handle = raw.replace(/^@/, "");
                    finalUrl = `https://www.tiktok.com/@${handle}`;
                } else {
                    finalUrl = `https://${raw}`;
                }
            }

            snsData.push({ type: type.toUpperCase(), url: finalUrl });
        });
        return snsData.length ? snsData : null;
    }

    function collectTagsPayload() {
        const rawTags = [];
        tagListEl.querySelectorAll(".tag-chip").forEach(chip => {
            rawTags.push(chip.textContent.replace(/^#/, "").trim());
        });
        return rawTags.length ? rawTags : null;
    }

    form?.addEventListener("submit", async (e) => {
        e.preventDefault(); // ✅ await보다 먼저!

        const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
        if (!authReady?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }

        if (!window.OnfilmAuth?.apiFetchWithAutoRefresh) {
            alert("auth.js에 apiFetchWithAutoRefresh가 필요해요.");
            return;
        }

        try {
            const name = document.getElementById("actorName").value.trim();
            if (!name) throw new Error("이름을 입력해 주세요.");

            const birthDate = buildBirthDateOrThrow();

            // ✅ 서버가 key 기반인지 url 기반인지에 따라 보존 필드 결정
            const imgField = (window.__SERVER_PROFILE_IMAGE_FIELD__ || "profileImageUrl").toString();
            const existingKey = (window.__EXISTING_PROFILE_IMAGE_KEY__ || "").toString().trim() || null;
            const existingUrl = (window.__EXISTING_PROFILE_IMAGE_URL__ || "").toString().trim() || null;

            const payload = {
                name,
                birthDate,
                birthPlace: document.getElementById("birthPlace").value.trim(),
                oneLineIntro: document.getElementById("bioInput").value.trim(),
                snsList: collectSnsPayload(),
                rawTags: collectTagsPayload(),
            };

            // ✅ 기존 이미지 값 유지(새 업로드가 없을 때 지워지는 사고 방지)
            // - 서버가 key 기반이면 key를, 아니면 url을 보존값으로 포함
            if (imgField === "profileImageKey") {
                if (existingKey) payload.profileImageKey = existingKey;
            } else {
                if (existingUrl) payload.profileImageUrl = existingUrl;
            }

            setSaving(true);

            const hasNewFile = profileFileInput.files && profileFileInput.files.length > 0;

            // 1) 수정/생성 판단: publicId 기준
            let editingPublicId = (window.__EDITING_PUBLIC_ID__ || "").toString().trim();
            const isEdit = !!editingPublicId;

            const url = isEdit
                ? `/api/people/${encodeURIComponent(editingPublicId)}`
                : `/api/people`;
            const method = isEdit ? "PUT" : "POST";

            const res = await window.OnfilmAuth.apiFetchWithAutoRefresh(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                const msg = await res.text().catch(() => "");
                throw new Error(`저장 실패: ${res.status}\n${msg}`);
            }

            // 2) 생성(POST)인 경우, 응답에서 publicId / id를 최대한 회수해서 이후 업로드에 사용
            const saved = await res.json().catch(() => null);
            if (!isEdit && saved) {
                if (saved.publicId != null) {
                    window.__EDITING_PUBLIC_ID__ = String(saved.publicId);
                    editingPublicId = window.__EDITING_PUBLIC_ID__;
                }
                const pid = saved.id ?? saved.personId ?? null;
                if (pid != null) window.__EDITING_PERSON_ID__ = pid;
                // 서버가 key 기반 DTO를 준다면 플래그 업데이트
                if (Object.prototype.hasOwnProperty.call(saved, "profileImageKey")) {
                    window.__SERVER_PROFILE_IMAGE_FIELD__ = "profileImageKey";
                }
            }

            // 3) ✅ 프로필 이미지 업로드(선택) — 새 POST API 적용
            //    POST /files/{personId}/profile  -> { key, url }
            if (hasNewFile) {
                const uploaded = await uploadProfileImageIfAny(); // 내부에서 personId 확보 후 업로드
                // 서버가 key 기반이라면, 업로드 결과 key를 보관
                if (uploaded?.key) window.__EXISTING_PROFILE_IMAGE_KEY__ = uploaded.key;
                if (uploaded?.url) window.__EXISTING_PROFILE_IMAGE_URL__ = uploaded.url;
            }

            alert("저장 완료!");

            const uname = await getUsernameSafe();
            if (uname) location.href = `/onfilm/${encodeURIComponent(uname)}`;
            else location.href = `/onfilm`;

        } catch (err) {
            console.error(err);
            alert(err?.message || "저장 중 오류가 발생했습니다.");
        } finally {
            setSaving(false);
        }
    });

    /* =========================================================
       8) PREFILL (저장된 데이터 폼에 채우기)
    ========================================================= */
    async function preloadProfileToEditForm() {
        const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
        if (!authReady?.ok) return;

        const uname = await getUsernameSafe();
        if (!uname) return;

        // ✅ username -> publicId (변경된 경로 반영)
        const publicId = await fetchPublicIdByUsername(uname);
        if (!publicId) return;

        const p = await fetchPersonByPublicId(publicId);
        if (!p) return;

        window.__EDITING_PUBLIC_ID__ = publicId;

        // ✅ personId (Long) 확보 (업로드 API에 필요)
        window.__EDITING_PERSON_ID__ = p?.id ?? p?.personId ?? null;

        // ✅ 서버가 key 기반인지 url 기반인지 기억
        window.__SERVER_PROFILE_IMAGE_FIELD__ = getServerProfileImageField(p);

        // ✅ 기존 이미지 값 캐시(유지/보존용)
        window.__EXISTING_PROFILE_IMAGE_URL__ = (p?.profileImageUrl || "").toString().trim() || null;
        window.__EXISTING_PROFILE_IMAGE_KEY__ = (p?.profileImageKey || "").toString().trim() || null;

        // 기본 필드
        document.getElementById("actorName").value = p?.name || "";
        document.getElementById("birthPlace").value = p?.birthPlace || "";
        document.getElementById("bioInput").value = p?.oneLineIntro || "";

        // birth yyyy-mm-dd
        const birth = (p?.birthDate || "").trim();
        if (birth) {
            const [yy, mm, dd] = birth.split("-");
            if (yy) yearInput.value = yy;
            if (mm) monthInput.value = mm;
            if (dd) dayInput.value = dd;
            birthDateHidden.value = birth;
        }

        // profile image
        const placeholder = profilePreview.getAttribute("src");
        const real = (p?.profileImageUrl || "").trim();
        if (real) {
            profilePreview.src = real;
            profilePreview.onerror = () => { profilePreview.onerror = null; profilePreview.src = placeholder; };
        } else {
            profilePreview.src = placeholder;
        }

        // SNS: 초기화 후 채움
        snsList.innerHTML = "";
        const sList = Array.isArray(p?.snsList) ? p.snsList : [];
        if (sList.length > 0) {
            sList.forEach(s => createSnsRow({ type: mapServerSnsTypeToUi(s?.type), url: String(s?.url || "").trim() }));
        } else {
            addSnsRow();
        }

        // Tags: 초기화 후 저장값만
        tagListEl.innerHTML = "";
        normalizeRawTags(p?.rawTags).forEach(t => addTag(t));
    }

    /* =========================================================
       9) BACK LINK (프로필로 돌아가기)
       - 프로필 존재+내용 있으면 /onfilm/{username}
       - 아니면 /index.html
    ========================================================= */
    async function handleBackToProfile(e) {
        e.preventDefault();

        try {
            const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
            if (!authReady?.ok) {
                window.location.href = "/index.html";
                return;
            }

            const uname = await getUsernameSafe();
            if (!uname) {
                window.location.href = "/index.html";
                return;
            }

            const publicId = await fetchPublicIdByUsername(uname);
            if (!publicId) {
                window.location.href = "/index.html";
                return;
            }

            const p = await fetchPersonByPublicId(publicId);
            if (!p || !personHasAnyContent(p)) {
                window.location.href = "/index.html";
                return;
            }

            window.location.href = "/onfilm/" + encodeURIComponent(uname);
        } catch (_) {
            window.location.href = "/index.html";
        }
    }

    /* =========================================================
       10) INIT (DOMContentLoaded)
    ========================================================= */
    document.addEventListener("DOMContentLoaded", async () => {
        const result = await window.__ONFILM_AUTH_READY_PROMISE__;
        if (!result?.ok) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login.html?next=${next}`;
            return;
        }

        // SNS 최소 1행 보장(프리필이 덮어씌우면 거기서 다시 세팅됨)
        if (snsList.children.length === 0) addSnsRow();

        await preloadProfileToEditForm();

        // back link
        const link = document.getElementById("backToProfileLink");
        link?.addEventListener("click", handleBackToProfile);
    });

    // 전체 드롭 기본 동작 방지(페이지 이동 방지)
    window.addEventListener("dragover", (e) => e.preventDefault());
    window.addEventListener("drop", (e) => e.preventDefault());
