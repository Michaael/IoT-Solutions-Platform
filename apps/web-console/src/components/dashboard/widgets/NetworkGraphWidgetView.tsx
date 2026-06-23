import { useMemo } from "react";
import type { NetworkGraphWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { parseDemoPreview } from "../widgetDemoPreview";

interface NetworkGraphWidgetViewProps {
  widget: NetworkGraphWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function NetworkGraphWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: NetworkGraphWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const nodesVar = useBoundVariable(
    objectPath,
    widget.nodesVariable ?? "nodes",
    widget.valueField,
    refreshIntervalMs
  );
  const edgesVar = useBoundVariable(
    objectPath,
    widget.edgesVariable ?? "edges",
    widget.valueField,
    refreshIntervalMs
  );

  const summary = useMemo(() => {
    const nodes = nodesVar.variable?.value?.rows?.length ?? 0;
    const edges = edgesVar.variable?.value?.rows?.length ?? 0;
    const labelField = widget.labelField ?? "name";
    const labels = (nodesVar.variable?.value?.rows ?? [])
      .slice(0, 8)
      .map((row) => String(readFieldValue(row, labelField) ?? "—"));
    return { nodes, edges, labels };
  }, [nodesVar.variable, edgesVar.variable, widget.labelField]);

  const demo = editable
    ? parseDemoPreview<{ nodes: string[]; edges: number }>(widget.demoPreviewJson)
    : null;
  const isDemo = Boolean(editable && summary.nodes === 0 && demo);
  const display = isDemo
    ? { nodes: demo!.nodes.length, edges: demo!.edges, labels: demo!.nodes }
    : summary;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-network"
      editable={editable}
      demo={isDemo}
    >
      <p style={styles.body}>
        Узлов: {display.nodes}, рёбер: {display.edges}
      </p>
      <ul className="dash-network-list">
        {display.labels.map((label, i) => (
          <li key={i}>{label}</li>
        ))}
      </ul>
    </DashWidgetShell>
  );
}
