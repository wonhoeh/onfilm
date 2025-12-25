/**
 * OnFilm Auth Client (Vanilla JS)
 * - Access token: memory only (this module variable)
 * - Refresh token: HttpOnly cookie (browser auto-sends)
 * - Auto restore after refresh: /auth/me -> if 401 then /auth/refresh -> /auth/me
 */

window.OnfilmAuth = (() => {
    let accessToken = null;   // ✅ memory only
    let meCache = null;

    const AUTH = {
        // 백엔드 엔드포인트 (같은 도메인이면 그대로)
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

    /**
     * 공통 fetch 래퍼
     * - accessToken 있으면 Authorization 헤더 자동 부착
     * - refresh 쿠키 주고받을 수 있게 credentials: "include"
     */
    async function apiFetch(url, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set("Accept", "application/json");

        // 바디가 JSON이면 Content-Type 세팅 (FormData는 자동이므로 건드리면 안 됨)
        if (options.body && typeof options.body === "string") {
            headers.set("Content-Type", "application/json");
        }

        if (accessToken) {
            headers.set("Authorization", `Bearer ${accessToken}`);
        }

        const res = await fetch(url, {
            ...options,
            headers,
            credentials: "include",
        });

        return res;
    }

    async function safeReadBody(res) {
        const ct = res.headers.get("content-type") || "";
        if (ct.includes("application/json")) {
            try { return await res.json(); } catch { return null; }
        }
        try { return await res.text(); } catch { return null; }
    }

    /**
     * /auth/me 호출
     * - 200이면 meCache 저장
     * - 401이면 null 리턴
     */
    async function fetchMe() {
        const res = await apiFetch(AUTH.me, { method: "GET" });
        if (res.status === 200) {
            const data = await safeReadBody(res);
            meCache = data;
            return data;
        }
        if (res.status === 401 || res.status === 403) return null;

        // 그 외 에러는 던져서 디버깅
        const body = await safeReadBody(res);
        throw new Error(`ME_FAILED status=${res.status} body=${JSON.stringify(body)}`);
    }

    /**
     * /auth/refresh 호출해서 새 access token 받기
     * - 서버가 { accessToken: "..." } 또는 { access_token: "..." } 형태로 준다고 가정
     */
    async function refreshAccessToken() {
        const res = await apiFetch(AUTH.refresh, { method: "POST" });

        if (res.status !== 200) {
            return null; // refresh 쿠키가 없거나 만료/폐기된 케이스
        }

        const data = await safeReadBody(res);
        const token =
            (data && (data.accessToken || data.access_token)) ? (data.accessToken || data.access_token) : null;

        if (!token) {
            throw new Error("REFRESH_OK_BUT_NO_ACCESS_TOKEN_IN_BODY");
        }

        setAccessToken(token);
        return token;
    }

    /**
     * ✅ 자동 로그인 복구 (페이지 로드 시 1번 실행)
     * 1) me 시도
     * 2) 401이면 refresh 시도
     * 3) refresh 성공하면 me 재시도
     * 결과: { ok: boolean, me: object|null }
     */
    async function restoreSession() {
        // access가 이미 있으면 me부터
        let me = await fetchMe();
        if (me) return { ok: true, me };

        // access가 없거나 만료 → refresh로 재발급
        const token = await refreshAccessToken();
        if (!token) {
            clearAuthState();
            return { ok: false, me: null };
        }

        // 새 access로 me 재시도
        me = await fetchMe();
        if (me) return { ok: true, me };

        // refresh는 됐는데 me가 또 실패면 비정상 → 초기화
        clearAuthState();
        return { ok: false, me: null };
    }

    /**
     * 로그인
     * - 성공: accessToken 메모리에 저장 + me 가져오기
     * - 서버가 login 응답으로 accessToken을 준다고 가정
     */
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

    /**
     * 회원가입 (너 정책: 가입 후 수동 로그인)
     */
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

    /**
     * 로그아웃
     * - 서버에 /auth/logout 호출해서 refresh 쿠키 폐기 + DB revoke
     * - 프론트 메모리 access 제거
     */
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