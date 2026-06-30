import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, screen, act } from "@testing-library/react";
import TimerWidgetView from "./TimerWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import * as useBoundVariableModule from "../../../hooks/useBoundVariable";
import * as useWidgetObjectPathModule from "../../../hooks/useWidgetObjectPath";

vi.mock("../../../hooks/useBoundVariable", () => ({
  useBoundVariable: vi.fn(),
}));

vi.mock("../../../hooks/useWidgetObjectPath", () => ({
  useWidgetObjectPath: vi.fn(),
  useWidgetSession: vi.fn(),
}));

describe("TimerWidgetView", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(useWidgetObjectPathModule.useWidgetObjectPath).mockReturnValue(
      "root.platform.devices.lab",
    );
    vi.mocked(useBoundVariableModule.useBoundVariable).mockReturnValue({
      rawValue: 90,
      variable: undefined,
      isLoading: false,
      isError: false,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("renders elapsed timer starting at 0:00", () => {
    const widget = {
      ...newWidget("timer", 0),
      title: "Elapsed",
      mode: "elapsed" as const,
      variableName: "runtime",
    };

    renderWithDashboard(<TimerWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("0:00")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByText("0:03")).toBeInTheDocument();
  });

  it("renders countdown from variable duration", () => {
    const widget = {
      ...newWidget("timer", 0),
      title: "Countdown",
      mode: "countdown" as const,
      durationSeconds: 60,
      variableName: "remaining",
    };

    renderWithDashboard(<TimerWidgetView widget={widget} refreshIntervalMs={5000} />);

    expect(screen.getByText("1:30")).toBeInTheDocument();
  });
});
