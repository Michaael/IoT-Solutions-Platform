import type { Page, Route } from "@playwright/test";
import { expect } from "@playwright/test";

export interface MockAuthSession {
  token: string;
  username: string;
  displayName: string;
  roles: string[];
}

export const MOCK_ADMIN_SESSION: MockAuthSession = {
  token: "e2e-mock-token",
  username: "admin",
  displayName: "Admin",
  roles: ["admin"],
};

export const MOCK_DEVICE_PATH = "root.platform.devices.lab-sensor";
export const MOCK_DASHBOARD_PATH = "root.platform.dashboards.ops-board";

const NOW = "2026-06-28T00:00:00.000Z";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

function mockObject(
  path: string,
  type: string,
  displayName: string,
  extra: { variableNames?: string[]; revision?: number } = {},
) {
  return {
    id: path.replace(/\./g, "-"),
    path,
    type,
    displayName,
    description: "",
    templateId: null,
    createdAt: NOW,
    sortOrder: 0,
    variableNames: extra.variableNames ?? [],
    eventNames: [],
    revision: extra.revision ?? 0,
  };
}

const TREE_CHILDREN: Record<string, ReturnType<typeof mockObject>[]> = {
  root: [mockObject("root.platform", "PLATFORM", "Platform")],
  "root.platform": [
    mockObject("root.platform.devices", "DEVICES", "Devices"),
    mockObject("root.platform.dashboards", "DASHBOARDS", "Dashboards"),
  ],
  "root.platform.devices": [
    mockObject(MOCK_DEVICE_PATH, "DEVICE", "Lab sensor", { variableNames: ["temperature"] }),
  ],
  "root.platform.dashboards": [
    mockObject(MOCK_DASHBOARD_PATH, "DASHBOARD", "Ops board"),
  ],
};

const ALL_MOCK_OBJECTS = Object.values(TREE_CHILDREN).flat();

function flattenMockTree(): ReturnType<typeof mockObject>[] {
  const items: ReturnType<typeof mockObject>[] = [];
  const visit = (parent: string) => {
    for (const child of TREE_CHILDREN[parent] ?? []) {
      items.push(child);
      visit(child.path);
    }
  };
  visit("root");
  return items;
}

const FLAT_MOCK_TREE = flattenMockTree();

const MOCK_DEVICE_EDITOR = {
  object: mockObject(MOCK_DEVICE_PATH, "DEVICE", "Lab sensor", {
    variableNames: ["temperature"],
    revision: 1,
  }),
  variables: [
    {
      name: "temperature",
      value: {
        schema: {
          name: "temperature",
          fields: [{ name: "value", type: "DOUBLE" }],
        },
        rows: [{ value: 21.5 }],
      },
      readable: true,
      writable: true,
      updatedAt: NOW,
      historyEnabled: false,
      historyRetentionDays: null,
    },
  ],
  events: [],
  functions: [],
};

const MOCK_DASHBOARD_LAYOUT = {
  columns: 12,
  rowHeight: 72,
  widgets: [
    {
      id: "w1",
      type: "label",
      title: "E2E KPI",
      text: "42.5 °C",
      x: 0,
      y: 0,
      w: 4,
      h: 2,
    },
  ],
};

const MOCK_DASHBOARD_VIEW = {
  path: MOCK_DASHBOARD_PATH,
  title: "Ops board",
  refreshIntervalMs: 5000,
  layoutJson: JSON.stringify(MOCK_DASHBOARD_LAYOUT),
  layout: MOCK_DASHBOARD_LAYOUT,
};

export async function mockAuthConfig(page: Page, mode: "local" | "oidc" = "local") {
  await page.route("**/api/v1/auth/config", (route) =>
    json(route, {
      mode,
      localLoginEnabled: mode === "local",
      oidc: mode === "oidc" ? { issuer: "https://keycloak.example/realms/ispf", clientId: "web-console" } : undefined,
    }),
  );
}

