
    const usernameInput     = document.getElementById("signupUsername");
    const usernameOk        = document.getElementById("usernameOk");
    const usernameError     = document.getElementById("usernameError");

    const emailInput        = document.getElementById("signupEmail");
    const emailOk           = document.getElementById("emailOk");
    const emailError        = document.getElementById("emailError");

    const passwordInput     = document.getElementById("signupPassword");
    const passwordConfirm   = document.getElementById("signupPasswordConfirm");
    const passwordError     = document.getElementById("passwordError");
    const termsAll          = document.getElementById("termsAll");
    const termsItems        = document.querySelectorAll(".terms-item");
    const submitBtn         = document.getElementById("signupSubmitBtn");

    const USERNAME_REGEX = /^[a-zA-Z0-9_-]{3,20}$/;
    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    let usernameAvailable = false;
    let checkingUsername = false;
    let usernameTimer = null;

    let emailAvailable = false;
    let checkingEmail = false;
    let emailTimer = null;

    // 비밀번호 보기/가리기
    document.querySelectorAll(".toggle-visibility-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            const targetId = btn.dataset.target;
            const input = document.getElementById(targetId);
            if (!input) return;

            const iconVisible = btn.querySelector(".icon-visible");
            const iconHidden  = btn.querySelector(".icon-hidden");

            if (input.type === "password") {
                input.type = "text";
                if (iconVisible) iconVisible.style.display = "block";
                if (iconHidden)  iconHidden.style.display  = "none";
            } else {
                input.type = "password";
                if (iconVisible) iconVisible.style.display = "none";
                if (iconHidden)  iconHidden.style.display  = "block";
            }
        });
    });

    function validatePasswordMatch() {
        const pwd  = passwordInput.value.trim();
        const pwd2 = passwordConfirm.value.trim();

        if (pwd2.length === 0) {
            passwordError.style.display = "none";
            return true;
        }
        if (pwd === pwd2) {
            passwordError.style.display = "none";
            return true;
        }
        passwordError.style.display = "block";
        return false;
    }

    function setUsernameState({ ok, error }) {
        usernameOk.style.display = ok ? "block" : "none";
        usernameError.style.display = error ? "block" : "none";
    }

    function setEmailState({ ok, error }) {
        emailOk.style.display = ok ? "block" : "none";
        emailError.style.display = error ? "block" : "none";
    }

    function basicUsernameValid() {
        return USERNAME_REGEX.test(usernameInput.value.trim());
    }

    function basicEmailValid() {
        return EMAIL_REGEX.test(emailInput.value.trim());
    }

    // ✅ username 중복 체크
    async function checkUsernameAvailability(username) {
        try {
            const res = await fetch(`/auth/check-username?username=${encodeURIComponent(username)}`, {
                method: "GET",
                credentials: "include"
            });
            if (!res.ok) return false;
            const data = await res.json();
            return !!data.available;
        } catch {
            return false;
        }
    }

    // ✅ email 중복 체크
    async function checkEmailAvailability(email) {
        try {
            const res = await fetch(`/auth/check-email?email=${encodeURIComponent(email)}`, {
                method: "GET",
                credentials: "include"
            });
            if (!res.ok) return false;
            const data = await res.json();
            return !!data.available;
        } catch {
            return false;
        }
    }

    async function validateUsername() {
        const v = usernameInput.value.trim();

        if (v.length === 0) {
            usernameAvailable = false;
            setUsernameState({ ok:false, error:false });
            return;
        }

        if (!USERNAME_REGEX.test(v)) {
            usernameAvailable = false;
            setUsernameState({ ok:false, error:true });
            return;
        }

        checkingUsername = true;
        const available = await checkUsernameAvailability(v);
        checkingUsername = false;

        usernameAvailable = available;
        setUsernameState({ ok:available, error:!available });
    }

    async function validateEmail() {
        const v = emailInput.value.trim();

        if (v.length === 0) {
            emailAvailable = false;
            setEmailState({ ok:false, error:false });
            return;
        }

        if (!EMAIL_REGEX.test(v)) {
            emailAvailable = false;
            setEmailState({ ok:false, error:true });
            return;
        }

        checkingEmail = true;
        const available = await checkEmailAvailability(v);
        checkingEmail = false;

        emailAvailable = available;
        setEmailState({ ok:available, error:!available });
    }

    function validateForm() {
        const pwdVal = passwordInput.value.trim();

        const requiredChecked = Array.from(termsItems)
            .filter(i => i.dataset.required === "true")
            .every(i => i.checked);

        const pwdMatch = validatePasswordMatch();

        const usernameOkNow = basicUsernameValid() && usernameAvailable && !checkingUsername;
        const emailOkNow = basicEmailValid() && emailAvailable && !checkingEmail;

        const isValid =
            usernameOkNow &&
            emailOkNow &&
            pwdVal.length >= 8 &&
            pwdMatch &&
            requiredChecked;

        submitBtn.disabled = !isValid;
    }

    // ✅ username 입력 디바운스 검사
    usernameInput.addEventListener("input", () => {
        usernameAvailable = false;
        setUsernameState({ ok:false, error:false });
        validateForm();

        if (usernameTimer) clearTimeout(usernameTimer);
        usernameTimer = setTimeout(async () => {
            await validateUsername();
            validateForm();
        }, 350);
    });

    // ✅ email 입력 디바운스 검사
    emailInput.addEventListener("input", () => {
        emailAvailable = false;
        setEmailState({ ok:false, error:false });
        validateForm();

        if (emailTimer) clearTimeout(emailTimer);
        emailTimer = setTimeout(async () => {
            await validateEmail();
            validateForm();
        }, 350);
    });

    // 전체 동의
    termsAll.addEventListener("change", () => {
        const checked = termsAll.checked;
        termsItems.forEach(i => i.checked = checked);
        validateForm();
    });

    // 개별 동의
    termsItems.forEach(item => {
        item.addEventListener("change", () => {
            termsAll.checked = Array.from(termsItems).every(i => i.checked);
            validateForm();
        });
    });

    passwordInput.addEventListener("input", validateForm);
    passwordConfirm.addEventListener("input", validateForm);

    function showError(msg) { alert(msg); }

    submitBtn.addEventListener("click", async () => {
        const username = usernameInput.value.trim();
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();
        const password2 = passwordConfirm.value.trim();

        if (!USERNAME_REGEX.test(username)) {
            usernameAvailable = false;
            setUsernameState({ ok:false, error:true });
            validateForm();
            return;
        }

        if (!EMAIL_REGEX.test(email)) {
            emailAvailable = false;
            setEmailState({ ok:false, error:true });
            validateForm();
            return;
        }

        if (password !== password2) {
            passwordError.style.display = "block";
            return;
        }

        // ✅ 가입 직전에 최종 중복 체크 (레이스 컨디션 방지)
        submitBtn.disabled = true;
        submitBtn.textContent = "확인 중...";

        const [emailOkFinal, usernameOkFinal] = await Promise.all([
            checkEmailAvailability(email),
            checkUsernameAvailability(username)
        ]);

        if (!emailOkFinal) {
            emailAvailable = false;
            setEmailState({ ok:false, error:true });
        } else {
            emailAvailable = true;
            setEmailState({ ok:true, error:false });
        }

        if (!usernameOkFinal) {
            usernameAvailable = false;
            setUsernameState({ ok:false, error:true });
        } else {
            usernameAvailable = true;
            setUsernameState({ ok:true, error:false });
        }

        if (!emailOkFinal || !usernameOkFinal) {
            submitBtn.textContent = "회원가입";
            validateForm();
            return;
        }

        submitBtn.textContent = "가입 중...";

        try {
            const res = await fetch("/auth/signup", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ email, password, username })
            });

            let data = null;
            const contentType = res.headers.get("content-type") || "";
            if (contentType.includes("application/json")) data = await res.json();
            else data = await res.text();

            if (!res.ok) {
                const msg =
                    (data && data.message) ? data.message :
                        (typeof data === "string" && data.trim().length ? data : `회원가입 실패 (${res.status})`);
                showError(msg);
                return;
            }

            window.location.href = "/index.html";
        } catch (e) {
            console.error(e);
            showError("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            submitBtn.textContent = "회원가입";
            validateForm();
        }
    });

