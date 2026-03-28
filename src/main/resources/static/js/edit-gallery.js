
  /* =========================================================
     0) AUTH READY
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
     1) username -> publicId helper (요청 반영)
        - /api/person/{username} -> { publicId: "..." }
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

  const fetchPublicIdByUsername = window.OnfilmCommon.fetchPublicIdByUsername;

  async function fetchGalleryByPublicId(publicId) {
    const res = await window.OnfilmAuth.apiFetchWithAutoRefresh(
            `/api/people/${encodeURIComponent(publicId)}/gallery`,
            { method:"GET", headers:{ "Accept":"application/json" } }
    );
    if (!res.ok) return [];
    const data = await res.json().catch(() => []);
    return Array.isArray(data) ? data : [];
  }

  async function fetchProfileByPublicId(publicId) {
    const res = await window.OnfilmAuth.apiFetchWithAutoRefresh(
            `/api/people/${encodeURIComponent(publicId)}`,
            { method:"GET", headers:{ "Accept":"application/json" } }
    );
    if (!res.ok) return null;
    return await res.json().catch(() => null);
  }

  async function updateGalleryItemPrivacy(publicId, key, isPrivate) {
    const res = await window.OnfilmAuth.apiFetchWithAutoRefresh(
            `/api/people/${encodeURIComponent(publicId)}/gallery/item/privacy`,
            {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ key, isPrivate })
            }
    );
    if (!res.ok) {
      const msg = await res.text().catch(() => "");
      throw new Error(`비공개 설정 실패: ${res.status} ${msg}`);
    }
  }

  function toStorageKey(value) {
    if (!value) return null;
    let s = String(value).trim();
    if (!s) return null;

    if (s.startsWith("http://") || s.startsWith("https://")) {
      try {
        s = new URL(s).pathname;
      } catch {
        return null;
      }
    }

    if (s.startsWith("/files/")) s = s.substring("/files/".length);
    if (s.startsWith("/")) s = s.substring(1);
    return s;
  }


  // =========================
  // 상태: 메모리 상의 사진 리스트
  // =========================
  /**
   * photos = [
   *   { id: 'local-1', file: File, previewUrl: 'blob:...', s3Key: null | 'xxx' }
   * ]
   */
  let photos = [];
  let idCounter = 0;
  let currentPublicId = null;

  const uploadArea     = document.getElementById("uploadArea");
  const fileInput      = document.getElementById("fileInput");
  const browseBtn      = document.getElementById("browseBtn");
  const clearAllBtn    = document.getElementById("clearAllBtn");
  const galleryGrid    = document.getElementById("galleryGrid");
  const emptyHint      = document.getElementById("emptyHint");
  const photoCountText = document.getElementById("photoCountText");
  const saveBtn        = document.getElementById("saveBtn");
  const privacyBtn     = document.getElementById("galleryPrivacyBtn");

  // =========================
  // 업로드 처리
  // =========================
  function handleFiles(files) {
    const arr = Array.from(files);
    arr.forEach(file => {
      if (!file.type.startsWith("image/")) return;

      const id = `local-${++idCounter}`;
      const previewUrl = URL.createObjectURL(file);

      photos.push({ id, file, previewUrl, s3Key: null, isPrivate: false });
    });

    renderGallery();
  }

  browseBtn.addEventListener("click", () => fileInput.click());

  fileInput.addEventListener("change", (e) => {
    if (!e.target.files || e.target.files.length === 0) return;
    handleFiles(e.target.files);
    fileInput.value = "";
  });

  // 업로드 영역 드래그앤드롭
  ["dragenter","dragover"].forEach(eventName => {
    uploadArea.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      uploadArea.classList.add("dragover");
    });
  });
  ["dragleave","drop"].forEach(eventName => {
    uploadArea.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      uploadArea.classList.remove("dragover");
    });
  });
  uploadArea.addEventListener("drop", (e) => {
    const dt = e.dataTransfer;
    if (!dt) return;
    const files = dt.files;
    if (files && files.length > 0) handleFiles(files);
  });

  clearAllBtn.addEventListener("click", async () => {
    if (!photos.length) return;
    if (!confirm("업로드된 모든 사진을 제거할까요?")) return;

    if (currentPublicId) {
      for (const p of photos) {
        if (p.s3Key) {
          await deleteGalleryImage(currentPublicId, p.s3Key).catch(() => null);
        }
      }
      await reorderGallery(currentPublicId).catch(() => null);
    }

    photos.forEach(p => p.previewUrl && URL.revokeObjectURL(p.previewUrl));
    photos = [];
    renderGallery();
  });

  // =========================
  // 갤러리 렌더링
  // =========================
  function renderGallery() {
    galleryGrid.innerHTML = "";

    emptyHint.style.display = (photos.length === 0) ? "block" : "none";
    photoCountText.textContent = `사진 ${photos.length}장`;

    photos.forEach((photo, index) => {
      const card = document.createElement("div");
      card.className = "photo-card";
      card.draggable = true;
      card.dataset.id = photo.id;

      const img = document.createElement("img");
      img.className = "photo-thumb";
      img.src = photo.previewUrl;
      img.alt = `사진 ${index + 1}`;

      // ✅ 이미지 자체 기본 drag 동작(새 탭 열림/드래그 고스트 이미지 이상) 방지
      img.addEventListener("dragstart", (e) => {
        // card가 drag 이벤트를 담당하므로 이미지 기본 동작은 막음
        e.preventDefault();
      });

      const bottomBar = document.createElement("div");
      bottomBar.className = "photo-bottom-bar";

      const handle = document.createElement("div");
      handle.className = "photo-handle";
      handle.innerHTML = `<span class="dots-icon">⋮⋮</span><span>드래그</span>`;

      const privacyBtn = document.createElement("button");
      privacyBtn.type = "button";
      privacyBtn.className = "photo-privacy-btn" + (photo.isPrivate ? " is-on" : "");
      privacyBtn.innerHTML = photo.isPrivate ? "🔒 비공개" : "🔓 공개";
      privacyBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        if (!currentPublicId || !photo.s3Key) return;
        const next = !photo.isPrivate;
        try {
          await updateGalleryItemPrivacy(currentPublicId, photo.s3Key, next);
          photo.isPrivate = next;
          privacyBtn.classList.toggle("is-on", next);
          privacyBtn.innerHTML = next ? "🔒 비공개" : "🔓 공개";
        } catch (err) {
          alert(err?.message || "비공개 설정 실패");
        }
      });

      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "photo-delete-btn";
      delBtn.textContent = "✕";
      delBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        deletePhoto(photo.id);
      });

      bottomBar.appendChild(handle);
      bottomBar.appendChild(privacyBtn);
      bottomBar.appendChild(delBtn);

      card.appendChild(img);
      card.appendChild(bottomBar);

      addDragAndDropHandlers(card);

      galleryGrid.appendChild(card);
    });
  }

  async function deletePhoto(id) {
    const idx = photos.findIndex(p => p.id === id);
    if (idx === -1) return;
    const [removed] = photos.splice(idx, 1);
    if (removed?.s3Key && currentPublicId) {
      await deleteGalleryImage(currentPublicId, removed.s3Key).catch(() => null);
    }
    if (removed?.previewUrl) URL.revokeObjectURL(removed.previewUrl);
    renderGallery();
  }

  // =========================
  // 드래그 앤 드롭 정렬 (좌/우 이동 개선)
  // =========================
  let draggedCard = null;

  function getDragAfterElement(container, x, y, excludeEl) {
    const cards = [...container.querySelectorAll(".photo-card:not(.dragging)")];

    let closest = { offset: Number.NEGATIVE_INFINITY, element: null };

    for (const child of cards) {
      if (child === excludeEl) continue;

      const rect = child.getBoundingClientRect();

      // 카드 중심점과 현재 포인터 사이 거리 기반(그리드 좌우 이동에 강함)
      const cx = rect.left + rect.width / 2;
      const cy = rect.top  + rect.height / 2;

      // 가중치: x를 더 강하게 봄(좌우 드래그 개선)
      const dx = Math.abs(x - cx);
      const dy = Math.abs(y - cy);
      const dist = dx * 1.2 + dy * 0.8;

      // 가장 가까운 카드가 target
      const score = -dist;
      if (score > closest.offset) {
        closest = { offset: score, element: child };
      }
    }
    return closest.element;
  }

  function shouldInsertAfter(targetEl, x) {
    const rect = targetEl.getBoundingClientRect();
    return x > (rect.left + rect.width / 2);
  }

  function addDragAndDropHandlers(card){
    card.addEventListener("dragstart", (e) => {
      draggedCard = card;
      card.classList.add("dragging");
      e.dataTransfer.effectAllowed = "move";
      e.dataTransfer.setData("text/plain", card.dataset.id); // firefox
    });

    card.addEventListener("dragend", () => {
      if (!draggedCard) return;
      draggedCard.classList.remove("dragging");
      draggedCard = null;
      syncPhotosOrderWithDOM();
    });

    // ✅ 핵심: 기존 y 기준/반쪽 기준을 버리고, "가장 가까운 카드" 기준으로 삽입
    card.addEventListener("dragover", (e) => {
      e.preventDefault();
      if (!draggedCard) return;

      const target = getDragAfterElement(galleryGrid, e.clientX, e.clientY, draggedCard);
      if (!target) return;

      if (target === draggedCard) return;

      const after = shouldInsertAfter(target, e.clientX);
      if (after) {
        if (target.nextSibling !== draggedCard) {
          galleryGrid.insertBefore(draggedCard, target.nextSibling);
        }
      } else {
        if (target !== draggedCard) {
          galleryGrid.insertBefore(draggedCard, target);
        }
      }
    });

    card.addEventListener("drop", (e) => {
      e.preventDefault();
    });
  }

  function syncPhotosOrderWithDOM() {
    const domCards = Array.from(galleryGrid.children);
    const newOrder = domCards
            .map(card => photos.find(p => p.id === card.dataset.id))
            .filter(Boolean);

    if (newOrder.length === photos.length) photos = newOrder;
  }

  // =========================
  // 저장 버튼 (현재는 payload 콘솔 출력)
  // =========================
  saveBtn.addEventListener("click", async () => {
    if (!currentPublicId) {
      alert("publicId를 찾을 수 없습니다. 다시 로그인해 주세요.");
      return;
    }

    await uploadPendingPhotos();
    await reorderGallery(currentPublicId);

    const uname = await getUsernameSafe();
    if (uname) {
      location.href = "/" + encodeURIComponent(uname);
      return;
    }

    alert("갤러리 저장이 완료되었습니다.");
  });

  // =========================
  // INIT
  // =========================
  document.addEventListener("DOMContentLoaded", async () => {
    const result = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!result?.ok) {
      const next = encodeURIComponent(location.pathname + location.search);
      location.href = `/login.html?next=${next}`;
      return;
    }

    const uname = await getUsernameSafe();
    currentPublicId = uname ? await fetchPublicIdByUsername(uname) : null;
    if (currentPublicId) {
      const profile = await fetchProfileByPublicId(currentPublicId);
      const isPrivate = !!profile?.galleryPrivate;
      if (privacyBtn) {
        privacyBtn.classList.toggle("is-on", isPrivate);
        privacyBtn.textContent = isPrivate ? "비공개" : "공개";
        privacyBtn.addEventListener("click", async () => {
          const next = !privacyBtn.classList.contains("is-on");
          const res = await window.OnfilmAuth.apiFetchWithAutoRefresh(
                  `/api/people/${encodeURIComponent(currentPublicId)}/gallery/privacy`,
                  {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ isPrivate: next })
                  }
          );
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            alert(`비공개 설정 실패: ${res.status} ${msg}`);
            return;
          }
          privacyBtn.classList.toggle("is-on", next);
          privacyBtn.textContent = next ? "비공개" : "공개";
        });
      }

      const urls = await fetchGalleryByPublicId(currentPublicId);
      photos = urls.map((item) => {
        const url = (typeof item === "string") ? item : (item?.url || "");
        const key = (typeof item === "string") ? toStorageKey(item) : (item?.key || toStorageKey(item?.url || ""));
        return {
          id: `remote-${++idCounter}`,
          file: null,
          previewUrl: url,
          s3Key: key,
          isPrivate: !!(item?.isPrivate),
        };
      });
    }

    renderGallery();

    // ✅ 전체 드롭 기본 동작 방지(페이지 이동/새 탭 열림 방지)
    window.addEventListener("dragover", (e) => e.preventDefault());
    window.addEventListener("drop", (e) => e.preventDefault());
  });


  async function uploadPendingPhotos() {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    for (const p of photos) {
      if (!p.file || p.s3Key) continue;

      const fd = new FormData();
      fd.append("file", p.file);

      const res = await fetcher("/api/files/person/me/gallery", {
        method: "POST",
        body: fd,
      });

      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        throw new Error(`사진 업로드 실패: ${res.status} ${msg}`);
      }

      const data = await res.json().catch(() => null);
      const key = data?.key ? String(data.key) : null;
      const url = data?.url ? String(data.url) : null;
      if (key) p.s3Key = key;
      if (url) {
        if (p.previewUrl?.startsWith("blob:")) URL.revokeObjectURL(p.previewUrl);
        p.previewUrl = url;
      }
      p.file = null;
    }

    renderGallery();
  }

  async function reorderGallery(publicId) {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const keys = photos.map(p => p.s3Key).filter(Boolean);
    const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}/gallery`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ keys }),
    });

    if (!res.ok) {
      const msg = await res.text().catch(() => "");
      throw new Error(`순서 저장 실패: ${res.status} ${msg}`);
    }
  }

  async function deleteGalleryImage(publicId, key) {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}/gallery?key=${encodeURIComponent(key)}`, {
      method: "DELETE",
    });

    if (!res.ok) {
      const msg = await res.text().catch(() => "");
      throw new Error(`삭제 실패: ${res.status} ${msg}`);
    }
  }


  /* =========================================================
     4) "프로필로 돌아가기" → 무조건 /{username}
        (요청대로 index.html로 보내지 않음)
  ========================================================= */
  document.addEventListener("DOMContentLoaded", () => {
    const link = document.getElementById("backToProfileLink");
    if (!link) return;

    link.addEventListener("click", async (e) => {
      e.preventDefault();

      const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
      if (!authReady?.ok) {
        const next = encodeURIComponent(location.pathname + location.search);
        location.href = `/login.html?next=${next}`;
        return;
      }

      const uname = await getUsernameSafe();
      if (!uname) {
        // username이 없으면 프로필 경로를 만들 수 없으니 로그인/랜딩 대신
        // 여기서는 안전하게 로그인으로 보냄(세션 ok인데 uname이 없으면 세팅이 덜된 상태)
        alert("username이 설정되어 있지 않습니다. 프로필에서 username을 설정해 주세요.");
        return;
      }

      location.href = "/" + encodeURIComponent(uname);
    });
  });
