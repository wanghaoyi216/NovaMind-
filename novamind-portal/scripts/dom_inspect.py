from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})

    # Navigate first, then set token
    page.goto("http://localhost:4200/portal/home", wait_until="networkidle")
    page.evaluate("localStorage.setItem('nova_token', 'test-token')")

    # KG page
    page.goto("http://localhost:4200/portal/knowledge-graph", wait_until="networkidle")
    page.wait_for_timeout(3000)
    print("=== KG PAGE BODY ===")
    body = page.locator("body").inner_html()
    print(body[:3000])

    # Admin page
    page.goto("http://localhost:4200/admin", wait_until="networkidle")
    page.wait_for_timeout(3000)
    print("\n\n=== ADMIN BODY ===")
    body = page.locator("body").inner_html()
    print(body[:3000])

    browser.close()
