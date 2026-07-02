import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { usePersistentTab } from "./usePersistentTab";

const TABS = ["general", "access", "variables"] as const;

describe("usePersistentTab", () => {
  beforeEach(() => sessionStorage.clear());

  it("restores the selected tab after remount", () => {
    const first = renderHook(() => usePersistentTab("object:root.device", "general", TABS));
    act(() => first.result.current[1]("access"));
    first.unmount();

    const second = renderHook(() => usePersistentTab("object:root.device", "general", TABS));
    expect(second.result.current[0]).toBe("access");
  });

  it("keeps independent tabs for different contexts", () => {
    sessionStorage.setItem("ispf:ui:active-tab:object:root.a", "variables");
    const { result, rerender } = renderHook(
      ({ path }) => usePersistentTab(`object:${path}`, "general", TABS),
      { initialProps: { path: "root.a" } }
    );
    expect(result.current[0]).toBe("variables");

    rerender({ path: "root.b" });
    expect(result.current[0]).toBe("general");
  });

  it("ignores unknown stored values", () => {
    sessionStorage.setItem("ispf:ui:active-tab:system", "removed-tab");
    const { result } = renderHook(() => usePersistentTab("system", "general", TABS));
    expect(result.current[0]).toBe("general");
  });
});
