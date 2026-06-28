import { describe, expect, it } from "vitest";
import { PLATFORM_BINDING_ENTRIES, PLATFORM_BINDING_NAMES } from "./platformBindings";

describe("platformBindings", () => {
  it("lists 18 functions matching PlatformBindingRegistry", () => {
    expect(PLATFORM_BINDING_ENTRIES).toHaveLength(18);
    expect(PLATFORM_BINDING_NAMES).toContain("counterRate");
    expect(PLATFORM_BINDING_NAMES).toContain("callFunctionAt");
    expect(PLATFORM_BINDING_NAMES).toContain("sumRecordField");
  });

  it("uses unique ids", () => {
    const ids = PLATFORM_BINDING_ENTRIES.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
