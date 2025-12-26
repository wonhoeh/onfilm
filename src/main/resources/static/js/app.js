document.addEventListener("DOMContentLoaded", async () => {
    const result = await window.OnfilmAuth.restoreSession();
    window.__ONFILM_AUTH_READY__ = result; // 디버깅용
});
