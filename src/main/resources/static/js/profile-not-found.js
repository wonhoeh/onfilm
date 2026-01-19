
    const params = new URLSearchParams(window.location.search);
    const u = params.get("u");
    if (u) {
      const hint = document.getElementById("hintText");
      hint.textContent = `요청한 프로필: ${u}`;
    }
  
