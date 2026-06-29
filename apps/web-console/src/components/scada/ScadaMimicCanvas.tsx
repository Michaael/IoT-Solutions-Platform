import { useMemo } from "react";
import type { MimicConnection, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import { applyFormatRules } from "../../scada/formatRules";
import { connectionPolylinePoints } from "../../scada/connectionRouting";
import { getSymbol, getSymbolRender, symbolSize } from "../../scada/symbols/registry";
import { asBool } from "../../scada/utils";

export interface ScadaMimicCanvasProps {
  document: ScadaMimicDocument;
  valuesByElementId: Record<string, Record<string, unknown>>;
  valuesByConnectionId: Record<string, Record<string, unknown>>;
  editable?: boolean;
  editMode?: boolean;
  selectedIds?: Set<string>;
  selectedConnectionId?: string | null;
  onSelectElement?: (id: string, additive?: boolean) => void;
  onSelectConnection?: (id: string | null) => void;
  onElementClick?: (element: MimicElement) => void;
  onCanvasClick?: (x: number, y: number) => void;
  onElementDrag?: (id: string, x: number, y: number) => void;
  viewTransform?: { panX: number; panY: number; zoom: number };
}

function ConnectionPath({
  connection,
  elements,
  values,
  selected,
  onSelect,
}: {
  connection: MimicConnection;
  elements: MimicElement[];
  values: Record<string, unknown>;
  selected?: boolean;
  onSelect?: () => void;
}) {
  const points = connectionPolylinePoints(connection, elements);
  if (points.length < 2) return null;
  const d = points.map((p: { x: number; y: number }, i: number) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
  const flowing = asBool(values.flowing);
  const alarm = asBool(values.alarm);
  const stroke = alarm ? "#f85149" : flowing ? "#58a6ff" : connection.style?.stroke ?? "#8b949e";
  return (
    <path
      d={d}
      fill="none"
      stroke={stroke}
      strokeWidth={connection.style?.strokeWidth ?? 3}
      strokeDasharray={connection.style?.dash}
      onClick={(e) => {
        e.stopPropagation();
        onSelect?.();
      }}
      style={{ cursor: onSelect ? "pointer" : undefined }}
      opacity={selected ? 1 : 0.95}
    />
  );
}

export default function ScadaMimicCanvas({
  document,
  valuesByElementId,
  valuesByConnectionId,
  editable,
  editMode,
  selectedIds,
  selectedConnectionId,
  onSelectElement,
  onSelectConnection,
  onElementClick,
  onCanvasClick,
  onElementDrag,
  viewTransform,
}: ScadaMimicCanvasProps) {
  const visibleLayers = useMemo(
    () => new Set(document.layers.filter((l) => l.visible).map((l) => l.id)),
    [document.layers]
  );

  const elements = document.elements.filter((el) => visibleLayers.has(el.layerId));
  const connections = document.connections.filter((c) => visibleLayers.has(c.layerId));

  const handleSvgClick = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!editMode || !onCanvasClick) return;
    const svg = e.currentTarget;
    const pt = svg.createSVGPoint();
    pt.x = e.clientX;
    pt.y = e.clientY;
    const ctm = svg.getScreenCTM();
    if (!ctm) return;
    const local = pt.matrixTransform(ctm.inverse());
    onCanvasClick(local.x, local.y);
  };

  return (
    <svg
      className="scada-mimic-svg"
      viewBox={`0 0 ${document.width} ${document.height}`}
      width="100%"
      height="100%"
      preserveAspectRatio="xMidYMid meet"
      style={{
        background: document.background ?? "var(--bg)",
        transform: viewTransform
          ? `translate(${viewTransform.panX}px, ${viewTransform.panY}px) scale(${viewTransform.zoom})`
          : undefined,
        transformOrigin: "0 0",
      }}
      onClick={handleSvgClick}
    >
      {document.grid?.visible && editMode && (
        <defs>
          <pattern id="scada-grid" width={document.grid.size} height={document.grid.size} patternUnits="userSpaceOnUse">
            <path d={`M ${document.grid.size} 0 L 0 0 0 ${document.grid.size}`} fill="none" stroke="var(--border)" strokeWidth={0.5} opacity={0.4} />
          </pattern>
        </defs>
      )}
      {document.grid?.visible && editMode && (
        <rect width={document.width} height={document.height} fill="url(#scada-grid)" pointerEvents="none" />
      )}

      {connections.map((conn) => (
        <ConnectionPath
          key={conn.id}
          connection={conn}
          elements={document.elements}
          values={valuesByConnectionId[conn.id] ?? {}}
          selected={selectedConnectionId === conn.id}
          onSelect={editMode ? () => onSelectConnection?.(conn.id) : undefined}
        />
      ))}

      {elements.map((el) => {
        const symbol = getSymbol(el.symbolId);
        const Render = getSymbolRender(el.symbolId);
        if (!symbol || !Render) return null;
        const { width, height } = symbolSize(el);
        const values = valuesByElementId[el.id] ?? {};
        const styleOverrides = applyFormatRules(values, el.formatRules);
        const selected = selectedIds?.has(el.id);
        const transform = `translate(${el.x},${el.y}) rotate(${el.rotation ?? 0} ${width / 2} ${height / 2})`;
        return (
          <g
            key={el.id}
            transform={transform}
            onClick={(e) => {
              e.stopPropagation();
              if (editMode) {
                onSelectElement?.(el.id, e.shiftKey);
              } else if (onElementClick) {
                onElementClick(el);
              }
            }}
            onMouseDown={(e) => {
              if (!editMode || !onElementDrag || e.button !== 0) return;
              e.stopPropagation();
              onSelectElement?.(el.id);
              const startX = e.clientX;
              const startY = e.clientY;
              const origX = el.x;
              const origY = el.y;
              const svg = (e.currentTarget.ownerSVGElement as SVGSVGElement | null);
              const move = (ev: MouseEvent) => {
                if (!svg) return;
                const scale = svg.clientWidth / document.width;
                const dx = (ev.clientX - startX) / scale;
                const dy = (ev.clientY - startY) / scale;
                onElementDrag(el.id, origX + dx, origY + dy);
              };
              const up = () => {
                window.removeEventListener("mousemove", move);
                window.removeEventListener("mouseup", up);
              };
              window.addEventListener("mousemove", move);
              window.addEventListener("mouseup", up);
            }}
            style={{ cursor: editMode ? "move" : onElementClick ? "pointer" : undefined }}
          >
            <Render
              width={width}
              height={height}
              values={values}
              props={el.props ?? {}}
              styleOverrides={styleOverrides}
              editable={editable}
              selected={selected}
            />
          </g>
        );
      })}
    </svg>
  );
}
