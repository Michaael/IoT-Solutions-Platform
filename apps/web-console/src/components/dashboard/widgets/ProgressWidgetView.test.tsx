import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import ProgressWidgetView from "./ProgressWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import * as useBoundVariableModule from "../../../hooks/useBoundVariable";

vi.mock("../../../hooks/useBoundVariable", () => ({
  useBoundVariable: vi.fn(),
}));

describe("ProgressWidgetView", () => {
  beforeEach(() => {
    vi.mocked(useBoundVariableModule.useBoundVariable).mockImplementation((_path, name) => {
      if (name === "maxLevel") {
        return {
          rawValue: 100,
          variable: undefined,
          isLoading: false,
          isError: false,
        };
      }
      return {
        rawValue: 65,
        variable: undefined,
        isLoading: false,
        isError: false,
      };
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders progress ratio from bound variables", () => {
    const widget = {
      ...newWidget("progress", 0),
      title: "Tank fill",
      objectPath: "root.platform.devices.tank-01",
      currentVariable: "level",
      maxVariable: "maxLevel",
      unit: "L",
      decimals: 0,
    };

    renderWithDashboard(<ProgressWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("Tank fill")).toBeInTheDocument();
    expect(screen.getByText("65%")).toBeInTheDocument();
    expect(document.querySelector(".dash-progress-value")?.textContent).toContain("65");
    expect(document.querySelector(".dash-progress-head .hint")?.textContent).toContain("100");
  });
});
