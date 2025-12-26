/**
 * OnFilm Auth Client (Vanilla JS)
 * - Access token: memory only
 * - Refresh token: HttpOnly cookie (credentials: "include")
 */
window.OnfilmAuth = (() => {
    let accessToken = null;
    let meCache = null;

    // ✅ 동시에 401이 여러 번 나면 refresh가 난사되는 걸 막기 위한 락
    let refreshInFlight = null;

    const AUTH = {
        me: "/auth/me",
        login: "/auth/login",
        signup: "/auth/signup",
        refresh: "/auth/refresh",
        logout: "/auth/logout",
    };

    function setAccessToken(token) {
        accessToken = token || null;
    }
    function getAccessToken() {
        return accessToken;
    }
    function clearAuthState() {
        accessToken = null;
        meCache = null;
    }

    async function apiFetch(url, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set("Accept", "application/json");

        // JSON 문자열 body면 Content-Type 세팅 (FormData는 세팅하면 안 됨)
        if (options.body && typeof options.body === "string") {
            headers.set("Content-Type", "application/json");
        }

        if (accessToken) {
            headers.set("Authorization", `Bearer ${accessToken}`);
        }

        return fetch(url, {
            ...options,
            headers,
            credentials: "include",
        });
    }

    async function safeReadBody(res) {
        const ct = res.headers.get("content-type") || "";
        if (ct.includes("application/json")) {
            try { return await res.json(); } catch { return null; }
        }
        try { return await res.text(); } catch { return null; }
    }

    async function fetchMe() {
        const res = await apiFetch(AUTH.me, { method: "GET" });
        if (res.status === 200) {
            const data = await safeReadBody(res);
            meCache = data;
            return data;
        }
        if (res.status === 401 || res.status === 403) return null;

        const body = await safeReadBody(res);
        throw new Error(`ME_FAILED status=${res.status} body=${JSON.stringify(body)}`);
    }

    async function refreshAccessToken() {
        // ✅ refresh 중이면 그 Promise를 공유
        if (refreshInFlight) return refreshInFlight;

        refreshInFlight = (async () => {
            const res = await apiFetch(AUTH.refresh, { method: "POST" });
            if (res.status !== 200) return null;

            const data = await safeReadBody(res);
            const token = data && (data.accessToken || data.access_token);
            if (!token) throw new Error("REFRESH_OK_BUT_NO_ACCESS_TOKEN_IN_BODY");

            setAccessToken(token);
            return token;
        })();

        try {
            return await refreshInFlight;
        } finally {
            refreshInFlight = null;
        }
    }

    /**
     * ✅ 여기! 자동 refresh 붙은 fetch
     * - 401/403이면 refresh 1번 시도 후 원 요청 재시도
     */
    async function apiFetchWithAutoRefresh(url, options = {}) {
        // refresh endpoint 자체는 재귀 방지
        if (url === AUTH.refresh) {
            return apiFetch(url, options);
        }

        let res = await apiFetch(url, options);

        if (res.status !== 401 && res.status !== 403) {
            return res;
        }

        const token = await refreshAccessToken();
        if (!token) {
            // refresh 실패면 그대로 반환(호출부에서 로그인 리다이렉트 처리)
            return res;
        }

        // ✅ 재시도
        return apiFetch(url, options);
    }

    async function restoreSession() {
        let me = await fetchMe();
        if (me) return { ok: true, me };

        const token = await refreshAccessToken();
        if (!token) {
            clearAuthState();
            return { ok: false, me: null };
        }

        me = await fetchMe();
        if (me) return { ok: true, me };

        clearAuthState();
        return { ok: false, me: null };
    }

    async function login(email, password) {
        const res = await apiFetch(AUTH.login, {
            method: "POST",
            body: JSON.stringify({ email, password }),
        });

        const data = await safeReadBody(res);
        if (!res.ok) {
            const msg = (data && data.message) ? data.message : `로그인 실패 (${res.status})`;
            throw new Error(msg);
        }

        const token = data && (data.accessToken || data.access_token);
        if (!token) throw new Error("LOGIN_OK_BUT_NO_ACCESS_TOKEN_IN_BODY");

        setAccessToken(token);
        const me = await fetchMe();
        return me;
    }

    async function signup(email, password) {
        const res = await apiFetch(AUTH.signup, {
            method: "POST",
            body: JSON.stringify({ email, password }),
        });

        const data = await safeReadBody(res);
        if (!res.ok) {
            const msg = (data && data.message) ? data.message : `회원가입 실패 (${res.status})`;
            throw new Error(msg);
        }
        return data;
    }

    async function logout() {
        try {
            await apiFetch(AUTH.logout, { method: "POST" });
        } finally {
            clearAuthState();
        }
    }

    function isLoggedIn() {
        return !!accessToken && !!meCache;
    }
    function getMe() {
        return meCache;
    }

    return {
        apiFetch,
        apiFetchWithAutoRefresh, // ✅ 반드시 노출
        restoreSession,
        login,
        signup,
        logout,
        setAccessToken,
        getAccessToken,
        isLoggedIn,
        getMe,
    };
})();
