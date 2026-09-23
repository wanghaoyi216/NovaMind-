from playwright.sync_api import sync_playwright

BASE = "http://localhost:4200"

def verify(description: str, condition: bool):
    mark = "PASS" if condition else "FAIL"
    print(f"  [{mark}] {description}")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()

    # Set up auth token for admin
    page.goto(f"{BASE}/portal/home", wait_until="networkidle")
    page.evaluate("localStorage.setItem('nova_token', 'test-token')")

    # ── Home Page ──
    print("\n=== Home Page ===")
    page.goto(f"{BASE}/portal/home", wait_until="networkidle")
    page.wait_for_timeout(3000)
    page.screenshot(path="/tmp/home-hero.png", full_page=False)
    verify("Brand logo visible", page.locator("svg").first.is_visible())
    verify("Navigation links count >= 3", page.locator(".nav-item").count() >= 3)
    verify("Footer visible", page.locator("footer").is_visible())
    verify("AI assistant floating", page.locator(".ai-floating-wrapper").count() > 0)

    full = page.screenshot(path="/tmp/home-full.png", full_page=True)
    verify("Home full-page screenshot", len(full) > 5000)

    # ── Login Page ──
    print("\n=== Login ===")
    page.goto(f"{BASE}/portal/login", wait_until="networkidle")
    page.wait_for_timeout(3000)
    page.screenshot(path="/tmp/login.png", full_page=True)
    verify("Login page loaded", "登录" in page.title())
    verify("Page has body content", page.locator("body").inner_html().__len__() > 200)

    # ── Course List ──
    print("\n=== Course List ===")
    page.goto(f"{BASE}/portal/courses", wait_until="networkidle")
    page.wait_for_timeout(3000)
    page.screenshot(path="/tmp/courses.png", full_page=True)
    verify("Course list page loaded", "课程中心" in page.title())

    # ── AI Chat ──
    print("\n=== AI Chat ===")
    page.goto(f"{BASE}/portal/ai-chat", wait_until="networkidle")
    page.wait_for_timeout(3000)
    page.screenshot(path="/tmp/ai-chat.png", full_page=True)
    verify("AI chat page loaded", "AI 助手" in page.title())

    # ── Knowledge Graph ──
    print("\n=== Knowledge Graph ===")
    page.goto(f"{BASE}/portal/knowledge-graph", wait_until="networkidle")
    page.wait_for_timeout(5000)
    page.screenshot(path="/tmp/knowledge-graph.png", full_page=True)
    verify("Knowledge graph page loaded", "知识图谱" in page.title())
    body = page.locator("body").inner_html()
    verify("Has page content", len(body) > 500)

    # ── Admin Pages (with token) ──
    admin_pages = [
        ("/admin", "Admin Overview"),
        ("/admin/courses", "Admin Course Manage"),
        ("/admin/users", "Admin User Manage"),
        ("/admin/ai", "Admin AI Manage"),
        ("/admin/reports", "Admin Reports"),
    ]
    for path, label in admin_pages:
        print(f"\n=== {label} ===")
        page.goto(f"{BASE}{path}", wait_until="networkidle")
        page.wait_for_timeout(2000)
        safe = path.replace("/", "_")
        page.screenshot(path=f"/tmp/admin{safe}.png", full_page=True)
        verify(f"{label} loaded", page.locator("body").is_visible())
        verify("Sidebar visible", page.locator(".admin-sidebar").is_visible())

    # ── Route Redirects ──
    print("\n=== Route Redirect ===")
    page.goto(f"{BASE}/", wait_until="networkidle")
    page.wait_for_timeout(2000)
    verify("/ redirects to /portal/home", page.url.endswith("/portal/home"))

    page.goto(f"{BASE}/portal", wait_until="networkidle")
    page.wait_for_timeout(2000)
    verify("/portal redirects to /portal/home", page.url.endswith("/portal/home"))

    # ── 404 Page ──
    print("\n=== 404 Page ===")
    page.goto(f"{BASE}/nonexistent", wait_until="networkidle")
    page.wait_for_timeout(2000)
    page.screenshot(path="/tmp/404.png", full_page=True)
    verify("404 page renders", "页面未找到" in page.title())

    # ── Auth Guard ──
    print("\n=== Auth Guard ===")
    page.evaluate("localStorage.removeItem('nova_token')")
    page.goto(f"{BASE}/admin", wait_until="networkidle")
    page.wait_for_timeout(2000)
    verify("Admin redirects to login when unauth", page.url.startswith(f"{BASE}/portal/login"))

    # ── Source: no PNG icons ──
    print("\n=== Source Checks ===")
    page.evaluate("localStorage.setItem('nova_token', 'test-token')")
    page.goto(f"{BASE}/portal/home", wait_until="networkidle")
    page.wait_for_timeout(2000)
    html = page.content().lower()
    verify("Favicon is SVG", "favicon.svg" in html)
    verify("No old PNG icon refs", all(x not in html for x in ["icon_nav_logo", "icon_nav_assistant"]))

    # ── Mobile ──
    print("\n=== Mobile Viewport ===")
    page.set_viewport_size({"width": 375, "height": 812})
    page.goto(f"{BASE}/portal/home", wait_until="networkidle")
    page.wait_for_timeout(2000)
    page.screenshot(path="/tmp/home-mobile.png", full_page=True)
    verify("Mobile viewport rendered", page.locator("body").is_visible())

    browser.close()
    print("\n=== ALL VERIFICATIONS COMPLETE ===")
    print("Screenshots saved to /tmp/*.png")
