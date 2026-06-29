import type { SymbolRenderProps } from "../../types/scadaMimic";
import { asBool, asNum, fmtNum, fmtText } from "../utils";
import { selectionOutline } from "./process";

function fmtKwLocal(raw: unknown): string {
  const n = asNum(raw);
  return n == null ? "— kW" : `${Math.round(n)} kW`;
}

export function GenBlockSymbol({ width, height, values, props, styleOverrides, selected, onClick }: SymbolRenderProps) {
  const running = asBool(values.running);
  const stroke = styleOverrides.stroke ?? (running ? "#3fb950" : "#484f58");
  const w = width;
  const h = height - 28;
  return (
    <g onClick={onClick}>
      <line x1={w / 2} y1={h} x2={w / 2} y2={height} stroke="#58a6ff" strokeWidth={3} />
      <rect width={w} height={h} rx={6} fill="var(--bg-elevated)" stroke={stroke} strokeWidth={2.5} />
      <circle cx={w / 2} cy={16} r={10} fill="none" stroke={stroke} strokeWidth={1.5} />
      <text x={w / 2} y={20} textAnchor="middle" fill={stroke} fontSize={11} fontWeight={700}>G</text>
      <text x={w / 2} y={38} textAnchor="middle" fill="var(--text)" fontSize={13} fontWeight={600}>{fmtText(props.label, "GEN")}</text>
      <text x={w / 2} y={52} textAnchor="middle" fill="var(--text-muted)" fontSize={10}>{fmtNum(props.ratedKw, 0, " kW")}</text>
      <rect x={w * 0.12} y={h - 22} width={w * 0.76} height={18} rx={4} fill="var(--bg)" stroke="var(--border)" />
      <text x={w / 2} y={h - 9} textAnchor="middle" fill={running ? "var(--success)" : "var(--text-muted)"} fontSize={11} fontWeight={600}>{fmtKwLocal(values.power)}</text>
      <circle cx={w - 10} cy={10} r={4} fill={running ? "#3fb950" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BreakerSymbol({ width, height, values, selected, onClick }: SymbolRenderProps) {
  const closed = asBool(values.closed);
  const cx = width / 2;
  return (
    <g onClick={onClick}>
      <line x1={cx} y1={0} x2={cx} y2={height * 0.35} stroke="var(--text)" strokeWidth={2.5} />
      <line x1={cx} y1={height * 0.65} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2.5} />
      {closed ? (
        <line x1={cx - 8} y1={height / 2} x2={cx + 8} y2={height / 2} stroke="#3fb950" strokeWidth={2.5} />
      ) : (
        <line x1={cx - 8} y1={height / 2 + 6} x2={cx + 8} y2={height / 2 - 6} stroke="#f0883e" strokeWidth={2.5} />
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DisconnectorSymbol({ width, height, values, selected, onClick }: SymbolRenderProps) {
  const closed = asBool(values.closed);
  const cx = width / 2;
  return (
    <g onClick={onClick}>
      <line x1={cx} y1={0} x2={cx} y2={height} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 10} y1={height / 2} x2={cx + 10} y2={height / 2} stroke={closed ? "#3fb950" : "#f0883e"} strokeWidth={3} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BusbarHorizontalSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const energized = asBool(values.energized);
  return (
    <g>
      <rect x={0} y={height / 2 - 3} width={width} height={6} fill={energized ? "#58a6ff" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function BusbarVerticalSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const energized = asBool(values.energized);
  return (
    <g>
      <rect x={width / 2 - 3} y={0} width={6} height={height} fill={energized ? "#58a6ff" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TransformerTwoWindingSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const loaded = asBool(values.loaded);
  const cx = width / 2;
  return (
    <g>
      <circle cx={cx - 14} cy={height / 2} r={14} fill="none" stroke={loaded ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      <circle cx={cx + 14} cy={height / 2} r={14} fill="none" stroke={loaded ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LoadBlockSymbol({ width, height, values, props, selected }: SymbolRenderProps) {
  const power = asNum(values.power);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      <text x={width / 2} y={height / 2 - 4} textAnchor="middle" fill="var(--text)" fontSize={11}>{fmtText(props.label, "Load")}</text>
      <text x={width / 2} y={height / 2 + 12} textAnchor="middle" fill="var(--success)" fontSize={10}>{fmtKwLocal(power)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function MeterSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const value = asNum(values.value);
  return (
    <g>
      <circle cx={width / 2} cy={height / 2} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fill="var(--text)" fontSize={10}>M</text>
      <text x={width / 2} y={height - 2} textAnchor="middle" fill="var(--text-muted)" fontSize={9}>{fmtNum(value, 1)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function LineFeederSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const flowing = asBool(values.flowing);
  const power = asNum(values.power);
  return (
    <g>
      <line x1={0} y1={height / 2} x2={width} y2={height / 2} stroke={flowing ? "#58a6ff" : "#484f58"} strokeWidth={3} />
      <text x={width / 2} y={height / 2 - 6} textAnchor="middle" fill="var(--text-muted)" fontSize={9}>{fmtKwLocal(power)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}
