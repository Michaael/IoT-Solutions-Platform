import type { SymbolRenderProps } from "../../types/scadaMimic";
import { asBool, asNum, fmtNum, fmtText } from "../utils";
import { selectionOutline } from "./process";
import { PumpCentrifugalSymbol, ValveButterflySymbol } from "./process";

export function TankSphericalSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const level = asNum(values.fillLevel);
  const max = asNum(values.maxLevel) ?? 100;
  const cx = width / 2;
  const cy = height / 2;
  const r = Math.min(width, height) / 2 - 4;
  return (
    <g>
      <circle cx={cx} cy={cy} r={r} fill="var(--bg-elevated)" stroke="#58a6ff" strokeWidth={2} />
      <clipPath id="tank-sphere-clip"><circle cx={cx} cy={cy} r={r - 2} /></clipPath>
      <rect x={cx - r} y={cy + r - (2 * r * (level ?? 0)) / max} width={2 * r} height={(2 * r * (level ?? 0)) / max} fill="#238636" clipPath="url(#tank-sphere-clip)" />
      <text x={cx} y={height - 2} textAnchor="middle" fontSize={9} fill="var(--text-muted)">{fmtNum(level, 0)}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ValveBallSymbol(props: SymbolRenderProps) {
  return <ValveButterflySymbol {...props} />;
}

export function ValveGlobeSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const open = asBool(values.open);
  return (
    <g>
      <line x1={width / 2} y1={0} x2={width / 2} y2={height} stroke="var(--text)" strokeWidth={2} />
      <circle cx={width / 2} cy={height / 2} r={10} fill={open ? "#3fb950" : "#484f58"} stroke="var(--text)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function PumpSubmersibleSymbol(props: SymbolRenderProps) {
  return <PumpCentrifugalSymbol {...props} />;
}

export function CompressorSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={11} fill="var(--text)">C</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function HeatExchangerSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <rect x={4} y={height * 0.2} width={width - 8} height={height * 0.6} rx={8} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <line x1={8} y1={height / 2} x2={width - 8} y2={height / 2} stroke="#58a6ff" strokeWidth={1} strokeDasharray="4 3" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FilterSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <polygon points={`${width / 2},4 ${width - 4},${height - 4} 4,${height - 4}`} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SeparatorSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <ellipse cx={width / 2} cy={height / 2} rx={width / 2 - 4} ry={height / 2 - 6} fill="var(--bg-elevated)" stroke="var(--border)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function MotorSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <circle cx={width / 2} cy={height / 2} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={12} fill="var(--text)">M</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function InverterSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke={active ? "#58a6ff" : "#484f58"} strokeWidth={2} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={10} fill="var(--text)">INV</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function CapacitorSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <line x1={4} y1={height / 2} x2={width / 2 - 4} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <line x1={width / 2 - 4} y1={height * 0.25} x2={width / 2 - 4} y2={height * 0.75} stroke="var(--text)" strokeWidth={3} />
      <line x1={width / 2 + 4} y1={height * 0.25} x2={width / 2 + 4} y2={height * 0.75} stroke="var(--text)" strokeWidth={3} />
      <line x1={width / 2 + 4} y1={height / 2} x2={width - 4} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ReactorSymbol({ width, height, selected }: SymbolRenderProps) {
  return (
    <g>
      <path d={`M4,${height / 2} Q${width / 2},4 ${width - 4},${height / 2} T4,${height / 2}`} fill="none" stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SensorTemperatureSymbol({ width, height, values, selected }: SymbolRenderProps) {
  return (
    <g>
      <circle cx={width / 2} cy={height / 2} r={8} fill="#f85149" opacity={0.2} stroke="#f85149" />
      <text x={width / 2} y={height - 2} textAnchor="middle" fontSize={9}>{fmtNum(values.value, 1, "°")}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function SensorPressureSymbol({ width, height, values, selected }: SymbolRenderProps) {
  return (
    <g>
      <rect width={width} height={height} rx={4} fill="var(--bg-elevated)" stroke="var(--border)" />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fontSize={10}>{fmtNum(values.value, 2, " MPa")}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function AlarmBannerSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <rect width={width} height={height} fill={active ? "#450a0a" : "var(--bg-elevated)"} stroke={active ? "#f85149" : "var(--border)"} />
      <text x={width / 2} y={height / 2 + 4} textAnchor="middle" fill={active ? "#fee2e2" : "var(--text-muted)"} fontSize={11}>{fmtText(values.text, "ALARM")}</text>
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TextMultilineSymbol({ width: _width, height: _height, values, props, selected }: SymbolRenderProps) {
  const lines = String(values.text ?? props.text ?? "Text").split("\\n");
  return (
    <g>
      {lines.map((line, i) => (
        <text key={i} x={0} y={14 + i * 14} fill="var(--text)" fontSize={11}>{line}</text>
      ))}
      {selectionOutline(selected, _width, _height)}
    </g>
  );
}

export function ArrowSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const right = asBool(values.right);
  return (
    <g>
      {right ? (
        <polygon points={`4,${height / 2} ${width - 4},${height / 2 - 6} ${width - 4},${height / 2 + 6}`} fill="#58a6ff" />
      ) : (
        <polygon points={`${width - 4},${height / 2} 4,${height / 2 - 6} 4,${height / 2 + 6}`} fill="#58a6ff" />
      )}
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function DiamondSymbol({ width, height, styleOverrides, selected }: SymbolRenderProps) {
  const cx = width / 2;
  const cy = height / 2;
  return (
    <g>
      <polygon points={`${cx},2 ${width - 2},${cy} ${cx},${height - 2} 2,${cy}`} fill={styleOverrides.fill ?? "transparent"} stroke={styleOverrides.stroke ?? "var(--border)"} strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FlareSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const active = asBool(values.active);
  return (
    <g>
      <line x1={width / 2} y1={height - 4} x2={width / 2} y2={height / 2} stroke="#f0883e" strokeWidth={2} />
      <polygon points={`${width / 2},4 ${width / 2 + 10},${height / 2} ${width / 2 - 10},${height / 2}`} fill={active ? "#f0883e" : "#484f58"} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function MixerSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const running = asBool(values.running);
  return (
    <g>
      <circle cx={width / 2} cy={height / 2} r={Math.min(width, height) / 2 - 2} fill="var(--bg-elevated)" stroke={running ? "#3fb950" : "#484f58"} strokeWidth={2} />
      <line x1={width / 2 - 8} y1={height / 2} x2={width / 2 + 8} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <line x1={width / 2} y1={height / 2 - 8} x2={width / 2} y2={height / 2 + 8} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function TransformerThreeWindingSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <circle cx={cx - 16} cy={height / 2} r={12} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <circle cx={cx} cy={height / 2} r={12} fill="none" stroke="#58a6ff" strokeWidth={2} />
      <circle cx={cx + 16} cy={height / 2} r={12} fill="none" stroke="#58a6ff" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function GroundSymbol({ width, height, selected }: SymbolRenderProps) {
  const cx = width / 2;
  return (
    <g>
      <line x1={cx} y1={0} x2={cx} y2={height / 2} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 12} y1={height / 2 + 4} x2={cx + 12} y2={height / 2 + 4} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 8} y1={height / 2 + 10} x2={cx + 8} y2={height / 2 + 10} stroke="var(--text)" strokeWidth={2} />
      <line x1={cx - 4} y1={height / 2 + 16} x2={cx + 4} y2={height / 2 + 16} stroke="var(--text)" strokeWidth={2} />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function FuseSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const blown = asBool(values.blown);
  return (
    <g>
      <line x1={width / 2} y1={0} x2={width / 2} y2={height} stroke="var(--text)" strokeWidth={2} />
      <rect x={width / 2 - 8} y={height / 2 - 6} width={16} height={12} fill={blown ? "#f85149" : "#d29922"} stroke="var(--text)" />
      {selectionOutline(selected, width, height)}
    </g>
  );
}

export function ContactSymbol({ width, height, values, selected }: SymbolRenderProps) {
  const closed = asBool(values.closed);
  return (
    <g>
      <line x1={width / 2} y1={0} x2={width / 2} y2={height / 3} stroke="var(--text)" strokeWidth={2} />
      <line x1={width / 2} y1={(2 * height) / 3} x2={width / 2} y2={height} stroke="var(--text)" strokeWidth={2} />
      <circle cx={width / 2} cy={height / 3} r={3} fill="var(--text)" />
      <circle cx={width / 2} cy={(2 * height) / 3} r={3} fill="var(--text)" />
      {closed && <line x1={width / 2 - 6} y1={height / 2} x2={width / 2 + 6} y2={height / 2} stroke="#3fb950" strokeWidth={2} />}
      {selectionOutline(selected, width, height)}
    </g>
  );
}
