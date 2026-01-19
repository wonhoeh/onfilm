
  /* =========================================================
   * Filmography Edit - "한 파일처럼" 모듈화 정리본
   * - ✅ username -> publicId (/api/person/{username})
   * - ✅ 프로필 확인 GET: /api/people/{publicId}
   * - ✅ 영화 생성 POST: /api/movie   (기존 /movies -> 수정)
   * ========================================================= */

  /* ============================
   * 1) AUTH READY PROMISE + GUARD
   * ============================ */
  window.__ONFILM_AUTH_READY_PROMISE__ = (async () => {
    try {
      if (!window.OnfilmAuth?.restoreSession) return { ok:false, me:null };
      return await window.OnfilmAuth.restoreSession();
    } catch {
      return { ok:false, me:null };
    }
  })();

  document.addEventListener("DOMContentLoaded", async () => {
    const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!authReady?.ok) {
      const next = encodeURIComponent(location.pathname + location.search);
      location.href = `/login.html?next=${next}`;
    }
  });

  /* ============================
   * 2) COMMON UTIL
   * ============================ */
  const Util = (() => {
    function escapeHtml(str){
      return String(str)
              .replaceAll("&","&amp;")
              .replaceAll("<","&lt;")
              .replaceAll(">","&gt;")
              .replaceAll('"',"&quot;")
              .replaceAll("'","&#39;");
    }
    function normalizeGenre(text){
      return String(text || "").trim().replace(/\s+/g, " ");
    }
    function bindDropzone(dropzone, onFiles) {
      ["dragenter","dragover"].forEach(evtName => {
        dropzone.addEventListener(evtName, (e) => {
          e.preventDefault();
          e.stopPropagation();
          dropzone.classList.add("is-dragover");
        });
      });
      ["dragleave","dragend"].forEach(evtName => {
        dropzone.addEventListener(evtName, (e) => {
          e.preventDefault();
          e.stopPropagation();
          dropzone.classList.remove("is-dragover");
        });
      });
      dropzone.addEventListener("drop", (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropzone.classList.remove("is-dragover");
        const dt = e.dataTransfer;
        if (dt && dt.files && dt.files.length) onFiles(dt.files);
      });
    }
    return { escapeHtml, normalizeGenre, bindDropzone };
  })();

  window.__DELETED_MOVIE_IDS__ = window.__DELETED_MOVIE_IDS__ || [];

  /* ============================
   * 3) username -> publicId
   * ============================ */
  async function resolvePublicIdByUsername(username) {
    const uname = String(username || "").trim();
    if (!uname) return null;

    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const res = await fetcher(`/api/person/${encodeURIComponent(uname)}`, {
      method: "GET",
      headers: { "Accept": "application/json" }
    });

    if (!res.ok) return null;

    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) {
      const data = await res.json().catch(() => null);
      if (typeof data === "string") return data.trim() || null;

      const candidates = [
        data?.publicId,
        data?.id,
        data?.personPublicId,
        data?.personId,
        data?.data?.publicId,
        data?.data?.id,
      ].filter(Boolean);

      return candidates.length ? String(candidates[0]).trim() : null;
    }

    const text = await res.text().catch(() => "");
    return text.trim() || null;
  }

  async function fetchFilmographyByPublicId(publicId) {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}/movies`, {
      method: "GET",
      headers: { "Accept": "application/json" }
    });

    if (!res.ok) return [];
    const data = await res.json().catch(() => []);
    return Array.isArray(data) ? data : [];
  }

  async function fetchProfileByPublicId(publicId) {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;
    const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}`, {
      method: "GET",
      headers: { "Accept": "application/json" }
    });
    if (!res.ok) return null;
    return await res.json().catch(() => null);
  }

  /* ============================
   * 4) FilmCardModule
   * ============================ */
  const FilmCardModule = (() => {
    let idSeq = 1;

    function create(containerEl, initial = {}) {
      const card = document.createElement("div");
      card.className = "film-card";
      card.setAttribute("draggable", "true");
      card.dataset.filmId = initial.id || `temp-${idSeq++}`;

      card._thumbnailFile = null;
      card._trailerFile = null;
      card._videoFile = null;

      card._thumbnailUrl = initial.thumbnailUrl || "";
      card._trailerUrls  = Array.isArray(initial.trailerUrls) ? [...initial.trailerUrls] : [];
      card._movieUrl     = initial.movieUrl || "";
      card._genres = Array.isArray(initial.genres) ? [...initial.genres] : [];
      card._isPrivate = !!initial.isPrivate;
      card._hadThumbnail = !!initial.thumbnailUrl;
      card._hadTrailer = Array.isArray(initial.trailerUrls) && initial.trailerUrls.length > 0;
      card._hadVideo = !!initial.movieUrl;
      card._deleteThumbnail = false;
      card._deleteTrailer = false;
      card._deleteVideo = false;

      card.innerHTML = template(card, initial);

      if (initial.ageRating) card.querySelector(".film-age-input").value = initial.ageRating;
      if (initial.personRole) card.querySelector(".mp-person-role").value = initial.personRole;
      if (initial.castType) card.querySelector(".mp-cast-type").value = initial.castType;

      bindHeaderTitleSync(card);
      bindPrivacy(card);
      bindDelete(card);
      bindGenreChips(card);
      bindThumbDropzone(card);
      bindTrailerDropzone(card);
      bindVideoDropzone(card);
      updateInitialUploadStatus(card);

      containerEl.appendChild(card);
      renderGenreChips(card);

      return card;
    }

    function template(card, initial) {
      return `
      <div class="film-card-header">
        <div class="film-card-left">
          <div class="drag-handle">≡</div>
          <div class="film-card-title">${Util.escapeHtml(initial.title || "새 작품")}</div>
        </div>
        <div class="film-card-actions">
          <button type="button" class="film-privacy-btn ${initial.isPrivate ? "is-on" : ""}">
            ${initial.isPrivate ? "비공개" : "공개"}
          </button>
          <button type="button" class="delete-film-btn">삭제</button>
        </div>
      </div>

      <div class="film-card-body">
        <div class="film-fields">
          <div class="field-group">
            <label class="field-label">제목</label>
            <input class="text-input film-title-input" type="text" placeholder="예) 인셉션" value="${initial.title || ""}">
          </div>

          <div class="film-meta-grid">
            <div class="field-group">
              <label class="field-label">개봉연도</label>
              <input class="text-input film-year-input" type="text" inputmode="numeric" maxlength="4" placeholder="YYYY" value="${initial.year || ""}">
            </div>

            <div class="field-group">
              <label class="field-label">런타임(분)</label>
              <input class="text-input film-runtime-input" type="number" min="1" placeholder="예) 120" value="${initial.runtime || ""}">
            </div>

            <div class="field-group">
              <label class="field-label">관람연령</label>
              <select class="select-input film-age-input">
                <option value="">선택</option>
                <option value="ALL">전체 관람가</option>
                <option value="AGE_12">12세 이상</option>
                <option value="AGE_15">15세 이상</option>
                <option value="AGE_18">청소년 관람불가</option>
              </select>
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">장르</label>
            <div class="field-hint">입력 후 엔터를 누르면 장르가 추가됩니다.</div>
            <div class="chip-input-wrap">
              <input class="text-input genre-chip-input" type="text" placeholder="예) 드라마 (엔터로 추가)">
              <div class="chip-box genre-chip-box" aria-label="장르 목록"></div>
            </div>
          </div>

          <div class="film-meta-grid">
            <div class="field-group">
              <label class="field-label">PersonRole</label>
              <select class="select-input mp-person-role">
                <option value="">선택</option>
                <option value="ACTOR">배우</option>
                <option value="DIRECTOR">감독</option>
                <option value="WRITER">작가</option>
              </select>
            </div>

            <div class="field-group">
              <label class="field-label">CastType</label>
              <select class="select-input mp-cast-type">
                <option value="">선택</option>
                <option value="LEAD">주연</option>
                <option value="SUPPORTING">조연</option>
                <option value="CAMEO">단역</option>
              </select>
            </div>

            <div class="field-group">
              <label class="field-label">극중 배역이름</label>
              <input class="text-input mp-character-name" type="text" placeholder="예) 코브" value="${initial.characterName || ""}">
            </div>
          </div>
        </div>

        <div class="upload-block">
          <div class="field-group">
            <label class="field-label">섬네일 업로드</label>
            <div class="field-hint">이미지 파일을 드래그 앤 드롭하거나 파일 선택 버튼을 눌러 업로드하세요.</div>
          </div>

          <div class="dropzone thumb-dropzone">
            <div><strong>여기로 파일을 드래그하세요</strong></div>
            <div>또는 아래 버튼을 눌러 파일을 선택하세요.</div>
            <button type="button" class="thumb-browse-btn">파일 선택</button>
            <div class="file-info thumb-file-info">${
              card._thumbnailUrl ? "업로드 완료" : "선택된 파일 없음"
      }</div>
            <div class="upload-status thumb-status">
              <div class="spinner-circle" style="display:none;"></div>
              <div class="check"></div>
              <span class="status-text"></span>
              <button type="button" class="clear-btn">선택 취소</button>
            </div>
            <input type="file" class="thumb-file-input" accept="image/*" style="display:none;">
          </div>

          <div class="field-group" style="margin-top:10px;">
            <label class="field-label">트레일러 업로드</label>
            <div class="field-hint">영상 파일을 드래그 앤 드롭하거나 파일 선택 버튼을 눌러 업로드하세요.</div>
          </div>

          <div class="dropzone trailer-dropzone">
            <div><strong>여기로 파일을 드래그하세요</strong></div>
            <div>또는 아래 버튼을 눌러 파일을 선택하세요.</div>
            <button type="button" class="trailer-browse-btn">파일 선택</button>
            <div class="file-info trailer-file-info">${
              card._trailerUrls.length ? "업로드 완료" : "선택된 파일 없음"
      }</div>
            <div class="upload-status trailer-status">
              <div class="spinner-circle" style="display:none;"></div>
              <div class="check"></div>
              <span class="status-text"></span>
              <button type="button" class="clear-btn">선택 취소</button>
            </div>
            <input type="file" class="trailer-file-input" accept="video/*" style="display:none;">
          </div>

          <div class="field-group" style="margin-top:10px;">
            <label class="field-label">영상 업로드</label>
            <div class="field-hint">작품(원본) 영상을 드래그 앤 드롭하거나 파일 선택 버튼을 눌러 업로드하세요.</div>
          </div>

          <div class="dropzone video-dropzone">
            <div><strong>여기로 파일을 드래그하세요</strong></div>
            <div>또는 아래 버튼을 눌러 파일을 선택하세요.</div>
            <button type="button" class="video-browse-btn">파일 선택</button>
            <div class="file-info video-file-info">${
              card._movieUrl ? "업로드 완료" : "선택된 파일 없음"
      }</div>
            <div class="upload-status video-status">
              <div class="spinner-circle" style="display:none;"></div>
              <div class="check"></div>
              <span class="status-text"></span>
              <button type="button" class="clear-btn">선택 취소</button>
            </div>
            <input type="file" class="video-file-input" accept="video/*" style="display:none;">
          </div>
        </div>
      </div>
    `;
    }

    function bindHeaderTitleSync(card) {
      const titleInput = card.querySelector(".film-title-input");
      const headerTitle = card.querySelector(".film-card-title");
      titleInput.addEventListener("input", () => {
        headerTitle.textContent = titleInput.value || "새 작품";
      });
    }

    function bindDelete(card) {
      card.querySelector(".delete-film-btn").addEventListener("click", () => {
        if (!confirm("이 작품을 삭제할까요?")) return;

        const movieId = card.dataset.filmId;
        if (movieId && /^[0-9]+$/.test(movieId)) {
          window.__DELETED_MOVIE_IDS__.push(Number(movieId));
        }

        card.remove();
      });
    }

    function bindPrivacy(card) {
      const btn = card.querySelector(".film-privacy-btn");
      if (!btn) return;
      btn.addEventListener("click", async () => {
        const next = !card._isPrivate;
        const movieId = card.dataset.filmId;
        if (movieId && /^[0-9]+$/.test(movieId)) {
          const publicId = window.__EDITING_PUBLIC_ID__;
          if (!publicId) {
            alert("publicId를 찾을 수 없습니다. 저장 후 다시 시도해주세요.");
            return;
          }
          const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
                  ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
                  : fetch;
          const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}/filmography/item/privacy`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ movieId: Number(movieId), isPrivate: next })
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            alert(`비공개 설정 실패: ${res.status} ${msg}`);
            return;
          }
        }

        card._isPrivate = next;
        btn.classList.toggle("is-on", next);
        btn.textContent = next ? "비공개" : "공개";
      });
    }

    function bindGenreChips(card){
      const input = card.querySelector(".genre-chip-input");

      let composing = false;
      input.addEventListener("compositionstart", () => composing = true);
      input.addEventListener("compositionend", () => composing = false);

      input.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        if (e.isComposing || composing) return;

        e.preventDefault();

        const value = Util.normalizeGenre(input.value);
        if (!value) return;

        const exists = card._genres.some(g => g.toLowerCase() === value.toLowerCase());
        if (exists) { input.value = ""; return; }

        card._genres.push(value);
        input.value = "";
        renderGenreChips(card);
      });
    }

    function renderGenreChips(card){
      const box = card.querySelector(".genre-chip-box");
      box.innerHTML = "";

      (card._genres || []).forEach((g, idx) => {
        const chip = document.createElement("span");
        chip.className = "chip";
        chip.draggable = true;
        chip.innerHTML = `
          <span>${Util.escapeHtml(g)}</span>
          <button type="button" aria-label="장르 삭제">×</button>
        `;
        chip.querySelector("button").addEventListener("click", () => {
          card._genres.splice(idx, 1);
          renderGenreChips(card);
        });
        chip.addEventListener("dragstart", (e) => {
          e.stopPropagation();
          chip.classList.add("dragging");
          card._dragGenreIndex = idx;
          card.setAttribute("draggable", "false");
          try { e.dataTransfer.setData("text/plain", g); } catch (_) {}
        });
        chip.addEventListener("dragend", (e) => {
          e.stopPropagation();
          chip.classList.remove("dragging");
          card._dragGenreIndex = null;
          card.setAttribute("draggable", "true");
        });
        chip.addEventListener("dragover", (e) => {
          e.preventDefault();
          e.stopPropagation();
        });
        chip.addEventListener("drop", (e) => {
          e.preventDefault();
          e.stopPropagation();
          const from = card._dragGenreIndex;
          const to = idx;
          if (from == null || from === to) return;
          const arr = card._genres || [];
          const [moved] = arr.splice(from, 1);
          arr.splice(to, 0, moved);
          renderGenreChips(card);
        });
        box.appendChild(chip);
      });
    }

    function updateInitialUploadStatus(card) {
      const map = [
        { url: card._thumbnailUrl, status: card.querySelector(".thumb-status"), info: card.querySelector(".thumb-file-info") },
        { url: (card._trailerUrls && card._trailerUrls.length ? card._trailerUrls[0] : ""), status: card.querySelector(".trailer-status"), info: card.querySelector(".trailer-file-info") },
        { url: card._movieUrl, status: card.querySelector(".video-status"), info: card.querySelector(".video-file-info") },
      ];

      map.forEach(({ url, status, info }) => {
        if (!status) return;
        const spinner = status.querySelector(".spinner-circle");
        const check = status.querySelector(".check");
        const text = status.querySelector(".status-text");
        const hasUrl = !!(url && String(url).trim());

        if (spinner) spinner.style.display = "none";
        if (check) check.style.display = hasUrl ? "block" : "none";
        if (text) text.textContent = hasUrl ? "완료" : "";
        if (info) info.textContent = hasUrl ? "업로드 완료" : "선택된 파일 없음";
      });
    }

    function bindThumbDropzone(card) {
      const dropzone = card.querySelector(".thumb-dropzone");
      const browseBtn = card.querySelector(".thumb-browse-btn");
      const fileInput = card.querySelector(".thumb-file-input");
      const fileInfo  = card.querySelector(".thumb-file-info");
      const statusBox = card.querySelector(".thumb-status");
      const statusSpinner = statusBox?.querySelector(".spinner-circle");
      const statusCheck = statusBox?.querySelector(".check");
      const statusText = statusBox?.querySelector(".status-text");
      const clearBtn = statusBox?.querySelector(".clear-btn");

      function handleFiles(files) {
        if (!files || !files.length) return;
        const file = files[0];
        if (!file.type.startsWith("image/")) { alert("이미지 파일만 업로드할 수 있습니다."); return; }

        card._thumbnailFile = file;
        card._thumbnailUrl = null;
        if (card._hadThumbnail) card._deleteThumbnail = true;

        fileInfo.textContent =
                `선택됨: ${file.name} (${Math.round(file.size/1024)}KB)\n` +
                `업로드 대기`;
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = "";
      }

      browseBtn.addEventListener("click", () => fileInput.click());
      fileInput.addEventListener("change", (e) => handleFiles(e.target.files));
      Util.bindDropzone(dropzone, handleFiles);

      clearBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        fileInput.value = "";
        card._thumbnailFile = null;
        if (card._hadThumbnail) card._deleteThumbnail = true;
        card._thumbnailUrl = "";
        fileInfo.textContent = card._hadThumbnail ? "삭제 예정" : "선택된 파일 없음";
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = card._hadThumbnail ? "삭제 예정" : "";
      });
    }

    function bindTrailerDropzone(card) {
      const dropzone = card.querySelector(".trailer-dropzone");
      const browseBtn = card.querySelector(".trailer-browse-btn");
      const fileInput = card.querySelector(".trailer-file-input");
      const fileInfo  = card.querySelector(".trailer-file-info");
      const statusBox = card.querySelector(".trailer-status");
      const statusSpinner = statusBox?.querySelector(".spinner-circle");
      const statusCheck = statusBox?.querySelector(".check");
      const statusText = statusBox?.querySelector(".status-text");
      const clearBtn = statusBox?.querySelector(".clear-btn");

      function handleFiles(files) {
        if (!files || !files.length) return;
        const file = files[0];
        if (!file.type.startsWith("video/")) { alert("영상 파일만 업로드할 수 있습니다."); return; }

        card._trailerFile = file;
        card._trailerUrls = [];
        if (card._hadTrailer) card._deleteTrailer = true;

        fileInfo.textContent =
                `선택됨: ${file.name} (${Math.round(file.size/1024/1024)}MB)\n` +
                `업로드 대기`;
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = "";
      }

      browseBtn.addEventListener("click", () => fileInput.click());
      fileInput.addEventListener("change", (e) => handleFiles(e.target.files));
      Util.bindDropzone(dropzone, handleFiles);

      clearBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        fileInput.value = "";
        card._trailerFile = null;
        if (card._hadTrailer) card._deleteTrailer = true;
        card._trailerUrls = [];
        fileInfo.textContent = card._hadTrailer ? "삭제 예정" : "선택된 파일 없음";
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = card._hadTrailer ? "삭제 예정" : "";
      });
    }

    function bindVideoDropzone(card) {
      const dropzone = card.querySelector(".video-dropzone");
      const browseBtn = card.querySelector(".video-browse-btn");
      const fileInput = card.querySelector(".video-file-input");
      const fileInfo  = card.querySelector(".video-file-info");
      const statusBox = card.querySelector(".video-status");
      const statusSpinner = statusBox?.querySelector(".spinner-circle");
      const statusCheck = statusBox?.querySelector(".check");
      const statusText = statusBox?.querySelector(".status-text");
      const clearBtn = statusBox?.querySelector(".clear-btn");

      function handleFiles(files) {
        if (!files || !files.length) return;
        const file = files[0];
        if (!file.type.startsWith("video/")) { alert("영상 파일만 업로드할 수 있습니다."); return; }

        card._videoFile = file;
        card._movieUrl = null;
        if (card._hadVideo) card._deleteVideo = true;

        fileInfo.textContent =
                `선택됨: ${file.name} (${Math.round(file.size/1024/1024)}MB)\n` +
                `업로드 대기`;
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = "";
      }

      browseBtn.addEventListener("click", () => fileInput.click());
      fileInput.addEventListener("change", (e) => handleFiles(e.target.files));
      Util.bindDropzone(dropzone, handleFiles);

      clearBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        fileInput.value = "";
        card._videoFile = null;
        if (card._hadVideo) card._deleteVideo = true;
        card._movieUrl = "";
        fileInfo.textContent = card._hadVideo ? "삭제 예정" : "선택된 파일 없음";
        if (statusSpinner) statusSpinner.style.display = "none";
        if (statusCheck) statusCheck.style.display = "none";
        if (statusText) statusText.textContent = card._hadVideo ? "삭제 예정" : "";
      });
    }

    function getPayload(card) {
      const title      = card.querySelector(".film-title-input").value.trim();
      const yearStr    = card.querySelector(".film-year-input").value.trim();
      const runtimeStr = card.querySelector(".film-runtime-input").value.trim();
      const ageRate    = card.querySelector(".film-age-input").value;

      const role          = card.querySelector(".mp-person-role").value || null;
      const castType      = card.querySelector(".mp-cast-type").value || null;
      const characterName = card.querySelector(".mp-character-name").value.trim() || null;

      return {
        title,
        runtimeStr,
        yearStr,
        ageRate,
        role,
        castType,
        characterName,
        rawGenreTexts: [...(card._genres || [])],
        thumbnailUrl: card._thumbnailUrl || null,
        trailerUrls: card._trailerUrls || [],
        movieUrl: card._movieUrl || ""
      };
    }

    return { create, getPayload };
  })();

  /* ============================
   * 5) DragSortModule
   * ============================ */
  const DragSortModule = (() => {
    let draggedCard = null;

    function attach(containerEl) {
      containerEl.addEventListener("dragstart", (e) => {
        const card = e.target.closest(".film-card");
        if (!card) return;
        draggedCard = card;
        card.classList.add("dragging");
      });

      containerEl.addEventListener("dragend", (e) => {
        const card = e.target.closest(".film-card");
        if (card) card.classList.remove("dragging");
        draggedCard = null;
      });

      containerEl.addEventListener("dragover", (e) => {
        e.preventDefault();
        if (!draggedCard) return;

        const afterElement = getDragAfterElement(containerEl, e.clientY);
        if (!afterElement) containerEl.appendChild(draggedCard);
        else containerEl.insertBefore(draggedCard, afterElement);
      });
    }

    function getDragAfterElement(container, y) {
      const draggableElements = [...container.querySelectorAll(".film-card:not(.dragging)")];
      let closest = { offset: Number.NEGATIVE_INFINITY, element: null };

      draggableElements.forEach(el => {
        const rect = el.getBoundingClientRect();
        const offset = y - rect.top - rect.height / 2;
        if (offset < 0 && offset > closest.offset) closest = { offset, element: el };
      });

      return closest.element;
    }

    return { attach };
  })();

  /* ============================
   * 6) POST JSON (✅ apiFetchWithAutoRefresh 우선)
   * ============================ */
  async function postJson(url, body) {
    const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!authReady?.ok) throw new Error("로그인이 필요합니다.");

    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const token = await Promise.resolve(window.OnfilmAuth?.getAccessToken?.()).catch(() => null);
    console.log("token head:", token ? token.slice(0, 12) + "..." : "(none)");

    const res = await fetcher(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      credentials: "include",
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`요청 실패 (${res.status}) ${text}`);
    }

    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) return await res.json();

    const t = await res.text();
    const num = Number(t);
    return Number.isFinite(num) ? num : t;
  }

  async function putJson(url, body) {
    const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!authReady?.ok) throw new Error("로그인이 필요합니다.");

    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const token = await Promise.resolve(window.OnfilmAuth?.getAccessToken?.()).catch(() => null);
    const res = await fetcher(url, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      credentials: "include",
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`요청 실패 (${res.status}) ${text}`);
    }

    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) return await res.json();

    const t = await res.text();
    return t;
  }

  async function uploadMovieAsset(movieId, kind, file) {
    if (!file) return null;

    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const fd = new FormData();
    fd.append("file", file);

    const res = await fetcher(`/api/files/movie/${movieId}/${kind}`, {
      method: "POST",
      body: fd,
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`업로드 실패(${kind}): ${res.status} ${text}`);
    }

    return await res.json().catch(() => null);
  }

  async function deleteMovieAsset(movieId, kind) {
    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const suffix = kind ? `/${kind}` : "";
    const res = await fetcher(`/api/files/movie/${movieId}${suffix}`, { method: "DELETE" });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`삭제 실패(${kind}): ${res.status} ${text}`);
    }
  }

  /* ============================
   * 7) "프로필로 돌아가기" (publicId 기반)
   * ============================ */
  function setupBackToProfileLink() {
    const link = document.getElementById("backToProfileLink");
    if (!link) return;

    link.addEventListener("click", async (e) => {
      e.preventDefault();

      try {
        // 1) username 확보
        let me = window.OnfilmAuth?.getMe?.() || null;
        if (!me || !me.username) {
          const r = await window.OnfilmAuth?.restoreSession?.().catch(() => null);
          me = r?.me || me;
        }
        const uname = me?.username ? String(me.username).trim() : "";
        if (!uname) {
          window.location.href = "/index.html";
          return;
        }

        // 2) username -> publicId (프로필 존재 확인용)
        const publicId = await resolvePublicIdByUsername(uname);
        if (!publicId) {
          window.location.href = "/index.html";
          return;
        }

        // 3) publicId로 프로필 확인: GET /api/people/{publicId}
        const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
                ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
                : fetch;

        const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}`, {
          method: "GET",
          headers: { "Accept": "application/json" }
        });

        if (!res.ok) {
          window.location.href = "/index.html";
          return;
        }

        const p = await res.json().catch(() => null);

        const hasAny =
                !!(p?.name && String(p.name).trim()) ||
                !!(p?.oneLineIntro && String(p.oneLineIntro).trim()) ||
                !!(p?.birthDate && String(p.birthDate).trim()) ||
                !!(p?.birthPlace && String(p.birthPlace).trim()) ||
                !!(p?.profileImageUrl && String(p.profileImageUrl).trim()) ||
                (Array.isArray(p?.snsList) && p.snsList.length > 0) ||
                (Array.isArray(p?.rawTags) && p.rawTags.length > 0);

        if (!hasAny) {
          window.location.href = "/index.html";
          return;
        }

        // ✅ 최종 라우팅은 username
        window.location.href = "/onfilm/" + encodeURIComponent(uname);

      } catch (_) {
        window.location.href = "/index.html";
      }
    });
  }

  /* ============================
   * 8) MAIN
   * ============================ */
  function main() {
    const filmListEl = document.getElementById("filmList");
    const addFilmBtn = document.getElementById("addFilmBtn");
    const form = document.getElementById("filmographyForm");
    const privacyBtn = document.getElementById("filmographyPrivacyBtn");

    DragSortModule.attach(filmListEl);
    initExistingFilmography(filmListEl, privacyBtn);
    addFilmBtn.addEventListener("click", () => FilmCardModule.create(filmListEl, {}));
    setupBackToProfileLink();

    // ✅ 백엔드 MovieController 기준: POST /api/movie
    form.addEventListener("submit", async (e) => {
      e.preventDefault();

      const authReady = window.__ONFILM_AUTH_READY_PROMISE__
              ? await window.__ONFILM_AUTH_READY_PROMISE__
              : await window.OnfilmAuth?.restoreSession?.().catch(() => ({ ok:false }));

      if (!authReady?.ok) {
        const next = encodeURIComponent(location.pathname + location.search);
        location.href = `/login.html?next=${next}`;
        return;
      }

      const submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.disabled = true;

      try {
        const me =
                (await Promise.resolve(window.OnfilmAuth.getMe?.()).catch(() => null))
                || authReady.me;

        const username = (me && me.username) ? String(me.username).trim() : "";
        if (!username) {
          throw new Error("username이 설정되어 있지 않습니다. 프로필에서 username을 설정해 주세요.");
        }

        const publicId = await resolvePublicIdByUsername(username);
        if (!publicId) {
          throw new Error("publicId를 찾을 수 없습니다. (/api/person/{username} 응답 확인 필요)");
        }
        const cards = [...filmListEl.querySelectorAll(".film-card")];
        const items = [];

        for (let i = 0; i < cards.length; i++) {
          const card = cards[i];
          const data = FilmCardModule.getPayload(card);

          if (!data.title) throw new Error(`(${i+1}번째 작품) 제목은 필수입니다.`);
          const runtime = data.runtimeStr ? Number(data.runtimeStr) : 0;
          if (!runtime || runtime <= 0) throw new Error(`(${i+1}번째 작품) 런타임은 1분 이상이어야 합니다.`);
          const releaseYear = data.yearStr ? Number(data.yearStr) : null;
          if (!data.ageRate) throw new Error(`(${i+1}번째 작품) 관람연령을 선택해주세요.`);
          if (!card._videoFile && !data.movieUrl) {
            throw new Error(`(${i+1}번째 작품) 영상 파일을 선택해 주세요.`);
          }

          const key = String(card.dataset.filmId || `temp-${i}`);
          const numericId = /^[0-9]+$/.test(key) ? Number(key) : null;

          items.push({
            clientKey: key,
            movieId: numericId,
            title: data.title,
            runtime,
            releaseYear,
            ageRating: data.ageRate,
            rawGenreTexts: data.rawGenreTexts,
            role: data.role,
            castType: data.castType,
            characterName: data.characterName,
            isPrivate: !!card._isPrivate
          });
        }

        const upsertRes = await putJson(`/api/people/${encodeURIComponent(publicId)}/filmography`, { items });
        const map = new Map();
        (upsertRes?.items || []).forEach((it) => {
          if (it?.clientKey && it?.movieId != null) map.set(String(it.clientKey), it.movieId);
        });

        for (let i = 0; i < cards.length; i++) {
          const card = cards[i];
          const key = String(card.dataset.filmId || `temp-${i}`);
          const movieId = map.get(key) || (/^[0-9]+$/.test(key) ? Number(key) : null);
          if (!movieId) continue;
          card.dataset.filmId = String(movieId);

          const thumbInfo = card.querySelector(".thumb-file-info");
          const trailerInfo = card.querySelector(".trailer-file-info");
          const videoInfo = card.querySelector(".video-file-info");
          const thumbStatus = card.querySelector(".thumb-status");
          const trailerStatus = card.querySelector(".trailer-status");
          const videoStatus = card.querySelector(".video-status");

          const showLoading = (statusBox) => {
            if (!statusBox) return;
            const spinner = statusBox.querySelector(".spinner-circle");
            const check = statusBox.querySelector(".check");
            const text = statusBox.querySelector(".status-text");
            if (spinner) spinner.style.display = "block";
            if (check) check.style.display = "none";
            if (text) text.textContent = "업로드 중...";
          };
          const showDone = (statusBox) => {
            if (!statusBox) return;
            const spinner = statusBox.querySelector(".spinner-circle");
            const check = statusBox.querySelector(".check");
            const text = statusBox.querySelector(".status-text");
            if (spinner) spinner.style.display = "none";
            if (check) check.style.display = "block";
            if (text) text.textContent = "완료";
          };

          if (card._deleteThumbnail) {
            await deleteMovieAsset(movieId, "thumbnail");
            card._deleteThumbnail = false;
            card._hadThumbnail = false;
          }
          let thumbRes = null;
          if (card._thumbnailFile) {
            showLoading(thumbStatus);
            thumbRes = await uploadMovieAsset(movieId, "thumbnail", card._thumbnailFile);
          }
          if (thumbRes?.url) {
            card._thumbnailUrl = thumbRes.url;
            if (thumbInfo) thumbInfo.textContent = "업로드 완료";
            showDone(thumbStatus);
          }

          if (card._deleteTrailer) {
            await deleteMovieAsset(movieId, "trailer");
            card._deleteTrailer = false;
            card._hadTrailer = false;
          }
          let trailerRes = null;
          if (card._trailerFile) {
            showLoading(trailerStatus);
            trailerRes = await uploadMovieAsset(movieId, "trailer", card._trailerFile);
          }
          if (trailerRes?.url) {
            card._trailerUrls = [trailerRes.url];
            if (trailerInfo) trailerInfo.textContent = "업로드 완료";
            showDone(trailerStatus);
          }

          if (card._deleteVideo) {
            await deleteMovieAsset(movieId, "file");
            card._deleteVideo = false;
            card._hadVideo = false;
          }
          let movieRes = null;
          if (card._videoFile) {
            showLoading(videoStatus);
            movieRes = await uploadMovieAsset(movieId, "file", card._videoFile);
          }
          if (movieRes?.url) {
            card._movieUrl = movieRes.url;
            if (videoInfo) videoInfo.textContent = "업로드 완료";
            showDone(videoStatus);
          }
        }

        const uniqueDeleted = Array.from(new Set(window.__DELETED_MOVIE_IDS__ || []));
        for (const movieId of uniqueDeleted) {
          try {
            await deleteMovieAsset(movieId, "");
          } catch (_) {}
        }
        window.__DELETED_MOVIE_IDS__ = [];

        location.href = "/onfilm/" + encodeURIComponent(username);

      } catch (err) {
        console.error(err);
        alert(err?.message ? String(err.message) : "저장 중 오류가 발생했습니다.");
      } finally {
        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  async function initExistingFilmography(filmListEl, privacyBtn) {
    try {
      const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
      if (!authReady?.ok) {
        FilmCardModule.create(filmListEl, {});
        return;
      }

      const me =
              (await Promise.resolve(window.OnfilmAuth.getMe?.()).catch(() => null))
              || authReady.me;
      const username = (me && me.username) ? String(me.username).trim() : "";
      if (!username) {
        FilmCardModule.create(filmListEl, {});
        return;
      }

      const publicId = await resolvePublicIdByUsername(username);
      if (!publicId) {
        FilmCardModule.create(filmListEl, {});
        return;
      }
      window.__EDITING_PUBLIC_ID__ = publicId;

      if (privacyBtn) {
        const profile = await fetchProfileByPublicId(publicId);
        const isPrivate = !!profile?.filmographyPrivate;
        privacyBtn.classList.toggle("is-on", isPrivate);
        privacyBtn.textContent = isPrivate ? "비공개" : "공개";
        privacyBtn.onclick = async () => {
          const next = !privacyBtn.classList.contains("is-on");
          const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
                  ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
                  : fetch;
          const res = await fetcher(`/api/people/${encodeURIComponent(publicId)}/filmography/privacy`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ isPrivate: next }),
          });
          if (!res.ok) {
            const msg = await res.text().catch(() => "");
            alert(`비공개 설정 실패: ${res.status} ${msg}`);
            return;
          }
          privacyBtn.classList.toggle("is-on", next);
          privacyBtn.textContent = next ? "비공개" : "공개";
        };
      }

      const films = await fetchFilmographyByPublicId(publicId);
      if (!films.length) {
        FilmCardModule.create(filmListEl, {});
        return;
      }

      filmListEl.innerHTML = "";
      films.forEach((item) => {
        const genres = (item?.genre || "")
                .split("/")
                .map(s => s.trim())
                .filter(Boolean);

        FilmCardModule.create(filmListEl, {
          id: item?.movieId ?? null,
          title: item?.title || "",
          year: item?.releaseYear != null ? String(item.releaseYear) : "",
          runtime: item?.runtime != null ? String(item.runtime) : "",
          ageRating: item?.ageRating || "",
          thumbnailUrl: item?.thumbnailUrl || "",
          trailerUrls: item?.trailerUrl ? [item.trailerUrl] : [],
          movieUrl: item?.movieUrl || "",
          genres,
          personRole: item?.personRole || "",
          castType: item?.castType || "",
          characterName: item?.characterName || "",
          isPrivate: !!item?.isPrivate,
        });
      });
    } catch (e) {
      console.error("initExistingFilmography error:", e);
      FilmCardModule.create(filmListEl, {});
    }
  }

  document.addEventListener("DOMContentLoaded", main);

