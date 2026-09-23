(function () {
  const brandName = "知行 AI 学堂";
  const resources = [
    {
      title: "实战项目学习台",
      desc: "课程、AI 助手、学习进度与知识图谱联动",
      img: "/assets/brand/hero-students.jpg"
    },
    {
      title: "AI 深度学习",
      desc: "AIGC 工程化场景",
      img: "/assets/brand/course-ai.jpg"
    },
    {
      title: "Java 后端架构",
      desc: "微服务与 Spring 生态",
      img: "/assets/brand/course-coding.jpg"
    },
    {
      title: "前端体验设计",
      desc: "交互、视觉与可访问性",
      img: "/assets/brand/course-uiux.jpg"
    }
  ];

  document.title = brandName;

  function mountResourceStrip() {
    if (document.querySelector(".zxai-resource-strip")) {
      return true;
    }

    const banner = document.querySelector(".mainWrapper .banner");
    if (!banner || !banner.parentElement) {
      return false;
    }

    const strip = document.createElement("section");
    strip.className = "zxai-resource-strip";
    strip.setAttribute("aria-label", "项目资源精选");
    strip.innerHTML = resources.map((item) => `
      <article class="zxai-resource-card">
        <img src="${item.img}" alt="${item.title}" loading="lazy" />
        <strong>${item.title}</strong>
        <span>${item.desc}</span>
      </article>
    `).join("");

    banner.insertAdjacentElement("afterend", strip);
    return true;
  }

  function enhanceLogo() {
    const logoImg = document.querySelector('.logo img');
    if (!logoImg || logoImg.dataset.zxaiEnhanced) return false;

    logoImg.dataset.zxaiEnhanced = 'true';
    logoImg.style.transition = 'opacity 0.3s ease';

    const logoText = document.createElement('span');
    logoText.className = 'zxai-logo-text';
    logoText.textContent = brandName;
    logoText.style.cssText = `
      margin-left: 10px;
      font-size: 18px;
      font-weight: 700;
      color: #10213f;
      letter-spacing: -0.5px;
      vertical-align: middle;
    `;

    const logoLink = logoImg.closest('a');
    if (logoLink) {
      logoLink.style.display = 'inline-flex';
      logoLink.style.alignItems = 'center';
      logoLink.appendChild(logoText);
    }
    return true;
  }

  function addHeroBadge() {
    const banner = document.querySelector('.mainWrapper .banner');
    if (!banner || banner.querySelector('.zxai-hero-badge')) return false;

    const badge = document.createElement('div');
    badge.className = 'zxai-hero-badge';
    badge.textContent = 'AI 驱动的实战学习平台';
    banner.insertBefore(badge, banner.firstChild);
    return true;
  }

  // 增强分类请求：拦截错误，降级显示
  function enhanceCategoryRequest() {
    if (window.__zxaiCategoryEnhanced) return true;
    window.__zxaiCategoryEnhanced = true;

    const categoryFallbackData = {
      code: 200,
      msg: 'OK',
      data: [
        { id: '1000001', name: '后端开发', children: [
          { id: '1000002', name: 'Java', children: [
            { id: '1000003', name: 'Spring Boot', children: [], level: 3, parentId: '1000002' }
          ], level: 2, parentId: '1000001' }
        ], level: 1, parentId: '0' },
        { id: '1000004', name: '前端开发', children: [
          { id: '1000005', name: 'Vue', children: [
            { id: '1000006', name: '工程化', children: [], level: 3, parentId: '1000005' }
          ], level: 2, parentId: '1000004' }
        ], level: 1, parentId: '0' },
        { id: '1000007', name: 'AI 智能', children: [
          { id: '1000008', name: 'AIGC', children: [
            { id: '1000009', name: 'Spring AI', children: [], level: 3, parentId: '1000008' }
          ], level: 2, parentId: '1000007' }
        ], level: 1, parentId: '0' }
      ]
    };

    // 拦截 fetch
    const originalFetch = window.fetch;
    window.fetch = function(...args) {
      const url = args[0] instanceof Request ? args[0].url : String(args[0]);
      if (url.includes('/categorys/all')) {
        return originalFetch.apply(this, args).then(response => {
          if (!response.ok) {
            console.warn('[知行AI] 分类接口返回非200，使用本地数据');
            return new Response(JSON.stringify(categoryFallbackData), {
              status: 200,
              headers: { 'Content-Type': 'application/json' }
            });
          }
          return response;
        }).catch(err => {
          console.warn('[知行AI] 分类接口请求失败，使用本地数据:', err);
          return new Response(JSON.stringify(categoryFallbackData), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
          });
        });
      }
      return originalFetch.apply(this, args);
    };

    // 拦截 XMLHttpRequest
    const originalXHROpen = XMLHttpRequest.prototype.open;
    const originalXHRSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
      this._zxaiUrl = url;
      return originalXHROpen.call(this, method, url, ...rest);
    };

    XMLHttpRequest.prototype.send = function(...args) {
      const url = this._zxaiUrl || '';
      if (url.includes('/categorys/all')) {
        const xhr = this;
        const originalOnReadyStateChange = xhr.onreadystatechange;

        xhr.onreadystatechange = function() {
          if (xhr.readyState === 4) {
            if (xhr.status !== 200 || !xhr.responseText || xhr.responseText.includes('出错')) {
              console.warn('[知行AI] XHR 分类接口异常，使用本地数据');
              Object.defineProperty(xhr, 'status', { value: 200, writable: false });
              Object.defineProperty(xhr, 'responseText', { value: JSON.stringify(categoryFallbackData), writable: false });
              Object.defineProperty(xhr, 'response', { value: JSON.stringify(categoryFallbackData), writable: false });
            }
          }
          if (originalOnReadyStateChange) {
            originalOnReadyStateChange.call(xhr);
          }
        };
      }
      return originalXHRSend.call(this, ...args);
    };

    return true;
  }

  enhanceCategoryRequest();

  const timer = window.setInterval(() => {
    const mounted = mountResourceStrip();
    const logoDone = enhanceLogo();
    const badgeDone = addHeroBadge();
    const categoryDone = enhanceCategoryRequest();
    if (mounted && logoDone && badgeDone && categoryDone) {
      window.clearInterval(timer);
    }
  }, 300);

  window.setTimeout(() => window.clearInterval(timer), 8000);
})();
