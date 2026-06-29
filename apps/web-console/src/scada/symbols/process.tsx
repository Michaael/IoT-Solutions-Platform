import type { SymbolRenderProps } from "../../types/scadaMimic";
import { asBool, asNum, clamp01, demoVal, fmtNum, fmtText } from "../utils";

export function selectionOutline(selected?: boolean, width?: number, height?: number) {
  if (!selected || width == null || height == null) return null;
  return (
    <rect
      x={-4}
      y={-4}
      width={width + 8}
      height={height + 8}
      fill="none"
      stroke="var(--accent)"
      strokeWidth={2}
      strokeDasharray="4 2"
      pointerEvents="none"
    />
  );
}

export function statusColor(open: boolean, alarm?: boolean): string {
  if (alarm) return "#f85149";
  return open ? "#3fb950" : "#484f58";
}

export function TankVerticalSymbol({ width, height, values, props, styleOverrides, selected, onClick }: SymbolRenderProps) {
  const level = asNum(demoVal(values, props, "fillLevel"));
  const max = asNum(props.maxLevel) ?? asNum(values.maxLevel) ?? 100;
  const rate = asNum(demoVal(values, props, "rate"));
  const fillRatio = level != null && max > 0 ? clamp01(level / max) : 0;
  const stroke = styleOverrides.stroke ?? fmtText(props.tankStroke, "#58a6ff");
  const liquidColor = fmtText(props.liquidColor, "#238636");
  const tankH = height - 40;
  const tankY = 20;
  return (
    <g onClick={onClick} style={{ cursor: onClick ? "pointer" : undefined }}>
      <rect x={width * 0.2} y={tankY} width={width * 0.6} height={tankH} rx={8} fill="var(--bg-elevated)" stroke={stroke} strokeWidth={2} />
      <rect x={width * 0.22} y={tankY + tankH * (1 - fillRatio)} width={width * 0.56} height={tankH * fillRatio} fill={liquidColor} opacity={0.85} />
      <text x={width / 2} y={14} textAnchor="middle" fill="var(--text)" fontSize={12} fontWeight={600}>{fmtText(props.label ?? props.tankId, "T")}</text>
      <text x={width / 2} y={height - 4} textAnchor="middle" fill="var(--text-muted)" fontSize={10}>{fmtNum(level, 0)} / {fmtNum(max, 0)}</text>
      {rate != null && (
        <text x={width - 6} y={tankY + 12} textAnchor="end" fill={rate >= 0 ? "#3fb950" : "#58a6ff"} fontSize={9}>{rate >= 0 ? "↑" : "↓"}{Math.abs(Math.round(rate))}</text>
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TankHorizontalSymbol(props: SymbolRenderProps) {
  return <TankVerticalSymbol {...props} width={props.height} height={props.width} />;
}

export function ValveButterflySymbol({ width, height, values, props, styleOverrides, selected, onClick }: SymbolRenderProps) {
  const open = asBool(demoVal(values, props, "open"));
  const intermediate = asBool(values.intermediate);
  const stroke = styleOverrides.stroke ?? statusColor(open || intermediate);
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g onClick={onClick} style={{ cursor: onClick ? "pointer" : undefined }}>
      <line x1={cx} y1={0} x2={cx} y2={cy - 10} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx} y1={cy + 10} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      <circle cx={cx} cy={cy} r={12} fill="var(--bg-elevated)" stroke={stroke} strokeWidth={2} />
      {open ? (
        <line x1={cx - 8} y1={cy} x2={cx + 8} y2={cy} stroke={stroke} strokeWidth={2.5} />
      ) : intermediate ? (
        <line x1={cx - 6} y1={cy + 4} x2={cx + 6} y2={cy - 4} stroke="#f0883e" strokeWidth={2.5} />
      ) : (
        <line x1={cx - 8} y1={cy + 6} x2={cx + 8} y2={cy - 6} stroke={stroke} strokeWidth={2.5} />
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ValveGateSymbol(props: SymbolRenderProps) {
  const open = asBool(props.values.open);
  const cx = props.width / 2;
  return (
    <g onClick={props.onClick}>
      <line x1={cx} y1={0} x2={cx} y2={props.height} stroke="var(--text)" strokeWidth={2} />
      <polygon points={`${cx - 10},${props.height / 2 - 8} ${cx + 10},${props.height / 2 - 8} ${cx + 10},${props.height / 2 + 8} ${cx - 10},${props.height / 2 + 8}`} fill={open ? "#3fb950" : "#484f58"} stroke="var(--text)" />
      {selectionOutline(props.selected, props.width, props.height)}
    </g>
  );
}

export function ValveCheckSymbol(props: SymbolRenderProps) {
  const open = asBool(props.values.open);
  const cx = props.width / 2;
  const cy = props.height / 2;
  return (
    <g onClick={props.onClick}>
      <circle cx={cx} cy={cy} r={14} fill="var(--bg-elevated)" stroke={open ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <polygon points={`${cx - 6},${cy} ${cx + 2},${cy - 8} ${cx + 2},${cy + 8}`} fill={open ? "#3fb950" : "#484f58"} />
      {selectionOutline(props.selected, props.width, props.height)}
    </g>
  );
}

export function PumpCentrifugalSymbol({ width, height, values, props, selected, onClick }: SymbolRenderProps) {
  const running = asBool(demoVal(values, props, "running"));
  const fault = asBool(demoVal(values, props, "fault"));
  const stroke = fault ? "#f85149" : running ? "#3fb950" : "#484f58";
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g onClick={onClick}>
      <circle cx={cx} cy={cy} r={Math.min(width, height) / 2 - 4} fill="var(--bg-elevated)" stroke={stroke} strokeWidth={2.5} />
      <polygon points={`${cx},${cy - 10} ${cx + 10},${cy + 6} ${cx - 10},${cy + 6}`} fill={stroke} />
      <text x={cx} y={height - 2} textAnchor="middle" fill="var(--text-muted)" fontSize={9}>{fmtText(props.label, "P")}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PumpPositiveSymbol(props: SymbolRenderProps) {
  return <PumpCentrifugalSymbol {...props} />;
}

export function PipeSegmentSymbol({ width, height, values, props, styleOverrides, selected }: SymbolRenderProps) {
  const flowing = asBool(demoVal(values, props, "flowing"));
  const stroke = styleOverrides.stroke ?? (flowing ? "#58a6ff" : "#8b949e");
  return (
    <g>
      <line x1={0} y1={height / 2} x2={width} y2={height / 2} stroke={stroke} strokeWidth={4} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PipeJunctionSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <circle cx={cx} cy={cy} r={6} fill="#8b949e" />
      <line x1={0} y1={cy} x2={width} y2={cy} stroke="#8b949e" strokeWidth={3} />
      <line x1={cx} y1={0} x2={cx} y2={height} stroke="#8b949e" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PipeTeeSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <line x1={0} y1={cy} x2={width} y2={cy} stroke="#8b949e" strokeWidth={3} />
      <line x1={cx} y1={cy} x2={cx} y2={height} stroke="#8b949e" strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SensorIndicatorSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const raw = demoVal(values, props, "state");
  const state =
    raw === true || raw === "true" || raw === 1
      ? "alarm"
      : raw === false || raw === "false" || raw === 0
        ? "ok"
        : fmtText(raw, "ok");
  const color = state === "alarm" ? "#f85149" : state === "warning" ? "#d29922" : "#3fb950";
  return (
    <g>
      <circle cx={width / 2} cy={height / 2} r={Math.min(width, height) / 2 - 2} fill={color} stroke="var(--border)" strokeWidth={1} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SensorGaugeInlineSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const value = asNum(values.value);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fill="var(--text)" fontSize={11}>{fmtNum(value, 1)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DataBlockSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const lines = [
    fmtText(values.line1 ?? props.line1, "—"),
    fmtText(values.line2 ?? props.line2, ""),
    fmtText(values.line3 ?? props.line3, ""),
    fmtText(values.line4 ?? props.line4, ""),
  ].filter(Boolean);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" />
      {lines.map((line, i) => (
        <text key={i} x={6} y={16 + i * 14} fill="var(--text)" fontSize={10} fontFamily="ui-monospace, Consolas, monospace">{line}</text>
      ))}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PipelineTrackSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const segments = Math.max(4, Math.floor(Number(props.segments) || 20));
  const segW = width / segments;
  return (
    <g>
      {Array.from({ length: segments }, (_, i) => {
        const status = fmtText(demoVal(values, props, `seg${i}`), "ok");
        const fill = status === "alarm" ? "#f85149" : status === "off" ? "#484f58" : "#238636";
        return <rect key={i} x={i * segW + 1} y={4} width={segW - 2} height={height - 8} fill={fill} rx={2} />;
      })}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LabelSymbol({ width: _width, height, values, props, styleOverrides, selected }: SymbolRenderProps) {
  const text = fmtText(values.text ?? props.text, "Label");
  return (
    <g>
      <text x={0} y={height / 2} fill={styleOverrides.color ?? "var(--text)"} fontSize={Number(props.fontSize) || 12}>{text}</text>
      {selectionOutline(selected, _width, height)}
    </g>
  );
}

export function ValueBadgeSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const value = demoVal(values, props, "value");
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg)" stroke="var(--border)" />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fill="var(--success)" fontSize={11} fontWeight={600}>{fmtNum(value, Number(props.decimals) || 0, fmtText(props.unit, ""))}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function StatusArrowSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const up = asBool(demoVal(values, props, "up"));
  const color = up ? "#3fb950" : "#58a6ff";
  const cy = height / 2;
  return (
    <g>
      {up ? (
        <polygon points={`${width / 2},4 ${width - 4},${cy + 8} 4,${cy + 8}`} fill={color} />
      ) : (
        <polygon points={`${width / 2},${height - 4} ${width - 4},${cy - 8} 4,${cy - 8}`} fill={color} />
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function RectSymbol({ width, height, styleOverrides, selected }: SymbolRenderProps) {
  return (
    <g>
      <rect width={width} height={height} rx={4} fill={styleOverrides.fill ?? "transparent"} stroke={styleOverrides.stroke ?? "var(--border)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function EllipseSymbol({ width, height, styleOverrides, selected }: SymbolRenderProps) {
  return (
    <g>
      <ellipse cx={width / 2} cy={height / 2} rx={width / 2 - 2} ry={height / 2 - 2} fill={styleOverrides.fill ?? "transparent"} stroke={styleOverrides.stroke ?? "var(--border)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TableEmbeddedSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const rows = Math.min(16, Math.max(1, Number(props.rows) || 4));
  const cols = Math.min(4, Math.max(1, Number(props.cols) || 2));
  const rowH = height / (rows + 1);
  const colW = width / cols;
  return (
    <g>
      <rect width={width} height={height} fill="var(--bg-elevated)" stroke="var(--border)" />
      {Array.from({ length: rows }, (_, r) =>
        Array.from({ length: cols }, (_, c) => (
          <text key={`${r}-${c}`} x={c * colW + 4} y={(r + 1) * rowH + rowH / 2} fill="var(--text)" fontSize={9}>{fmtText(demoVal(values, props, `r${r}c${c}`), "—")}</text>
        ))
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}
