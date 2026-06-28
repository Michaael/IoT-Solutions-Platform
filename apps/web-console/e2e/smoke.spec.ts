import { test, expect } from "@playwright/test";
import {
  expandTreeTo,
  mockAuthConfig,
  mockAuthenticatedApi,
  MOCK_DASHBOARD_PATH,
  MOCK_DEVICE_PATH,
  seedAuthSession,
} from "./fixtures/apiMocks";

test.describe("login page", () => {
  test("renders local sign-in form with mocked auth config", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "ISPF" })).toBeVisible();
    await expect(page.getByLabel("Username")).toBeVisible();
    await expect(page.getByLabel("Password")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });
});

test.describe("admin explorer", () => {
  test("opens Explorer shell with mocked session", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expect(page.getByText("Admin console")).toBeVisible();
    await expect(page.getByRole("button", { name: "Explorer" })).toHaveClass(/active/);
    await expect(page.getByText("Object tree")).toBeVisible();
    await expect(page.locator(".tree-label", { hasText: "Platform" })).toBeVisible();
  });
});

test.describe("explorer device variables", () => {
  test("selects a device in the tree (deep link)", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    const sensorRow = page.locator(".tree-row").filter({ hasText: "Lab sensor" });
    await sensorRow.scrollIntoViewIfNeeded();
    await sensorRow.click();

    await expect(page).toHaveURL(/lab-sensor/);
  });

  test("expands the tree to a nested device row", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    await expect(page.locator(".tree-label", { hasText: "Lab sensor" })).toBeVisible();
  });
});

test.describe("dashboard preview", () => {
  test("loads dashboard layout via API mock", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    const payload = await page.evaluate(async (path) => {
      const response = await fetch(`/api/v1/dashboards/by-path?path=${encodeURIComponent(path)}`);
      return response.json() as Promise<{ title: string; layoutJson: string }>;
    }, MOCK_DASHBOARD_PATH);

    expect(payload.title).toBe("Ops board");
    expect(payload.layoutJson).toContain("42.5 °C");
  });
});

test.describe("operator deep link", () => {
  test("loads demo operator manifest from public bundle", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByRole("strong").filter({ hasText: "Demo Application" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Admin console" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Готовые позиции" })).toBeVisible();
  });
});

test.describe("live backend", () => {
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  const hasLiveCreds = Boolean(username && password);

  test.skip(!hasLiveCreds, "Set E2E_USERNAME and E2E_PASSWORD for live login smoke");

  test("signs in against a running ISPF server", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "ISPF" })).toBeVisible({ timeout: 30_000 });

    const localLogin = page.getByLabel("Username");
    if (!(await localLogin.isVisible().catch(() => false))) {
      test.skip(true, "Local login form not available (OIDC-only server)");
    }

    await localLogin.fill(username!);
    await page.getByLabel("Password").fill(password!);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Admin console").or(page.getByText("Operator · applications"))).toBeVisible({
      timeout: 30_000,
    });
  });
});
