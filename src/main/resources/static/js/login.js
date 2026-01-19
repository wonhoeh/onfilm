
    const emailInput      = document.getElementById("loginEmail");
    const passwordInput   = document.getElementById("loginPassword");
    const loginError      = document.getElementById("loginError");
    const loginSubmitBtn  = document.getElementById("loginSubmitBtn");
    const toggleBtn       = document.querySelector(".toggle-visibility-btn");

    // ✅ access token은 "메모리"에만 저장 (새로고침하면 사라짐)
    let accessToken = null;

    function validateLoginForm() {
        const emailVal = emailInput.value.trim();
        const pwdVal   = passwordInput.value.trim();
        return emailVal.length > 0 && pwdVal.length > 0;
    }

    async function tryLogin() {
        if (!validateLoginForm()) {
            loginError.textContent = "이메일과 비밀번호를 모두 입력해주세요.";
            loginError.style.display = "block";
            return;
        }

        loginSubmitBtn.disabled = true;
        loginSubmitBtn.textContent = "로그인 중...";

        try {
            const email = emailInput.value.trim();
            const password = passwordInput.value.trim();

            await window.OnfilmAuth.login(email, password);

            // next 파라미터가 있으면 그쪽으로, 없으면 홈으로
            const params = new URLSearchParams(location.search);
            const next = params.get("next");
            location.href = next ? decodeURIComponent(next) : "/index.html";
        } catch (e) {
            loginError.textContent = e.message || "로그인 실패";
            loginError.style.display = "block";
        } finally {
            loginSubmitBtn.disabled = false;
            loginSubmitBtn.textContent = "로그인";
        }
    }


    function syncPasswordToggle(visible) {
        if (!toggleBtn) return;
        const iconVisible = toggleBtn.querySelector(".icon-visible");
        const iconHidden = toggleBtn.querySelector(".icon-hidden");
        if (iconVisible) iconVisible.style.display = visible ? "block" : "none";
        if (iconHidden) iconHidden.style.display = visible ? "none" : "block";
        toggleBtn.setAttribute("aria-pressed", visible ? "true" : "false");
    }

    toggleBtn?.addEventListener("click", () => {
        if (!passwordInput) return;
        const nextVisible = passwordInput.type === "password";
        passwordInput.type = nextVisible ? "text" : "password";
        syncPasswordToggle(nextVisible);
    });

    passwordInput?.addEventListener("input", (e) => {
        const raw = e.target.value;
        const cleaned = raw.replace(/[^A-Za-z0-9]/g, "");
        if (raw !== cleaned) e.target.value = cleaned;
    });

    loginSubmitBtn.addEventListener("click", tryLogin);
    [emailInput, passwordInput].forEach(el => {
        el.addEventListener("keydown", (e) => {
            if (e.key === "Enter") tryLogin();
        });
    });

