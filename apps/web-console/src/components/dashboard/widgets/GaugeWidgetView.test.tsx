import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import GaugeWidgetView from "./GaugeWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import * as useBoundVariableModule from "../../../hooks/useBoundVariable";

vi.mock("../../../hooks/useBoundVariable", () => ({
  useBoundVariable: vi.fn(),
}));

describe("GaugeWidgetView", () => {
  beforeEach(() => {
    vi.mocked(useBoundVariableModule.useBoundVariable).mockImplementation((_path, name) => {
      if (name === "minLimit") {
        return { rawValue: 0, variable: undefined, isLoading: false, isError: false };
      }
      if (name === "maxLimit") {
        return { rawValue: 200, variable: undefined, isLoading: false, isError: false };
      }
      return { rawValue: 75, variable: undefined, isLoading: false, isError: false };
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders gauge value and min/max range", () => {
    const widget = {
      ...newWidget("gauge", 0),
      title: "Pressure",
      objectPath: "root.platform.devices.sensor-01",
      variableName: "pressure",
      minVariable: "minLimit",
      maxVariable: "maxLimit",
      unit: "bar",
      decimals: 1,
    };

    renderWithDashboard(<GaugeWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("Pressure")).toBeInTheDocument();
    expect(document.querySelector(".dash-gauge-value")?.textContent).toContain("75.0");
    expect(document.querySelector(".dash-gauge-range")?.textContent).toContain("0.0");
    expect(document.querySelector(".dash-gauge-range")?.textContent).toContain("200.0");
  });

  it("shows object hint when path is missing", () => {
    const widget = {
      ...newWidget("gauge", 0),
      title: "Gauge",
      objectPath: "",
      selectionKey: "device",
      variableName: "pressure",
    };

    renderWithDashboard(<GaugeWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText(/Specify object/i)).toBeInTheDocument();
  });
});
