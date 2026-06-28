import { useEffect, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import cytoscape, { type Core, type LayoutOptions, type StylesheetStyle } from "cytoscape";
import type { NetworkGraphWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { parseDemoPreview } from "../widgetDemoPreview";
import {
  type DemoNetworkGraphPreview,
  type NetworkGraphFieldConfig,
  type NetworkGraphLayout,
  parseDemoNetworkGraphPreview,
  parseNetworkGraphData,
  toCytoscapeElements,
} from "../../../utils/networkGraphData";

interface NetworkGraphWidgetViewProps {
  widget: NetworkGraphWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

const CYTOSCAPE_STYLE: StylesheetStyle[] = [
  {
    selector: "node",
    style: {
      label: "data(label)",
      "text-valign": "center",
      "text-halign": "center",
      "background-color": "#2f81f7",
      color: "#e8eaed",
      "font-size": "10px",
      "text-wrap": "wrap",
      "text-max-width": "72px",
      width: 40,
      height: 40,
    },
  },
  {
    selector: "edge",
    style: {
      width: 2,
      "line-color": "#6b7280",
      "target-arrow-color": "#6b7280",
      "target-arrow-shape": "triangle",
      "curve-style": "bezier",
    },
  },
];

function layoutOptions(layout: NetworkGraphLayout): LayoutOptions {
  if (layout === "cose") {
    return {
      name: "cose",
      animate: false,
      nodeRepulsion: 8000,
      idealEdgeLength: 80,
      padding: 24,
    } as LayoutOptions;
  }
  return {
    name: layout,
    animate: false,
    padding: 24,
  } as LayoutOptions;
}

export default function NetworkGraphWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: NetworkGraphWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);

  const fieldConfig: NetworkGraphFieldConfig = useMemo(
    () => ({
      labelField: widget.labelField,
      idField: widget.idField,
      edgeFromField: widget.edgeFromField,
      edgeToField: widget.edgeToField,
    }),
    [widget.labelField, widget.idField, widget.edgeFromField, widget.edgeToField]
  );

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

  const liveData = useMemo(
    () =>
      parseNetworkGraphData(
        nodesVar.variable?.value?.rows as Record<string, unknown>[] | undefined,
        edgesVar.variable?.value?.rows as Record<string, unknown>[] | undefined,
        fieldConfig
      ),
    [nodesVar.variable, edgesVar.variable, fieldConfig]
  );

  const demoPreview = editable
    ? parseDemoPreview<DemoNetworkGraphPreview>(widget.demoPreviewJson)
    : null;
  const demoData = useMemo(
    () => (demoPreview ? parseDemoNetworkGraphPreview(demoPreview) : null),
    [demoPreview]
  );

  const isDemo = Boolean(editable && liveData.nodes.length === 0 && demoData);
  const graphData = isDemo ? demoData! : liveData;
  const layout = (widget.layout ?? "cose") as NetworkGraphLayout;
  const elements = useMemo(() => toCytoscapeElements(graphData), [graphData]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const cy = cytoscape({
      container,
      elements: [],
      style: CYTOSCAPE_STYLE,
      minZoom: 0.4,
      maxZoom: 2.5,
      wheelSensitivity: 0.2,
      userZoomingEnabled: !editable,
      userPanningEnabled: !editable,
      boxSelectionEnabled: false,
    });
    cyRef.current = cy;

    const resizeObserver = new ResizeObserver(() => {
      cy.resize();
      cy.fit(undefined, 24);
    });
    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      cy.destroy();
      cyRef.current = null;
    };
  }, [editable]);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;

    cy.batch(() => {
      cy.elements().remove();
      if (elements.length > 0) {
        cy.add(elements);
      }
    });

    if (elements.length === 0) {
      return;
    }

    const layoutRun = cy.layout(layoutOptions(layout));
    layoutRun.run();
    cy.fit(undefined, 24);
  }, [elements, layout]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-network"
      editable={editable}
      demo={isDemo}
      rootStyle={{ display: "flex", flexDirection: "column", minHeight: 0 }}
      footer={t("view.networkGraphStats", {
        nodes: graphData.nodes.length,
        edges: graphData.edges.length,
      })}
    >
      {graphData.nodes.length === 0 ? (
        <p className="dash-widget-meta" style={styles.meta}>
          {t("view.networkGraphEmpty")}
        </p>
      ) : (
        <div
          ref={containerRef}
          className="dash-network-graph-canvas"
          aria-label={t("view.networkGraphCanvas")}
        />
      )}
    </DashWidgetShell>
  );
}
