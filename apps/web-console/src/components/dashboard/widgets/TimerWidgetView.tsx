import { useEffect, useState } from "react";
import type { TimerWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface TimerWidgetViewProps {
  widget: TimerWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

function formatSeconds(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export default function TimerWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: TimerWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    "value",
    refreshIntervalMs
  );
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (widget.mode !== "elapsed") {
      return;
    }
    const id = window.setInterval(() => setElapsed((v) => v + 1), 1000);
    return () => window.clearInterval(id);
  }, [widget.mode]);

  const duration = widget.durationSeconds ?? 60;
  const fromVar = typeof rawValue === "number" ? rawValue : Number(rawValue);
  const display =
    widget.mode === "countdown"
      ? formatSeconds(Math.max(0, (Number.isFinite(fromVar) ? fromVar : duration) - elapsed))
      : formatSeconds(elapsed);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-timer"
      editable={editable}
    >
      <p className="dash-timer-value" style={styles.value}>
        {display}
      </p>
    </DashWidgetShell>
  );
}