/** Single dispatcher avoids Playwright glob overlap (`objects?*` vs `by-path/editor`). */
export async function mockAuthenticatedApi(page: Page, session: MockAuthSession = MOCK_ADMIN_SESSION) {
  await mockAuthConfig(page);

  await page.route("**/api/v1/**", (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const { pathname, searchParams } = url;

    if (request.method() !== "GET") {
      return json(route, { message: "mock not implemented" }, 501);
    }

    switch (pathname) {
      case "/api/v1/auth/me":
        return json(route, {
          authenticated: true,
          principal: session.username,
          roles: session.roles,
          timeZone: "UTC",
        });
      case "/api/v1/auth/login":
        return json(route, {
          token: session.token,
          username: session.username,
          displayName: session.displayName,
          roles: session.roles,
        });
      case "/api/v1/info":
        return json(route, {
          name: "ISPF",
          shortName: "ISPF",
          version: "0.9.33-e2e",
          timestamp: NOW,
          javaVersion: "25",
          springBootVersion: "3.4.0",
          capabilities: [],
        });
      case "/api/v1/platform/metrics":
        return json(route, {
          timestamp: NOW,
          sections: [
            {
              id: "runtime",
              title: "Runtime",
              values: { uptimeSeconds: 3600, heapUsedMb: 256 },
            },
          ],
        });
      case "/api/v1/platform/license":
        return json(route, {
          installationId: "e2e-installation-id",
          enforce: false,
          mode: "community",
          tier: null,
          expiresAt: null,
          valid: true,
          message: "AGPL community mode (e2e mock)",
        });
      case "/api/v1/platform/automation-index/stats":
        return json(route, {
          alertRulesIndexed: 0,
          correlatorsIndexed: 0,
          workflowTriggersIndexed: 0,
          lastRebuildAt: null,
        });
      case "/api/v1/platform/redis/health":
        return json(route, {
          enabled: false,
          connected: false,
          host: null,
          port: null,
          correlatorWindowsEnabled: false,
          correlatorWindowStore: "jdbc",
          aclCacheBackend: "local",
          objectAclTtlSeconds: 60,
          contextPackTtlSeconds: 60,
          platformBriefingTtlSeconds: 60,
          correlatorWindowKeys: null,
          connectionError: null,
        });
      case "/api/v1/platform/nats/health":
        return json(route, {
          enabled: false,
          connected: false,
          url: null,
          jetStreamEnabled: false,
          jetStreamReady: false,
          streamMessages: null,
          streamBytes: null,
          consumerPending: null,
          connectionError: null,
        });
      case "/api/v1/platform/reports/yarg/health":
        return json(route, {
          available: false,
          libreOfficePath: null,
          message: "e2e mock",
        });
      case "/api/v1/platform/mcp/health":
        return json(route, {
          enabled: false,
          available: false,
          message: "e2e mock",
        });
      case "/api/v1/operator-apps":
        return json(route, []);
      case "/api/v1/objects/by-path/binding-rules": {
        const objectPath = searchParams.get("path");
        if (objectPath === MOCK_DEVICE_PATH || objectPath) {
          return json(route, []);
        }
        return json(route, []);
      }
      case "/api/v1/objects/by-path/variables": {
        const objectPath = searchParams.get("path");
        if (objectPath === MOCK_DEVICE_PATH) {
          return json(route, MOCK_DEVICE_EDITOR.variables);
        }
        return json(route, []);
      }
      case "/api/v1/objects/by-path/editor": {
        const objectPath = searchParams.get("path");
        if (objectPath === MOCK_DEVICE_PATH) {
          return json(route, MOCK_DEVICE_EDITOR);
        }
        if (objectPath === MOCK_DASHBOARD_PATH) {
          return json(route, {
            object: mockObject(MOCK_DASHBOARD_PATH, "DASHBOARD", "Ops board"),
            variables: [],
            events: [],
            functions: [],
          });
        }
        const summary = ALL_MOCK_OBJECTS.find((obj) => obj.path === objectPath);
        if (summary) {
          return json(route, {
            object: summary,
            variables: [],
            events: [],
            functions: [],
          });
        }
        return json(route, { message: "not found" }, 404);
      }
      case "/api/v1/objects/by-path": {
        const objectPath = searchParams.get("path");
        const found = ALL_MOCK_OBJECTS.find((obj) => obj.path === objectPath);
        if (found) {
          return json(route, found);
        }
        return json(route, { message: "not found" }, 404);
      }
      case "/api/v1/dashboards/by-path": {
        const dashboardPath = searchParams.get("path");
        if (dashboardPath === MOCK_DASHBOARD_PATH) {
          return json(route, MOCK_DASHBOARD_VIEW);
        }
        return json(route, { message: "not found" }, 404);
      }
      case "/api/v1/objects": {
        const parent = searchParams.get("parent") ?? "";
        if (!parent) {
          return json(route, FLAT_MOCK_TREE);
        }
        if (parent === "root") {
          return json(route, FLAT_MOCK_TREE);
        }
        return json(route, TREE_CHILDREN[parent] ?? []);
      }
      default:
        break;
    }

    if (pathname.startsWith("/api/v1/platform/binding-invocations")) {
      return json(route, []);
    }

    if (pathname.startsWith("/api/v1/platform/binding-audit-status")) {
      return json(route, {
        masterEnabled: false,
        objectEnabled: false,
        enabled: false,
      });
    }

    if (pathname.startsWith("/api/v1/platform/update/")) {
      return json(route, {
        checkEnabled: false,
        applyEnabled: false,
        currentVersion: "0.9.33-e2e",
        latestVersion: null,
        updateAvailable: false,
        releaseName: null,
        releaseUrl: null,
        releaseNotes: null,
        publishedAt: null,
        checkedAt: null,
        checkError: null,
        applyState: "idle",
        applyMessage: null,
        applyStartedAt: null,
      });
    }

    if (pathname.startsWith("/api/v1/objects/leases")) {
      return json(route, []);
    }

    if (/^\/api\/v1\/operator-apps\/[^/]+\/ui$/.test(pathname)) {
      return json(route, { message: "not found" }, 404);
    }

    if (pathname.startsWith("/api/v1/applications/")) {
      if (
        pathname.includes("/operator-manifest")
        || pathname.includes("/operator-ui")
        || pathname.includes("/hmi-ui")
        || pathname.includes("/reports/")
      ) {
        return json(route, { message: "not found" }, 404);
      }
      return json(route, []);
    }

    if (pathname.startsWith("/api/v1/ai/")) {
      switch (pathname) {
        case "/api/v1/ai/provider":
          return json(route, {
            enabled: false,
            providerId: "e2e-mock",
            available: false,
            reason: "e2e-mock",
          });
        case "/api/v1/ai/tools/context-pack":
          return json(route, {
            contextPackVersion: "e2e-mock",
            platformVersion: "0.9.33-e2e",
            exampleCount: 0,
          });
        case "/api/v1/ai/agent/tools":
          return json(route, { tools: [] });
        default:
          return json(route, { message: "mock not implemented" }, 501);
      }
    }

    return json(route, []);
  });
}

