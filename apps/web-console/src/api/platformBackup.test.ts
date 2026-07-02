import { beforeEach, describe, expect, it, vi } from "vitest";
import { exportPlatformBackup } from "./platformBackup";

vi.mock("../auth/session", () => ({ getAuthHeaders: () => ({}) }));

describe("exportPlatformBackup", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("deduplicates concurrent exports for the same root", async () => {
    let resolveFetch!: (response: unknown) => void;
    const fetchPromise = new Promise((resolve) => {
      resolveFetch = resolve;
    });
    const fetchMock = vi.fn(() => fetchPromise);
    vi.stubGlobal("fetch", fetchMock);

    const first = exportPlatformBackup("root.platform.models");
    const second = exportPlatformBackup("root.platform.models");
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveFetch({
      ok: true,
      json: () => Promise.resolve({ nodeCount: 2 }),
    });
    await expect(first).resolves.toEqual({ nodeCount: 2 });
    await expect(second).resolves.toEqual({ nodeCount: 2 });
  });

  it("allows a new export after the previous request settles", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ nodeCount: 1 }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await exportPlatformBackup("root.platform.models");
    await exportPlatformBackup("root.platform.models");

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
