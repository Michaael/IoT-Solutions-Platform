import { test, expect } from "@playwright/test";

/**
 * Live smoke against a deployed ISPF (prod/staging/local jar).
 * Run only when E2E_BASE_URL + credentials are set — see e2e/README.md.
 */
const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const hasLiveCreds = Boolean(username && password);

test.describe("live backend", () => {
  test.skip(!hasLiveCreds, "Set E2E_USERNAME and E2E_PASSWORD for live smoke");

  test("signs in and opens admin Explorer", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "ISPF" })).toBeVisible({ timeout: 30_000 });

    const localLogin = page.getByLabel("Username");
    if (!(await localLogin.isVisible().catch(() => false))) {
      test.skip(true, "Local login form not available (OIDC-only server)");
    }

    await localLogin.fill(username!);
    await page.getByLabel("Password").fill(password!);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Admin console")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: "Explorer" })).toHaveClass(/active/);
    await expect(page.locator(".tree-label", { hasText: "Platform" })).toBeVisible({
      timeout: 30_000,
    });
  });

  test("loads platform info API", async ({ page }) => {
    await page.goto("/");

    const localLogin = page.getByLabel("Username");
    if (!(await localLogin.isVisible().catch(() => false))) {
      test.skip(true, "Local login form not available (OIDC-only server)");
    }

    await localLogin.fill(username!);
    await page.getByLabel("Password").fill(password!);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("Admin console")).toBeVisible({ timeout: 30_000 });

    const info = await page.evaluate(async () => {
      const response = await fetch("/api/v1/info");
      return response.json() as Promise<{ version: string; name: string }>;
    });

    expect(info.name).toContain("ISPF");
    expect(info.version).toMatch(/^\d+\.\d+\.\d+/);
  });

  test("opens System metrics and reads platform license", async ({ page }) => {
    await page.goto("/");

    const localLogin = page.getByLabel("Username");
    if (!(await localLogin.isVisible().catch(() => false))) {
      test.skip(true, "Local login form not available (OIDC-only server)");
    }

    await localLogin.fill(username!);
    await page.getByLabel("Password").fill(password!);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("Admin console")).toBeVisible({ timeout: 30_000 });

    await page.getByRole("button", { name: "System" }).click();
    await expect(page.getByRole("heading", { name: "Platform license" })).toBeVisible({
      timeout: 30_000,
    });

    const license = await page.evaluate(async () => {
      const response = await fetch("/api/v1/platform/license");
      return response.json() as Promise<{ installationId: string; valid: boolean; mode: string }>;
    });
    expect(license.installationId).toBeTruthy();
    expect(typeof license.valid).toBe("boolean");
    expect(license.mode).toBeTruthy();
  });
});