export async function seedAuthSession(
  page: Page,
  session: MockAuthSession = MOCK_ADMIN_SESSION,
  options?: { selectedPath?: string },
) {
  await page.addInitScript(({ stored, selectedPath }) => {
    localStorage.setItem("ispf-auth-session", JSON.stringify(stored));
    sessionStorage.removeItem("ispf-tree-expanded-paths");
    sessionStorage.setItem("ispf-tree-selected-path", selectedPath ?? "root.platform");
  }, { stored: session, selectedPath: options?.selectedPath });
}

export async function waitForAdminExplorer(page: Page) {
  await expect(page.getByText("Admin console")).toBeVisible();
  await expect(page.locator(".tree-label", { hasText: "Platform" })).toBeVisible();
}

export async function expandTreeTo(page: Page, label: string) {
  await waitForAdminExplorer(page);
  const labelLocator = page.locator(".tree-label", { hasText: label });
  await expect(labelLocator).toBeVisible({ timeout: 15_000 });
  const row = page.locator(".tree-row").filter({ has: labelLocator });
  const toggle = row.locator(".tree-toggle");
  const glyph = await toggle.textContent();
  if (glyph?.includes("▸")) {
    const childrenLoaded = page.waitForResponse(
      (response) =>
        response.url().includes("/api/v1/objects")
        && response.url().includes("parent=")
        && response.ok(),
    );
    await toggle.click();
    await childrenLoaded;
  }
}

export function waitForObjectEditor(page: Page, objectPath: string) {
  return page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/objects/by-path/editor")
      && response.url().includes(encodeURIComponent(objectPath))
      && response.ok(),
    { timeout: 15_000 },
  );
}

export function waitForDashboardLoad(page: Page, dashboardPath: string) {
  return page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/dashboards/by-path")
      && response.url().includes(encodeURIComponent(dashboardPath))
      && response.ok(),
    { timeout: 15_000 },
  );
}

export async function selectTreeObjectByLabel(page: Page, label: string) {
  const labelLocator = page.locator(".tree-label", { hasText: label });
  await expect(labelLocator).toBeVisible({ timeout: 15_000 });
  await page.locator(".tree-row").filter({ has: labelLocator }).click();
}

export async function doubleClickTreeObjectByLabel(page: Page, label: string) {
  const labelLocator = page.locator(".tree-label", { hasText: label });
  await expect(labelLocator).toBeVisible({ timeout: 15_000 });
  await page.locator(".tree-row").filter({ has: labelLocator }).dblclick();
}
