    /* =========================================================
       OnFilm Actor Detail
       URL:  /onfilm/{username}
       API:  /api/person/{username} -> { publicId }
             /api/people/{publicId}
             /api/people/{publicId}/movies
             /api/people/{publicId}/gallery   (있으면 사용, 없으면 empty 처리)
    ========================================================= */

    let popup = null;
    let currentPublicId = null;

    /* ---------------------------
       0) Utils
    --------------------------- */
    const $  = (sel) => document.querySelector(sel);
    const $$ = (sel) => Array.from(document.querySelectorAll(sel));

    function escapeHtml(str) {
        return String(str ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function safeHostname(url) {
        try { return new URL(url).hostname; }
        catch { return ""; }
    }

    function toPublicMediaUrl(value) {
        const raw = String(value || "").trim();
        if (!raw) return "";
        if (raw.toLowerCase() === "pending") return "";
        if (/^https?:\/\//i.test(raw)) return raw;
        if (raw.startsWith("/files/")) return raw;
        if (raw.startsWith("files/")) return "/" + raw;
        return "/files/" + raw.replace(/^\/+/, "");
    }

    function lockBodyScroll(lock) {
        document.body.style.overflow = lock ? "hidden" : "";
    }

    /* ---------------------------
       1) Path
    --------------------------- */
    function getUsernameFromPath() {
        const parts = window.location.pathname.split("/").filter(Boolean);
        if (parts.length >= 2 && parts[0] === "onfilm") return decodeURIComponent(parts[1]);
        return null;
    }

    /* ---------------------------
       2) API helpers
    --------------------------- */
    async function apiGetJson(url) {
        const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

        const res = await fetcher(url, { headers: { "Accept": "application/json" }});
        if (!res.ok) throw new Error(`API_FAILED ${res.status} ${url}`);
        return await res.json();
    }

    async function fetchPublicIdByUsername(username) {
        return apiGetJson(`/api/person/${encodeURIComponent(username)}`); // { publicId }
    }

    async function fetchPersonByPublicId(publicId) {
        return apiGetJson(`/api/people/${encodeURIComponent(publicId)}`);
    }

    async function fetchFilmographyByPublicId(publicId) {
        return apiGetJson(`/api/people/${encodeURIComponent(publicId)}/movies`);
    }

    async function fetchGalleryByPublicId(publicId) {
        // ✅ 서버에 없을 수 있으니 실패해도 빈 배열로 처리
        try {
            return await apiGetJson(`/api/people/${encodeURIComponent(publicId)}/gallery`);
        } catch (e) {
            return [];
        }
    }

    /* ---------------------------
       3) Auth / Owner helpers
    --------------------------- */
    async function restoreMeSafe() {
        try {
            if (!window.OnfilmAuth?.restoreSession) return null;
            const result = await window.OnfilmAuth.restoreSession();
            if (result && result.ok && result.me) return result.me;
        } catch (_) {}
        return null;
    }

    // ✅ owner 판정 개선: id/ownerId 뿐 아니라 username도 비교 (서버 스펙이 바뀌어도 안전)
    function computeIsOwner(me, person, pathUsername) {
        if (!me) return false;

        const myId = me?.id;
        const personId = person?.id;
        const ownerId  = person?.ownerId;

        if (myId != null && personId != null && String(myId) === String(personId)) return true;
        if (myId != null && ownerId  != null && String(myId) === String(ownerId))  return true;

        const myUsername = String(me?.username || "").trim();
        const uname = String(pathUsername || "").trim();
        if (myUsername && uname && myUsername === uname) return true;

        return false;
    }

    function applyOwnerUI(isOwner){
        $("#profileSettingsBtn")?.classList.toggle("hidden", !isOwner);

        const overlay = $("#profileEditOverlay");
        overlay?.classList.toggle("hidden", !isOwner);

        if (!isOwner && overlay) {
            overlay.classList.remove("show");
            lockBodyScroll(false);
        }

        $$(".owner-only").forEach(el => {
            el.classList.remove("hidden-keep-space");
            if (!isOwner) el.classList.add("hidden-keep-space");
        });

        window.__isOwner = !!isOwner;
    }

    /* ---------------------------
       4) Profile binding UI
    --------------------------- */
    function applyPersonToUI(p) {
        const nameEl = $("#profileName");
        if (nameEl) {
            nameEl.textContent = p?.name || "회원";
            nameEl.dataset.value = p?.name || "";
        }

        const metaEl = $("#profileMeta");
        if (metaEl) {
            const birthDate  = p?.birthDate  || "";
            const birthPlace = p?.birthPlace || "";
            metaEl.dataset.birthDate  = birthDate;
            metaEl.dataset.birthPlace = birthPlace;

            const left  = birthDate  ? birthDate  : "생년월일 미등록";
            const right = birthPlace ? birthPlace : "출생지 미등록";
            metaEl.textContent = `${left} · ${right}`;
        }

        const bioEl = $("#bioDisplay");
        if (bioEl) {
            bioEl.textContent = p?.oneLineIntro || "소개글을 입력해 주세요.";
            bioEl.dataset.value = p?.oneLineIntro || "";
        }

        const imgEl = $("#profilePhoto");
        if (imgEl) {
            const placeholder = imgEl.getAttribute("src");
            const real = (p?.profileImageUrl || "").trim();
            imgEl.dataset.profileSrc = real;
            imgEl.src = real || placeholder;
            imgEl.onerror = () => { imgEl.onerror = null; imgEl.src = placeholder; };
        }

        const snsRow = $("#snsRow");
        if (snsRow) {
            snsRow.innerHTML = "";
            const list = Array.isArray(p?.snsList) ? p.snsList : [];
            list.forEach(sns => {
                const type = (sns?.type || "").toString().toUpperCase();
                let url = (sns?.url || "").toString().trim();
                if (!url) return;
                if (!/^https?:\/\//i.test(url)) url = "https://" + url;

                const brand =
                    type === "INSTAGRAM" ? "instagram" :
                        type === "YOUTUBE"   ? "youtube"   :
                            type === "NAVER"     ? "naver"     :
                                (type === "TWITTER" || type === "X") ? "x" : "x";

                const a = document.createElement("a");
                a.className = "sns-pill";
                a.href = url;
                a.target = "_blank";
                a.rel = "noopener";
                a.dataset.brand = brand;
                a.innerHTML = `
          <span class="sns-badge"><span class="sns-icon"></span></span>
          <span>${escapeHtml(type)}</span>
          <span class="sns-handle">${escapeHtml(safeHostname(url))}</span>
        `;
                snsRow.appendChild(a);
            });
        }

        const tagListEl = $("#tagList");
        if (tagListEl) {
            tagListEl.innerHTML = "";
            const tags = Array.isArray(p?.rawTags) ? p.rawTags : [];
            tags.forEach(t => {
                const raw = (typeof t === "string" ? t : (t?.rawTag || "")).toString().trim();
                if (!raw) return;
                const span = document.createElement("span");
                span.className = "profile-tag";
                span.textContent = raw.startsWith("#") ? raw : `#${raw}`;
                tagListEl.appendChild(span);
            });
        }

        updateEmptyUI();
        window.__filmographyPrivate = !!p?.filmographyPrivate;
    }

    /* ---------------------------
       5) Filmography (cards + slider + popup)
    --------------------------- */
    const slider   = $("#filmSlider");
    const prevBtn  = $("#filmPrevBtn");
    const nextBtn  = $("#filmNextBtn");
    const filmLine = $(".film-slider-container");

    let index = 0;

    function getCardWidth(){
        const card = slider?.children?.[0];
        if (!card) return 0;
        const style = window.getComputedStyle(card);
        const marginRight = parseInt(style.marginRight) || 0;
        return card.offsetWidth + marginRight + 20;
    }

    function updateArrows() {
        if (!slider || !prevBtn || !nextBtn) return;

        const cardCount = slider.children.length;
        const maxIndex = cardCount - 3;

        if (cardCount <= 3) {
            prevBtn.style.display = "none";
            nextBtn.style.display = "none";
            return;
        }

        if (index > maxIndex) index = maxIndex;
        if (index < 0) index = 0;

        prevBtn.style.display = (index === 0) ? "none" : "flex";
        nextBtn.style.display = (index >= maxIndex) ? "none" : "flex";
    }

    function updateSlider(){
        if (!slider) return;
        const w = getCardWidth();
        slider.style.transform = `translateX(${-w * index}px)`;
        updateArrows();
    }

    nextBtn?.addEventListener("click", () => {
        const maxIndex = slider.children.length - 3;
        if (index < maxIndex) { index++; updateSlider(); }
    });

    prevBtn?.addEventListener("click", () => {
        if (index > 0) { index--; updateSlider(); }
    });

    window.addEventListener("mousemove", (e) => {
        if (!filmLine || !prevBtn || !nextBtn) return;
        const rect = filmLine.getBoundingClientRect();
        const y = e.clientY;
        const inFilmLine = y >= rect.top && y <= rect.bottom;

        if (inFilmLine) updateArrows();
        else {
            if (!popup || !popup.classList.contains("show")) {
                prevBtn.style.display = "none";
                nextBtn.style.display = "none";
            }
        }
    });

    function ageToBadge(ageRating) {
        switch ((ageRating || "").toUpperCase()) {
            case "ALL": return "ALL";
            case "AGE_12": return "12";
            case "AGE_15": return "15";
            case "AGE_18": return "18";
            default: return "";
        }
    }

    function castTypeToKorean(castType) {
        switch ((castType || "").toUpperCase()) {
            case "LEAD": return "주연";
            case "SUPPORTING": return "조연";
            case "CAMEO": return "카메오";
            default: return "";
        }
    }

    function createFilmCard(item) {
        const article = document.createElement("article");
        article.className = "film-card";

        const title = item?.title ?? "";
        const genre = item?.genre ?? "";
        const runtime = (item?.runtime != null) ? `${item.runtime}분` : "";
        const age = ageToBadge(item?.ageRating);
        const video = toPublicMediaUrl(item?.trailerUrl || "");
        const thumb = toPublicMediaUrl(item?.thumbnailUrl || "");
        const movie = toPublicMediaUrl(item?.movieUrl || "");

        const releaseYear = (item?.releaseYear != null) ? String(item.releaseYear) : "";
        const castLabel = castTypeToKorean(item?.castType);
        const roleName = (item?.characterName || item?.roleName || item?.role || "").trim();

        let metaText = "";
        if (releaseYear) metaText = releaseYear;
        if (castLabel) metaText = metaText ? `${metaText} · ${castLabel}` : castLabel;
        if (roleName) metaText = metaText ? `${metaText} · ${roleName}` : roleName;

        article.dataset.title = title;
        article.dataset.genre = genre;
        article.dataset.runtime = runtime;
        article.dataset.age = age;
        article.dataset.video = video;
        article.dataset.thumb = thumb;
        article.dataset.movie = movie;

        article.innerHTML = `
      <img class="film-poster" src="${thumb || "/videos/thumbnail.png"}" alt="">
      <div class="film-body">
        <div class="film-title">${escapeHtml(title)}</div>
        <div class="film-meta">${escapeHtml(metaText)}</div>
      </div>
    `;

        if (window.__filmographyPrivate || item?.isPrivate) {
            const lock = document.createElement("div");
            lock.className = "film-card-lock";
            lock.textContent = "🔒";
            article.appendChild(lock);
        }

        const img = article.querySelector("img.film-poster");
        if (img) img.onerror = () => { img.onerror = null; img.src = "/videos/thumbnail.png"; };

        return article;
    }

    function openVideoPlayerFromCard(card, opts = {}) {
        if (!card) return;
        if (opts.playWithSound) sessionStorage.setItem("allowSoundPlay", "true");
        else sessionStorage.removeItem("allowSoundPlay");
        const payload = {
            title: card.dataset.title || "",
            movieUrl: card.dataset.movie || "",
            trailerUrl: card.dataset.video || "",
            thumbnailUrl: card.dataset.thumb || "",
        };
        sessionStorage.setItem("onfilm.player", JSON.stringify(payload));
        window.location.href = "/video-player.html";
    }

    async function renderFilmography(publicId) {
        if (!slider) return;
        slider.innerHTML = "";

        try {
            const list = await fetchFilmographyByPublicId(publicId);
            const movies = Array.isArray(list) ? list : [];

            movies.forEach(item => slider.appendChild(createFilmCard(item)));

            index = 0;
            updateSlider();
            updateEmptyUI();
        } catch (e) {
            console.error("renderFilmography error:", e);
            updateEmptyUI();
            updateArrows();
        }
    }

    /* ---- movie popup ---- */
    let hideTimer = null;
    let activeCard = null;

    function createPopup() {
        popup = document.createElement("div");
        popup.className = "movie-popup";

        popup.innerHTML = `
      <div class="popup-video-box">
        <img class="popup-thumb" />
        <video muted loop playsinline></video>

        <button type="button" class="popup-mute-btn" aria-label="음소거 토글">
          <svg class="popup-sound-on" xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor"
            width="26" height="26" style="display:none;">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M19.114 5.636a9 9 0 0 1 0 12.728M16.463 8.288a5.25 5.25 0 0 1 0 7.424M6.75 8.25
              l4.72-4.72a.75.75 0 0 1 1.28.53v15.88a.75.75 0 0 1-1.28.53l-4.72-4.72H4.51
              c-.88 0-1.704-.507-1.938-1.354A9.009 9.009 0 0 1 2.25 12c0-.83.112-1.633.322-2.396
              C2.806 8.756 3.63 8.25 4.51 8.25H6.75Z"/>
          </svg>

          <svg class="popup-sound-off" xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor"
            width="26" height="26">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M17.25 9.75 19.5 12m0 0 2.25 2.25M19.5 12l2.25-2.25M19.5 12l2.25 2.25m-10.5-6
              4.72-4.72a.75.75 0 0 1 1.28.53v15.88a.75.75 0 0 1-1.28.53l-4.72-4.72H4.51
              c-.88 0-1.704-.507-1.938-1.354A9.009 9.009 0 0 1 2.25 12c0-.83.112-1.633.322-2.396
              C2.806 8.756 3.63 8.25 4.51 8.25H6.75Z"/>
          </svg>
        </button>
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

        popup.addEventListener("mouseenter", () => { if (hideTimer) clearTimeout(hideTimer); });
        popup.addEventListener("mouseleave", hidePopup);

        const videoBox = popup.querySelector(".popup-video-box");
        if (videoBox) {
            videoBox.addEventListener("click", (e) => {
                if (e.target.closest(".popup-mute-btn")) return;
                e.stopPropagation();
                openVideoPlayerFromCard(activeCard, { playWithSound: true });
            });
        }

        const muteBtn = popup.querySelector(".popup-mute-btn");

        // (선택) SVG를 눌러도 타겟이 버튼으로 잡히게 해서 안정화
        muteBtn?.querySelectorAll("svg, path").forEach(el => el.style.pointerEvents = "none");

        // 포인터 다운 단계에서부터 상위 클릭 전파 차단 (드래그/터치 포함)
        const stopOnly = (e) => { e.preventDefault(); e.stopPropagation(); };
        ["pointerdown","mousedown","touchstart"].forEach(evt => {
            muteBtn?.addEventListener(evt, stopOnly, { capture:true, passive:false });
        });

        // 실제 토글은 click에서만
        muteBtn?.addEventListener("click", (e) => {
            e.preventDefault();
            e.stopPropagation();

            const v = popup.querySelector("video");
            const soundOn  = popup.querySelector(".popup-sound-on");
            const soundOff = popup.querySelector(".popup-sound-off");
            if (!v) return;

            v.muted = !v.muted;
            if (!v.muted && v.volume === 0) v.volume = 1.0;

            if (soundOn && soundOff) {
                soundOn.style.display  = v.muted ? "none" : "block";
                soundOff.style.display = v.muted ? "block" : "none";
            }
        }, { capture:true });


        popup.querySelector(".popup-play-btn-circle").onclick = (e) => {
            e.stopPropagation();
            openVideoPlayerFromCard(activeCard, { playWithSound: true });
        };

        const likeBtn = popup.querySelector(".popup-like-btn");
        likeBtn.onclick = (e) => {
            e.stopPropagation();
            likeBtn.classList.toggle("liked");
            if (likeBtn.classList.contains("liked")) {
                likeBtn.style.transform = "scale(1.25)";
                setTimeout(() => { likeBtn.style.transform = "scale(1)"; }, 250);
            }
        };

        popup.addEventListener("click", (e) => {
            if (e.target.closest?.(".popup-mute-btn")) return;
            if (e.target.closest?.(".popup-action-row")) return; // 재생/좋아요 버튼 영역도 제외
            openVideoPlayerFromCard(activeCard);
        });

    }
    createPopup();

    function showPopup(card){
        if (!popup) return;
        if (hideTimer) clearTimeout(hideTimer);
        activeCard = card;

        updateArrows();

        const rect    = card.getBoundingClientRect();
        const videoEl = popup.querySelector("video");
        const thumbEl = popup.querySelector(".popup-thumb");
        const soundOn = popup.querySelector(".popup-sound-on");
        const soundOff = popup.querySelector(".popup-sound-off");

        popup.querySelector(".popup-title").textContent = card.dataset.title ?? "";
        popup.querySelector(".popup-meta").innerHTML = `
      ${card.dataset.genre ?? ""} · ${card.dataset.runtime ?? ""}
      <span class="popup-age">${card.dataset.age ?? ""}</span>
    `;

        const thumb = card.dataset.thumb;
        if (thumb) {
            thumbEl.src = thumb;
            thumbEl.style.display = "block";
            thumbEl.style.opacity = "1";
        } else {
            thumbEl.style.display = "none";
        }

        videoEl.pause();
        videoEl.src = card.dataset.video || "";
        videoEl.currentTime = 0;
        videoEl.muted = true;
        if (soundOn && soundOff) {
            soundOn.style.display = "none";
            soundOff.style.display = "block";
        }

        const onTimeUpdate = () => {
            if (videoEl.currentTime >= 0.15) {
                thumbEl.style.opacity = "0";
                videoEl.removeEventListener("timeupdate", onTimeUpdate);
            }
        };
        videoEl.addEventListener("timeupdate", onTimeUpdate);
        videoEl.play().catch(() => {});

        const EXTRA_INFO_HEIGHT = 60;
        popup.style.width  = rect.width + "px";
        popup.style.height = (rect.height + EXTRA_INFO_HEIGHT) + "px";
        popup.style.left   = rect.left + "px";
        popup.style.top    = (rect.top - EXTRA_INFO_HEIGHT * 0.4) + "px";

        popup.style.opacity   = "0";
        popup.style.transform = "scale(0.9)";
        popup.classList.add("show");

        requestAnimationFrame(() => {
            popup.style.opacity   = "1";
            popup.style.transform = "scale(1.3) translateY(-6px)";
        });
    }

    function hidePopup(){
        hideTimer = setTimeout(() => {
            const videoEl = popup.querySelector("video");
            const thumbEl = popup.querySelector(".popup-thumb");

            popup.style.opacity = "0";
            popup.style.transform = "scale(0.85) translateY(10px)";

            videoEl.pause();
            if (thumbEl) thumbEl.style.opacity = "1";

            setTimeout(() => popup.classList.remove("show"), 180);
            activeCard = null;
        }, 120);
    }

    slider?.addEventListener("mouseover", (e) => {
        const card = e.target.closest(".film-card");
        if (!card || !slider.contains(card)) return;
        if (activeCard === card) return;
        showPopup(card);
    });

    slider?.addEventListener("mouseout", (e) => {
        const fromCard = e.target.closest(".film-card");
        if (!fromCard) return;
        const toEl = e.relatedTarget;
        if (toEl && fromCard.contains(toEl)) return;
        hidePopup();
    });

    function hidePopupImmediate() {
        if (!popup) return;
        popup.style.opacity = "0";
        popup.style.transform = "scale(0.85) translateY(10px)";
        popup.querySelector("video").pause();
        popup.classList.remove("show");
        activeCard = null;
        if (hideTimer) clearTimeout(hideTimer);
    }

    window.addEventListener("wheel", () => { if (activeCard) hidePopupImmediate(); });
    window.addEventListener("scroll", () => { if (activeCard) hidePopupImmediate(); });
    window.addEventListener("touchmove", () => { if (activeCard) hidePopupImmediate(); });

    /* ---------------------------
       6) Gallery + Lightbox
    --------------------------- */
    const gallerySection = $("#gallerySection");
    const galleryGrid    = $("#galleryGrid");
    const gallerySpinner = $("#gallerySpinner");

    const INITIAL_COUNT = 6;
    const BATCH_SIZE    = 6;

    let visibleCount = 0;
    let isLoading    = false;

    let lightboxOverlay = null;
    let lightboxImg     = null;
    let currentIndex    = 0;

    function getGalleryItems() {
        return galleryGrid ? Array.from(galleryGrid.querySelectorAll(".gallery-item-wrap")) : [];
    }

    function createGalleryItem(src, isPrivate) {
        const wrap = document.createElement("div");
        wrap.className = "gallery-item-wrap";

        const img = document.createElement("img");
        img.className = "gallery-item";
        img.src = src || "/videos/gallery-placeholder.png";
        img.alt = "gallery";
        img.loading = "lazy";
        img.onerror = () => { img.onerror = null; img.src = "/videos/gallery-placeholder.png"; };

        wrap.appendChild(img);
        if (isPrivate) {
            const lock = document.createElement("div");
            lock.className = "gallery-item-lock";
            lock.textContent = "🔒";
            wrap.appendChild(lock);
        }
        return wrap;
    }

    async function renderGallery(publicId) {
        if (!galleryGrid) return;

        // ✅ empty CTA div 2개는 유지하고, 실제 이미지들만 제거
        getGalleryItems().forEach(el => el.remove());

        const list = await fetchGalleryByPublicId(publicId);
        const photos = Array.isArray(list) ? list : [];

        // 서버가 [{url}] 또는 ["url"] 둘 다 대응
        const items = photos
            .map(x => {
                if (typeof x === "string") return { url: x, isPrivate: false };
                return {
                    url: x?.url || x?.imageUrl || x?.photoUrl || "",
                    isPrivate: !!x?.isPrivate
                };
            })
            .map(x => ({ url: String(x.url || "").trim(), isPrivate: x.isPrivate }))
            .filter(x => !!x.url);

        items.forEach(i => galleryGrid.appendChild(createGalleryItem(i.url, i.isPrivate)));

        initGalleryIfAny();
        updateEmptyUI();
    }

    function initGalleryIfAny() {
        const items = getGalleryItems();
        if (items.length === 0) return;

        items.forEach((img, idx) => {
            img.style.display = (idx < INITIAL_COUNT) ? "block" : "none";
        });

        visibleCount = Math.min(INITIAL_COUNT, items.length);
        if (items.length <= INITIAL_COUNT && gallerySpinner) gallerySpinner.style.display = "none";
    }

    function showMoreImages() {
        const items = getGalleryItems();
        const remaining = items.length - visibleCount;
        if (remaining <= 0) return;

        const toShow = Math.min(BATCH_SIZE, remaining);
        for (let i = 0; i < toShow; i++) {
            const img = items[visibleCount + i];
            if (img) img.style.display = "block";
        }
        visibleCount += toShow;
    }

    function handleScroll() {
        const items = getGalleryItems();
        if (isLoading) return;
        if (visibleCount >= items.length) return;
        if (!gallerySection) return;

        const rect = gallerySection.getBoundingClientRect();
        const viewHeight = window.innerHeight || document.documentElement.clientHeight;

        if (rect.bottom - 120 <= viewHeight) {
            isLoading = true;
            if (gallerySpinner) gallerySpinner.style.display = "flex";

            const delay = 500;
            setTimeout(() => {
                showMoreImages();
                isLoading = false;
                if (gallerySpinner) gallerySpinner.style.display = "none";
            }, delay);
        }
    }

    let lightboxPrevArrow = null;
    let lightboxNextArrow = null;

    function createLightbox() {
        lightboxOverlay = document.createElement("div");
        lightboxOverlay.className = "gallery-lightbox-overlay";
        lightboxOverlay.innerHTML = `
      <button class="gallery-lightbox-arrow left">‹</button>
      <img class="gallery-lightbox-image" src="" alt="photo detail">
      <button class="gallery-lightbox-arrow right">›</button>
    `;
        document.body.appendChild(lightboxOverlay);

        lightboxImg       = lightboxOverlay.querySelector(".gallery-lightbox-image");
        lightboxPrevArrow = lightboxOverlay.querySelector(".gallery-lightbox-arrow.left");
        lightboxNextArrow = lightboxOverlay.querySelector(".gallery-lightbox-arrow.right");

        lightboxOverlay.addEventListener("click", (e) => {
            if (e.target === lightboxImg || e.target === lightboxPrevArrow || e.target === lightboxNextArrow) return;
            closeLightbox();
        });

        lightboxImg.addEventListener("click", closeLightbox);

        lightboxPrevArrow.addEventListener("click", (e) => { e.stopPropagation(); showPrevPhoto(); });
        lightboxNextArrow.addEventListener("click", (e) => { e.stopPropagation(); showNextPhoto(); });

        window.addEventListener("keydown", (e) => {
            if (!lightboxOverlay.classList.contains("show")) return;
            if (e.key === "Escape") closeLightbox();
            else if (e.key === "ArrowLeft") showPrevPhoto();
            else if (e.key === "ArrowRight") showNextPhoto();
        });
    }

    function updateLightboxArrows() {
        const items = getGalleryItems();
        const total = items.length;
        if (!lightboxPrevArrow || !lightboxNextArrow) return;
        lightboxPrevArrow.style.display = (currentIndex <= 0) ? "none" : "flex";
        lightboxNextArrow.style.display = (currentIndex >= total - 1) ? "none" : "flex";
    }

    function openLightbox(index) {
        const items = getGalleryItems();
        if (items.length === 0) return;

        if (!lightboxOverlay) createLightbox();
        currentIndex = index;

        const img = items[currentIndex].querySelector(".gallery-item");
        if (img) lightboxImg.src = img.src;
        lightboxOverlay.classList.add("show");
        updateLightboxArrows();
    }

    function closeLightbox() {
        if (!lightboxOverlay) return;
        lightboxOverlay.classList.remove("show");
    }

    function showPrevPhoto() {
        const items = getGalleryItems();
        if (currentIndex <= 0) return;
        currentIndex--;
        const img = items[currentIndex].querySelector(".gallery-item");
        if (img) lightboxImg.src = img.src;
        updateLightboxArrows();
    }

    function showNextPhoto() {
        const items = getGalleryItems();
        if (currentIndex >= items.length - 1) return;
        currentIndex++;
        const img = items[currentIndex].querySelector(".gallery-item");
        if (img) lightboxImg.src = img.src;
        updateLightboxArrows();
    }

    galleryGrid?.addEventListener("click", (e) => {
        const wrap = e.target.closest(".gallery-item-wrap");
        if (!wrap) return;
        const img = wrap.querySelector(".gallery-item");
        if (!img) return;
        const items = getGalleryItems();
        const idx = items.indexOf(wrap);
        if (idx >= 0) openLightbox(idx);
    });

    /* ---------------------------
       7) Edit overlay (⚙️)
    --------------------------- */
    function setupEditOverlay(){
        const settingsBtn = $("#profileSettingsBtn");
        const editOverlay = $("#profileEditOverlay");
        const editCard    = editOverlay?.querySelector(".profile-edit-card");

        if (!settingsBtn || !editOverlay || !editCard) return;

        function closeEditOverlay(){
            editOverlay.classList.remove("show");
            settingsBtn.setAttribute("aria-expanded", "false");
            lockBodyScroll(false);
        }

        settingsBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            if (!window.__isOwner) return;

            editOverlay.classList.add("show");
            settingsBtn.setAttribute("aria-expanded", "true");
            lockBodyScroll(true);
        });

        editOverlay.addEventListener("click", (e) => {
            if (!editCard.contains(e.target)) { closeEditOverlay(); return; }

            const item = e.target.closest(".profile-settings-item");
            if (!item) return;

            const action = item.dataset.action;
            if (action === "profile") window.location.href = "/edit-profile.html";
            else if (action === "filmography") window.location.href = "/edit-filmography.html";
            else if (action === "gallery") window.location.href = "/edit-gallery.html";

            closeEditOverlay();
        });

        document.addEventListener("keydown", (e) => {
            if (e.key === "Escape" && editOverlay.classList.contains("show")) closeEditOverlay();
        });
    }

    /* ---------------------------
       8) Empty UI (핵심)
    --------------------------- */
    function updateEmptyUI(){
        const profileHeader = $(".profile-header");
        const nameEl = $("#profileName");
        const metaEl = $("#profileMeta");
        const bioEl  = $("#bioDisplay");

        const snsRow = $("#snsRow");
        const tagList = $("#tagList");

        const hasName = !!(nameEl?.dataset.value?.trim());
        const hasMeta = !!(metaEl?.dataset.birthDate?.trim() || metaEl?.dataset.birthPlace?.trim());
        const hasBio  = !!(bioEl?.dataset.value?.trim());
        const hasSns  = snsRow && snsRow.querySelectorAll("a.sns-pill").length > 0;
        const hasTags = tagList && tagList.querySelectorAll(".profile-tag").length > 0;

        const profileHasAny = hasName || hasMeta || hasBio || hasSns || hasTags;

        snsRow?.classList.toggle("is-empty", !hasSns);
        tagList?.classList.toggle("is-empty", !hasTags);
        profileHeader?.classList.toggle("is-empty", !profileHasAny);

        const isOwner = !!window.__isOwner;

        // FILMOGRAPHY
        const filmCards  = slider ? slider.querySelectorAll(".film-card") : [];
        const filmEmpty  = $("#filmEmpty");
        const filmEmptyViewOnly = $("#filmEmptyViewOnly");
        const hasFilms = filmCards.length > 0;

        filmEmpty?.classList.toggle("hidden", hasFilms || !isOwner);
        filmEmptyViewOnly?.classList.toggle("hidden", hasFilms || isOwner);

        $("#filmSliderContainer .film-slider-wrapper")?.classList.toggle("hidden", !hasFilms);
        prevBtn?.classList.toggle("hidden", !hasFilms);
        nextBtn?.classList.toggle("hidden", !hasFilms);

        // GALLERY
        const galleryImgs = getGalleryItems();
        const hasGallery = galleryImgs.length > 0;

        $("#galleryEmpty")?.classList.toggle("hidden", hasGallery || !isOwner);
        $("#galleryEmptyViewOnly")?.classList.toggle("hidden", hasGallery || isOwner);
    }

    (function autoUpdateEmptyUI(){
        const filmSliderEl = $("#filmSlider");
        const galleryGridEl  = $("#galleryGrid");
        const profileInfo  = $(".profile-info");

        let scheduled = false;
        const schedule = () => {
            if (scheduled) return;
            scheduled = true;
            requestAnimationFrame(() => {
                scheduled = false;
                updateEmptyUI();
                updateArrows();
            });
        };

        const observer = new MutationObserver(schedule);
        const config = { childList:true, subtree:true, attributes:true, characterData:true };

        filmSliderEl  && observer.observe(filmSliderEl, config);
        galleryGridEl && observer.observe(galleryGridEl, config);
        profileInfo   && observer.observe(profileInfo, config);

        schedule();
    })();

    /* ---------------------------
       9) Login overlay + Header menu
    --------------------------- */
    function openLoginOverlay(){ $("#loginOverlay")?.classList.add("show"); }
    function closeLoginOverlay(){ $("#loginOverlay")?.classList.remove("show"); }

    document.addEventListener("click", (e) => {
        const loginBtn = e.target?.closest?.("#loginBtn");
        if (loginBtn) { openLoginOverlay(); return; }

        const overlay = $("#loginOverlay");
        if (overlay && overlay.classList.contains("show") && e.target === overlay) closeLoginOverlay();
    });
    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape") closeLoginOverlay();
    });

    document.addEventListener("click", async (e) => {
        const submitBtn = e.target?.closest?.("#loginSubmitBtn");
        if (!submitBtn) return;

        const email = $("#loginEmail")?.value?.trim() || "";
        const password = $("#loginPassword")?.value || "";
        if (!email || !password) { alert("아이디와 비밀번호를 입력해 주세요."); return; }

        submitBtn.disabled = true;
        try {
            await window.OnfilmAuth.login(email, password);
            closeLoginOverlay();
            await renderHeaderActions();
            await initProfilePage();
        } catch (err) {
            console.error(err);
            alert(err?.message || "로그인 실패");
        } finally {
            submitBtn.disabled = false;
        }
    });

    function renderLoggedOut(container){
        container.innerHTML = `<button type="button" class="login-btn" id="loginBtn">로그인</button>`;
    }

    function renderLoggedIn(container, me){
        const { username, displayName, avatarUrl } = window.OnfilmCommon.normalizeMe(me);
        const initial = window.OnfilmCommon.getInitial(username, displayName);

        container.innerHTML = window.OnfilmCommon.buildProfileMenuHtml(displayName, avatarUrl, initial);

        window.OnfilmCommon.attachProfileMenuHandlers({
            me,
            onLogout: async () => {
                await window.OnfilmAuth.logout();
                renderLoggedOut(container);
                applyOwnerUI(false);
                if (currentPublicId) {
                    await renderFilmography(currentPublicId);
                    await renderGallery(currentPublicId);
                }
                updateEmptyUI();
            }
        });
    }

    async function renderHeaderActions(){
        const container = $("#headerActions");
        if (!container) return;

        renderLoggedOut(container);

        try {
            const result = await window.OnfilmAuth.restoreSession();
            if (result && result.ok && result.me) renderLoggedIn(container, result.me);
            else renderLoggedOut(container);
        } catch (e) {
            console.error("restoreSession failed", e);
            renderLoggedOut(container);
        }
    }

    /* ---------------------------
       10) Page init (수정 핵심)
    --------------------------- */
    async function initProfilePage() {
        const username = getUsernameFromPath();
        if (!username) return;

        try {
            const onfilm = await fetchPublicIdByUsername(username);
            const publicId = onfilm?.publicId;
            if (!publicId) {
                window.location.href = `/profile-not-found.html?u=${encodeURIComponent(username)}`;
                return;
            }
            currentPublicId = publicId;

            // 1) person 먼저
            const person = await fetchPersonByPublicId(publicId);
            applyPersonToUI(person);

            // 2) owner 판정 -> UI 적용 (✅ 여기서 먼저 해야 empty CTA가 제대로 뜸)
            const me = await restoreMeSafe();
            const isOwner = computeIsOwner(me, person, username);
            applyOwnerUI(isOwner);

            // 3) 필모/갤러리 렌더 (owner 상태가 이미 반영된 뒤)
            await renderFilmography(publicId);
            await renderGallery(publicId);

            // 4) empty 재갱신
            updateEmptyUI();

            // 5) 갤러리 lazy scroll
            const items = getGalleryItems();
            if (items.length > INITIAL_COUNT) {
                window.addEventListener("scroll", handleScroll);
                handleScroll();
            }
        } catch (e) {
            console.error(e);
            window.location.href = `/profile-not-found.html?u=${encodeURIComponent(username)}`;
        }
    }

    /* ---------------------------
       11) Bootstrap
    --------------------------- */
    document.addEventListener("DOMContentLoaded", async () => {
        await renderHeaderActions();
        setupEditOverlay();

        const params = new URLSearchParams(window.location.search);
        if (params.get("login") === "true") openLoginOverlay();

        await initProfilePage();
        updateArrows();
    });
