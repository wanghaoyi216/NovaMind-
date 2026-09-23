(function () {
  const brandName = "知行 AI 学堂";

  document.title = `${brandName} - 管理后台`;

  function enhanceLogo() {
    const logoImg = document.querySelector('img[src*="logo"], img[src*="newlogo"]');
    if (!logoImg || logoImg.dataset.zxaiAdminEnhanced) {
      return Boolean(logoImg);
    }

    logoImg.dataset.zxaiAdminEnhanced = "true";
    const wrap = logoImg.closest("a, .logo, div") || logoImg.parentElement;
    if (wrap) {
      wrap.style.display = "inline-flex";
      wrap.style.alignItems = "center";
      const text = document.createElement("span");
      text.className = "zxai-admin-logo-text";
      text.textContent = brandName;
      wrap.appendChild(text);
    }
    return true;
  }

  function mountAdminRibbon() {
    if (document.querySelector(".zxai-admin-ribbon")) {
      return true;
    }

    const ribbon = document.createElement("aside");
    ribbon.className = "zxai-admin-ribbon";
    ribbon.innerHTML = `
      <strong>AI 实战项目后台</strong>
      <span>课程管理、用户运营、AIGC 能力和知识图谱数据将在这里形成统一控制面。</span>
    `;
    document.body.appendChild(ribbon);
    return true;
  }

  function mountLoginHero() {
    if (document.querySelector(".zxai-admin-login-hero")) {
      return true;
    }

    const path = window.location.hash || window.location.pathname;
    if (!/login/i.test(path) && !document.querySelector('input[type="password"]')) {
      return false;
    }

    const hero = document.createElement("section");
    hero.className = "zxai-admin-login-hero";
    hero.innerHTML = `
      <strong>把课程平台做成自己的作品集</strong>
      <span>品牌、资源、AI 助手和图谱能力已经接入，后台用于验证完整业务闭环。</span>
    `;
    document.body.appendChild(hero);
    return true;
  }

  const timer = window.setInterval(() => {
    const logoDone = enhanceLogo();
    const ribbonDone = mountAdminRibbon();
    mountLoginHero();
    if (logoDone && ribbonDone) {
      window.clearInterval(timer);
    }
  }, 300);

  window.setTimeout(() => window.clearInterval(timer), 8000);
})();
