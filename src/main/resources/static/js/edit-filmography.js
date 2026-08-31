
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
    try {
      const id = await window.OnfilmCommon.fetchPublicIdByUsername(uname);
      return id ? String(id).trim() : null;
    } catch (_) {
      return null;
    }
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
    try {
      return await window.OnfilmCommon.fetchPersonByPublicId(publicId);
    } catch (_) {
      return null;
    }
  }

  /* ============================
   * 4) FilmCardModule
   * ============================ */
  const FilmCardModule = (() => {
    let idSeq = 1;
    let genreInputSeq = 1;
    const AUTOCOMPLETE_DELAY_MS = 250;

    function toGenreState(value) {
      if (typeof value === "string") {
        const name = Util.normalizeGenre(value);
        return name ? { genreId: null, name, custom: true } : null;
      }

      const name = Util.normalizeGenre(value?.name ?? value?.customText);
      if (!name) return null;

      const genreId = value?.genreId == null ? null : Number(value.genreId);
      return {
        genreId: Number.isFinite(genreId) ? genreId : null,
        name,
        custom: value?.custom === true || !Number.isFinite(genreId)
      };
    }

    function genreKey(value) {
      return Util.normalizeGenre(value?.name)
              .replace(/^#+/, "")
              .trim()
              .toLocaleLowerCase("ko-KR");
    }

    function toGenreRequest(genre) {
      return genre.custom
              ? { genreId: null, customText: genre.name }
              : { genreId: genre.genreId, customText: null };
    }

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
      card._genres = Array.isArray(initial.genres)
              ? initial.genres.map(toGenreState).filter(Boolean)
              : [];
      card._genreAutocompleteId = `genre-autocomplete-${genreInputSeq++}`;
      card._isPrivate = !!initial.isPrivate;
      card._hadThumbnail = !!initial.thumbnailUrl;
      card._hadTrailer = Array.isArray(initial.trailerUrls) && initial.trailerUrls.length > 0;
      card._hadVideo = !!initial.movieUrl;
      card._deleteThumbnail = false;
      card._deleteTrailer = false;
      card._deleteVideo = false;

      card.innerHTML = template(card, initial);

      if (initial.ageRating) card.querySelector(".film-age-input").value = initial.ageRating;
      const initialRoles = Array.isArray(initial.roles)
              ? initial.roles
              : (initial.personRole ? [{
                role: initial.personRole,
                castType: initial.castType,
                characterName: initial.characterName
              }] : []);
      initialRoles.forEach((role) => {
        const checkbox = card.querySelector(`.mp-role-option[value="${role.role}"]`);
        if (checkbox) checkbox.checked = true;
      });
      const actorRole = initialRoles.find((role) => role.role === "ACTOR");
      if (actorRole?.castType) card.querySelector(".mp-cast-type").value = actorRole.castType;
      if (actorRole?.characterName) {
        card.querySelector(".mp-character-name").value = actorRole.characterName;
      }

      bindHeaderTitleSync(card);
      bindPrivacy(card);
      bindDelete(card);
      bindGenreChips(card);
      bindThumbDropzone(card);
      bindTrailerDropzone(card);
      bindVideoDropzone(card);
      bindRoleCharacterSync(card);
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
            <input class="text-input film-title-input" type="text" placeholder="작품 제목을 입력해주세요" value="${initial.title || ""}">
          </div>

          <div class="film-meta-grid">
            <div class="field-group">
              <label class="field-label">개봉연도</label>
              <input class="text-input film-year-input" type="text" inputmode="numeric" maxlength="4" placeholder="YYYY" value="${initial.year || ""}">
            </div>

            <div class="field-group">
              <label class="field-label">런타임(분)</label>
              <input class="text-input film-runtime-input" type="number" min="1" placeholder="분 단위로 입력해주세요" value="${initial.runtime || ""}">
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
            <div class="chip-input-wrap">
              <div class="genre-input-shell">
                <input class="text-input genre-chip-input"
                       type="text"
                       maxlength="60"
                       autocomplete="off"
                       role="combobox"
                       aria-autocomplete="list"
                       aria-expanded="false"
                       aria-controls="${card._genreAutocompleteId}"
                       placeholder="표준 장르를 검색하거나 직접 입력하세요">
                <div id="${card._genreAutocompleteId}"
                     class="genre-autocomplete"
                     role="listbox"
                     hidden></div>
              </div>
              <div class="chip-box genre-chip-box" aria-label="장르 목록"></div>
            </div>
          </div>

          <div class="film-meta-grid role-fields">
            <div class="field-group role-selection-field">
              <label class="field-label">참여 역할</label>
              <div class="role-option-list">
                <label class="role-option"><input class="mp-role-option" type="checkbox" value="ACTOR"> 배우</label>
                <label class="role-option"><input class="mp-role-option" type="checkbox" value="DIRECTOR"> 감독</label>
                <label class="role-option"><input class="mp-role-option" type="checkbox" value="WRITER"> 작가</label>
              </div>
            </div>

            <div class="field-group">
              <label class="field-label">캐스팅 구분</label>
              <select class="select-input mp-cast-type">
                <option value="">선택</option>
                <option value="LEAD">주연</option>
                <option value="SUPPORTING">조연</option>
                <option value="CAMEO">단역</option>
              </select>
            </div>

            <div class="field-group">
              <label class="field-label">극중 배역이름</label>
              <input class="text-input mp-character-name" type="text" maxlength="100" placeholder="배역 이름을 입력해주세요">
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

        card._genreAbortController?.abort();

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
      const listbox = card.querySelector(".genre-autocomplete");
      const state = {
        suggestions: [],
        activeIndex: -1,
        loading: false,
        error: "",
        debounceTimer: null,
        requestSequence: 0
      };

      let composing = false;
      input.addEventListener("compositionstart", () => composing = true);
      input.addEventListener("compositionend", () => {
        composing = false;
        scheduleAutocomplete();
      });

      function closeAutocomplete() {
        listbox.hidden = true;
        input.setAttribute("aria-expanded", "false");
        input.removeAttribute("aria-activedescendant");
        state.activeIndex = -1;
      }

      function cancelAutocomplete() {
        clearTimeout(state.debounceTimer);
        card._genreAbortController?.abort();
        state.requestSequence += 1;
        state.loading = false;
        state.suggestions = [];
        closeAutocomplete();
      }

      function setActiveIndex(index) {
        const options = [...listbox.querySelectorAll("[role='option']")];
        if (!options.length) {
          state.activeIndex = -1;
          input.removeAttribute("aria-activedescendant");
          return;
        }

        state.activeIndex = (index + options.length) % options.length;
        options.forEach((option, optionIndex) => {
          const active = optionIndex === state.activeIndex;
          option.classList.toggle("is-active", active);
          option.setAttribute("aria-selected", String(active));
        });

        const activeOption = options[state.activeIndex];
        input.setAttribute("aria-activedescendant", activeOption.id);
        activeOption.scrollIntoView({ block: "nearest" });
      }

      function renderAutocomplete() {
        const query = Util.normalizeGenre(input.value);
        listbox.innerHTML = "";

        if (!query) {
          closeAutocomplete();
          return;
        }

        listbox.hidden = false;
        input.setAttribute("aria-expanded", "true");

        if (state.loading) {
          const loading = document.createElement("div");
          loading.className = "genre-autocomplete-message";
          loading.textContent = "표준 장르를 검색하고 있어요.";
          listbox.appendChild(loading);
          return;
        }

        if (state.error) {
          const error = document.createElement("div");
          error.className = "genre-autocomplete-message is-error";
          error.textContent = state.error;
          listbox.appendChild(error);
        } else if (!state.suggestions.length) {
          const empty = document.createElement("div");
          empty.className = "genre-autocomplete-message";
          empty.textContent = "일치하는 표준 장르가 없습니다.";
          listbox.appendChild(empty);
        } else {
          state.suggestions.forEach((genre, index) => {
            const option = document.createElement("button");
            option.type = "button";
            option.id = `${card._genreAutocompleteId}-option-${index}`;
            option.className = "genre-autocomplete-option";
            option.setAttribute("role", "option");
            option.setAttribute("aria-selected", "false");
            option.textContent = genre.name;
            option.addEventListener("mousedown", event => event.preventDefault());
            option.addEventListener("click", () => addGenre(genre));
            listbox.appendChild(option);
          });
        }

        const customHint = document.createElement("div");
        customHint.className = "genre-custom-hint";
        customHint.textContent = `Enter를 누르면 '${query}'을(를) 직접 입력 장르로 추가합니다.`;
        listbox.appendChild(customHint);
        state.activeIndex = -1;
        input.removeAttribute("aria-activedescendant");
      }

      function addGenre(value) {
        const genre = toGenreState(value);
        if (!genre) return;

        const duplicate = card._genres.some(existing => genreKey(existing) === genreKey(genre));
        if (!duplicate) {
          card._genres.push(genre);
          renderGenreChips(card);
        }

        input.value = "";
        state.error = "";
        cancelAutocomplete();
        input.focus();
      }

      async function loadAutocomplete(query, sequence) {
        const controller = new AbortController();
        card._genreAbortController = controller;

        try {
          const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
                  ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
                  : fetch;
          const response = await fetcher(
                  `/api/genres/autocomplete?query=${encodeURIComponent(query)}`,
                  {
                    method: "GET",
                    headers: { "Accept": "application/json" },
                    credentials: "include",
                    signal: controller.signal
                  }
          );

          if (!response.ok) {
            throw new Error(`자동완성 요청 실패 (${response.status})`);
          }

          const body = await response.json().catch(() => []);
          if (sequence !== state.requestSequence) return;

          const selectedKeys = new Set(card._genres.map(genreKey));
          state.suggestions = (Array.isArray(body) ? body : [])
                  .map(item => toGenreState({
                    genreId: item?.id,
                    name: item?.name,
                    custom: false
                  }))
                  .filter(Boolean)
                  .filter(genre => !selectedKeys.has(genreKey(genre)));
          state.error = "";
        } catch (error) {
          if (error?.name === "AbortError" || sequence !== state.requestSequence) return;
          state.suggestions = [];
          state.error = "추천 장르를 불러오지 못했습니다. 직접 입력은 계속 사용할 수 있어요.";
        } finally {
          if (sequence !== state.requestSequence) return;
          state.loading = false;
          renderAutocomplete();
        }
      }

      function scheduleAutocomplete() {
        if (composing) return;

        clearTimeout(state.debounceTimer);
        card._genreAbortController?.abort();
        const query = Util.normalizeGenre(input.value);
        state.requestSequence += 1;
        state.suggestions = [];
        state.error = "";

        if (!query) {
          state.loading = false;
          closeAutocomplete();
          return;
        }

        const sequence = state.requestSequence;
        state.loading = true;
        renderAutocomplete();
        state.debounceTimer = setTimeout(
                () => loadAutocomplete(query, sequence),
                AUTOCOMPLETE_DELAY_MS
        );
      }

      input.addEventListener("input", scheduleAutocomplete);

      input.addEventListener("keydown", (e) => {
        if (e.isComposing || composing) return;

        if (e.key === "ArrowDown" || e.key === "ArrowUp") {
          if (listbox.hidden || !state.suggestions.length) return;
          e.preventDefault();
          setActiveIndex(state.activeIndex + (e.key === "ArrowDown" ? 1 : -1));
          return;
        }

        if (e.key === "Escape") {
          cancelAutocomplete();
          return;
        }

        if (e.key !== "Enter") return;
        e.preventDefault();

        const inputGenreKey = genreKey({ name: input.value });
        const selected = state.suggestions[state.activeIndex]
                ?? state.suggestions.find(genre => genreKey(genre) === inputGenreKey);
        if (selected) {
          addGenre(selected);
          return;
        }

        const customText = Util.normalizeGenre(input.value);
        if (customText) {
          addGenre({ genreId: null, name: customText, custom: true });
        }
      });

      input.addEventListener("blur", () => {
        window.setTimeout(() => {
          if (document.activeElement !== input) {
            cancelAutocomplete();
          }
        }, 150);
      });
    }

    function renderGenreChips(card){
      const box = card.querySelector(".genre-chip-box");
      box.innerHTML = "";

      (card._genres || []).forEach((genre, idx) => {
        const chip = document.createElement("span");
        chip.className = `chip ${genre.custom ? "is-custom" : "is-standard"}`;
        chip.draggable = true;
        chip.innerHTML = `
          <span>${Util.escapeHtml(genre.name)}</span>
          <span class="chip-kind">${genre.custom ? "직접 입력" : "표준"}</span>
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
          try { e.dataTransfer.setData("text/plain", genre.name); } catch (_) {}
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

    function bindRoleCharacterSync(card) {
      const roleOptions = [...card.querySelectorAll(".mp-role-option")];
      const actorOption = roleOptions.find((option) => option.value === "ACTOR");
      const characterInput = card.querySelector(".mp-character-name");
      const castSelect = card.querySelector(".mp-cast-type");
      if (!actorOption || !characterInput) return;

      const applyState = () => {
        const isActor = actorOption.checked;
        characterInput.disabled = !isActor;
        if (!isActor) characterInput.value = "";
        if (castSelect) {
          castSelect.disabled = !isActor;
          if (!isActor) castSelect.value = "";
        }
      };

      actorOption.addEventListener("change", applyState);
      applyState();
    }

    function getPayload(card) {
      const title      = card.querySelector(".film-title-input").value.trim();
      const yearStr    = card.querySelector(".film-year-input").value.trim();
      const runtimeStr = card.querySelector(".film-runtime-input").value.trim();
      const ageRate    = card.querySelector(".film-age-input").value;

      const castType      = card.querySelector(".mp-cast-type").value || null;
      const characterName = card.querySelector(".mp-character-name").value.trim() || null;
      const roles = [...card.querySelectorAll(".mp-role-option:checked")].map((option) => ({
        role: option.value,
        castType: option.value === "ACTOR" ? castType : null,
        characterName: option.value === "ACTOR" ? characterName : null
      }));

      return {
        title,
        runtimeStr,
        yearStr,
        ageRate,
        roles,
        genres: [...(card._genres || [])].map(toGenreRequest),
        thumbnailUrl: card._thumbnailUrl || null,
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

    const res = await fetcher(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
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

    const res = await fetcher(url, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
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

  async function fetchJobStatus(jobId) {
    const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!authReady?.ok) throw new Error("로그인이 필요합니다.");

    const fetcher = window.OnfilmAuth?.apiFetchWithAutoRefresh
            ? window.OnfilmAuth.apiFetchWithAutoRefresh.bind(window.OnfilmAuth)
            : fetch;

    const res = await fetcher(`/api/media-jobs/${encodeURIComponent(jobId)}`, {
      method: "GET",
      headers: { "Accept": "application/json" },
      credentials: "include"
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`작업 상태 조회 실패: ${res.status} ${text}`);
    }
    return await res.json();
  }

  async function waitForJobCompletion(jobId, onStatus) {
    const timeoutMs = 30 * 60 * 1000;
    const intervalMs = 3000;
    const startedAt = Date.now();

    while (Date.now() - startedAt < timeoutMs) {
      const job = await fetchJobStatus(jobId);
      if (typeof onStatus === "function") onStatus(job);

      if (job?.status === "DONE") return job;
      if (job?.status === "FAILED") {
        throw new Error(job?.failureReason || "인코딩 작업에 실패했습니다.");
      }

      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    throw new Error("인코딩 대기 시간이 초과되었습니다.");
  }

  async function requestMovieAssetPresign(movieId, kind, file) {
    return await postJson(`/api/files/movie/${movieId}/${kind}/presign`, {
      contentType: file?.type || "application/octet-stream"
    });
  }

  async function completeMovieAssetUpload(movieId, kind, sourceKey, file) {
    return await postJson(`/api/files/movie/${movieId}/${kind}/complete`, {
      sourceKey,
      contentType: file?.type || "application/octet-stream"
    });
  }

  async function uploadFileToPresignedUrl(uploadUrl, file, onProgress) {
    const getCsrfToken = () => {
      try {
        const name = "XSRF-TOKEN";
        const parts = document.cookie ? document.cookie.split(";") : [];
        for (const p of parts) {
          const [k, ...rest] = p.trim().split("=");
          if (k === name) return decodeURIComponent(rest.join("="));
        }
      } catch (_) {}
      return null;
    };

    return await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("PUT", uploadUrl, true);
      xhr.withCredentials = true;

      if (file?.type) {
        xhr.setRequestHeader("Content-Type", file.type);
      }

      const csrf = getCsrfToken();
      if (csrf && /^\/|^https?:\/\/localhost(?::\d+)?\//i.test(String(uploadUrl || ""))) {
        xhr.setRequestHeader("X-CSRF-TOKEN", csrf);
      }

      xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable) return;
        const percent = Math.max(0, Math.min(100, Math.round((e.loaded / e.total) * 100)));
        if (typeof onProgress === "function") onProgress(percent);
      };

      xhr.onerror = () => reject(new Error("S3 업로드 중 네트워크 오류가 발생했습니다."));
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve();
          return;
        }
        reject(new Error(`S3 업로드 실패: ${xhr.status} ${xhr.responseText || ""}`));
      };

      xhr.send(file);
    });
  }

  async function uploadMovieAsset(movieId, kind, file, onProgress, onStatus) {
    if (!file) return null;

    const authReady = await window.__ONFILM_AUTH_READY_PROMISE__;
    if (!authReady?.ok) throw new Error("로그인이 필요합니다.");

    if (typeof onStatus === "function") onStatus("presign");
    const presigned = await requestMovieAssetPresign(movieId, kind, file);

    if (!presigned?.sourceKey || !presigned?.uploadUrl) {
      throw new Error(`업로드 준비 실패(${kind}): presign 응답이 올바르지 않습니다.`);
    }

    if (typeof onStatus === "function") onStatus("uploading");
    await uploadFileToPresignedUrl(presigned.uploadUrl, file, onProgress);

    if (typeof onStatus === "function") onStatus("completing");
    const jobResponse = await completeMovieAssetUpload(movieId, kind, presigned.sourceKey, file);

    if (!jobResponse?.jobId) {
      throw new Error(`업로드 완료 처리 실패(${kind}): jobId가 없습니다.`);
    }

    if (typeof onStatus === "function") onStatus("processing");
    const job = await waitForJobCompletion(jobResponse.jobId, (jobStatus) => {
      if (typeof onStatus === "function") onStatus("polling", jobStatus);
    });

    return {
      ...jobResponse,
      status: job?.status || "DONE"
    };
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
          window.location.href = "/";
          return;
        }

        // 2) username -> publicId (프로필 존재 확인용)
        const publicId = await resolvePublicIdByUsername(uname);
        if (!publicId) {
          window.location.href = "/";
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
          window.location.href = "/";
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
          window.location.href = "/";
          return;
        }

        // ✅ 최종 라우팅은 username
        window.location.href = "/" + encodeURIComponent(uname);

      } catch (_) {
        window.location.href = "/";
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
          if (!data.roles.length) throw new Error(`(${i+1}번째 작품) 참여 역할을 하나 이상 선택해주세요.`);
          const actorRole = data.roles.find((role) => role.role === "ACTOR");
          if (actorRole && !actorRole.castType) {
            throw new Error(`(${i+1}번째 작품) 배우의 캐스팅 구분을 선택해주세요.`);
          }
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
            genres: data.genres,
            roles: data.roles,
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

          const showLoading = (statusBox, percent, label) => {
            if (!statusBox) return;
            const spinner = statusBox.querySelector(".spinner-circle");
            const check = statusBox.querySelector(".check");
            const text = statusBox.querySelector(".status-text");
            if (spinner) spinner.style.display = "block";
            if (check) check.style.display = "none";
            if (text) {
              const p = Number.isFinite(percent) ? Math.max(0, Math.min(100, Math.round(percent))) : null;
              if (p == null) text.textContent = label || "처리 중...";
              else text.textContent = `${label || "업로드 중..."} ${p}%`;
            }
          };
          const setInfoText = (infoEl, label) => {
            if (!infoEl) return;
            infoEl.textContent = label || "";
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
            showLoading(thumbStatus, null, "업로드 준비 중...");
            setInfoText(thumbInfo, "업로드 준비 중");
            thumbRes = await uploadMovieAsset(movieId, "thumbnail", card._thumbnailFile, (p) => {
              showLoading(thumbStatus, p, "업로드 중...");
              setInfoText(thumbInfo, Number.isFinite(p) ? `업로드 중 ${Math.round(p)}%` : "업로드 중");
            }, (stage) => {
              if (stage === "presign") setInfoText(thumbInfo, "업로드 준비 중");
              if (stage === "uploading") setInfoText(thumbInfo, "업로드 중");
              if (stage === "completing") {
                showLoading(thumbStatus, null, "인코딩 요청 중...");
                setInfoText(thumbInfo, "인코딩 요청 중");
              }
              if (stage === "processing" || stage === "polling") {
                showLoading(thumbStatus, null, "인코딩 중...");
                setInfoText(thumbInfo, "인코딩 중");
              }
            });
          }
          if (thumbRes?.jobId) {
            card._thumbnailUrl = thumbRes.targetKey || card._thumbnailUrl;
            if (thumbInfo) thumbInfo.textContent = "인코딩 완료";
            showDone(thumbStatus);
          }

          if (card._deleteTrailer) {
            await deleteMovieAsset(movieId, "trailer");
            card._deleteTrailer = false;
            card._hadTrailer = false;
          }
          let trailerRes = null;
          if (card._trailerFile) {
            showLoading(trailerStatus, null, "업로드 준비 중...");
            setInfoText(trailerInfo, "업로드 준비 중");
            trailerRes = await uploadMovieAsset(movieId, "trailer", card._trailerFile, (p) => {
              showLoading(trailerStatus, p, "업로드 중...");
              setInfoText(trailerInfo, Number.isFinite(p) ? `업로드 중 ${Math.round(p)}%` : "업로드 중");
            }, (stage) => {
              if (stage === "presign") setInfoText(trailerInfo, "업로드 준비 중");
              if (stage === "uploading") setInfoText(trailerInfo, "업로드 중");
              if (stage === "completing") {
                showLoading(trailerStatus, null, "인코딩 요청 중...");
                setInfoText(trailerInfo, "인코딩 요청 중");
              }
              if (stage === "processing" || stage === "polling") {
                showLoading(trailerStatus, null, "인코딩 중...");
                setInfoText(trailerInfo, "인코딩 중");
              }
            });
          }
          if (trailerRes?.jobId) {
            card._trailerUrls = trailerRes.targetKey ? [trailerRes.targetKey] : card._trailerUrls;
            if (trailerInfo) trailerInfo.textContent = "인코딩 완료";
            showDone(trailerStatus);
          }

          if (card._deleteVideo) {
            await deleteMovieAsset(movieId, "file");
            card._deleteVideo = false;
            card._hadVideo = false;
          }
          let movieRes = null;
          if (card._videoFile) {
            showLoading(videoStatus, null, "업로드 준비 중...");
            setInfoText(videoInfo, "업로드 준비 중");
            movieRes = await uploadMovieAsset(movieId, "file", card._videoFile, (p) => {
              showLoading(videoStatus, p, "업로드 중...");
              setInfoText(videoInfo, Number.isFinite(p) ? `업로드 중 ${Math.round(p)}%` : "업로드 중");
            }, (stage) => {
              if (stage === "presign") setInfoText(videoInfo, "업로드 준비 중");
              if (stage === "uploading") setInfoText(videoInfo, "업로드 중");
              if (stage === "completing") {
                showLoading(videoStatus, null, "인코딩 요청 중...");
                setInfoText(videoInfo, "인코딩 요청 중");
              }
              if (stage === "processing" || stage === "polling") {
                showLoading(videoStatus, null, "인코딩 중...");
                setInfoText(videoInfo, "인코딩 중");
              }
            });
          }
          if (movieRes?.jobId) {
            card._movieUrl = movieRes.targetKey || card._movieUrl;
            if (videoInfo) videoInfo.textContent = "인코딩 완료";
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

        location.href = "/" + encodeURIComponent(username);

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
        privacyBtn.textContent = isPrivate ? "전체 비공개" : "전체 공개";
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
          privacyBtn.textContent = next ? "전체 비공개" : "전체 공개";
        };
      }

      const films = await fetchFilmographyByPublicId(publicId);
      if (!films.length) {
        FilmCardModule.create(filmListEl, {});
        return;
      }

      filmListEl.innerHTML = "";
      films.forEach((item) => {
        const genres = Array.isArray(item?.genres) ? item.genres : [];

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
          roles: Array.isArray(item?.roles) ? item.roles : [],
          isPrivate: !!item?.isPrivate,
        });
      });
    } catch (e) {
      console.error("initExistingFilmography error:", e);
      FilmCardModule.create(filmListEl, {});
    }
  }

  document.addEventListener("DOMContentLoaded", main);
