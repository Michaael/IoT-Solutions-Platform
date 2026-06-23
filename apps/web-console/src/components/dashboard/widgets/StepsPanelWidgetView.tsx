import { useMemo, useState } from "react";
import type { DashboardWidget, StepsPanelWidget } from "../../../types/dashboard";
import { resolveContextParam } from "../dashboardUtils";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface StepDef {
  id: string;
  label: string;
  children: DashboardWidget[];
}

interface StepsPanelWidgetViewProps {
  widget: StepsPanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function StepsPanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: StepsPanelWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { params } = useWidgetSession();
  const steps = useMemo(() => {
    try {
      const parsed = widget.stepsJson ? (JSON.parse(widget.stepsJson) as StepDef[]) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as StepDef[];
    }
  }, [widget.stepsJson]);

  const contextStep = widget.activeStepKey
    ? String(resolveContextParam(widget.activeStepKey, params) ?? "")
    : "";
  const [localStep, setLocalStep] = useState(steps[0]?.id ?? "");
  const activeId = contextStep || localStep;
  const active = steps.find((s) => s.id === activeId) ?? steps[0];

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-steps"
      editable={editable}
    >
      <nav className="dash-steps-bar">
        {steps.map((step) => (
          <button
            key={step.id}
            type="button"
            className={`btn small ${step.id === active?.id ? "primary" : ""}`}
            onClick={() => setLocalStep(step.id)}
          >
            {step.label}
          </button>
        ))}
      </nav>
      <div className="dash-steps-body" style={styles.body}>
        {active?.children?.length
          ? renderWidgetList(active.children, {
              refreshIntervalMs,
              editable: editable ?? false,
              depth: depth + 1,
            })
          : <p className="hint">Нет виджетов шага</p>}
      </div>
    </DashWidgetShell>
  );
}
