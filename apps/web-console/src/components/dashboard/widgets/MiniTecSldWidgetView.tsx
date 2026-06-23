import type { MiniTecSldWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

/** Mirrors {@code MiniTecPaths} on the server. */
const P = {
  gpu1: "root.platform.devices.mini-tec-plant.gpu-01",
  gpu2: "root.platform.devices.mini-tec-plant.gpu-02",
  gpu3: "root.platform.devices.mini-tec-plant.gpu-03",
  grpb: "root.platform.devices.mini-tec-plant.grpb",
  rumb: "root.platform.devices.mini-tec-plant.rumb-10kv",
  dgu: "root.platform.devices.mini-tec-plant.dgu",
  load: "root.platform.devices.mini-tec-plant.load-module",
  hub: "root.platform.devices.mini-tec-plant.station-hub",
} as const;

interface MiniTecSldWidgetViewProps {
  widget: MiniTecSldWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

function asBool(raw: unknown): boolean {
  return raw === true || raw === "true" || raw === 1;
}

function asNum(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim() !== "") {
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function fmtKw(raw: unknown): string {
  const n = asNum(raw);
  return n == null ? "— kW" : `${Math.round(n)} kW`;
}

function fmtFlow(raw: unknown): string {
  const n = asNum(raw);
  return n == null ? "—" : `${Math.round(n)} m³/h`;
}

function fmtHz(raw: unknown): string {
  const n = asNum(raw);
  return n == null ? "— Hz" : `${n.toFixed(2)} Hz`;
}

function useMeas(path: string, variable: string, refreshIntervalMs: number) {
  return useBoundVariable(path, variable, "value", refreshIntervalMs).rawValue;
}

interface GenBlockProps {
  x: number;
  y: number;
  label: string;
  ratedKw: number;
  powerRaw: unknown;
  runningRaw: unknown;
}

function GenBlock({ x, y, label, ratedKw, powerRaw, runningRaw }: GenBlockProps) {
  const running = asBool(runningRaw);
  const stroke = running ? "#3fb950" : "#484f58";
  const glow = running ? "rgba(63,185,80,0.35)" : "transparent";
  return (
    <g transform={`translate(${x},${y})`}>
      <line x1="56" y1="88" x2="56" y2="112" stroke="#58a6ff" strokeWidth="3" />
      <rect
        width="112"
        height="84"
        rx="6"
        fill="#161b22"
        stroke={stroke}
        strokeWidth="2.5"
        style={{ filter: running ? `drop-shadow(0 0 6px ${glow})` : undefined }}
      />
      <circle cx="56" cy="22" r="10" fill="none" stroke={stroke} strokeWidth="1.5" />
      <text x="56" y="26" textAnchor="middle" fill={stroke} fontSize="11" fontWeight="700">
        G
      </text>
      <text
        x="56"
        y="44"
        textAnchor="middle"
        fill="#e6edf3"
        fontFamily="Segoe UI, system-ui, sans-serif"
        fontSize="14"
        fontWeight="600"
      >
        {label}
      </text>
      <text
        x="56"
        y="58"
        textAnchor="middle"
        fill="#8b949e"
        fontFamily="Segoe UI, system-ui, sans-serif"
        fontSize="10"
      >
        {ratedKw} kW
      </text>
      <rect x="14" y="64" width="84" height="18" rx="4" fill="#0d1117" stroke="#30363d" />
      <text
        x="56"
        y="77"
        textAnchor="middle"
        fill={running ? "#3fb950" : "#8b949e"}
        fontFamily="ui-monospace, Consolas, monospace"
        fontSize="11"
        fontWeight="600"
      >
        {fmtKw(powerRaw)}
      </text>
      <circle cx="98" cy="12" r="4" fill={running ? "#3fb950" : "#484f58"} />
    </g>
  );
}

function BreakerSymbol({ x, y, closed }: { x: number; y: number; closed: boolean }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <line x1="0" y1="20" x2="0" y2="4" stroke="#e6edf3" strokeWidth="2.5" />
      <line x1="0" y1="36" x2="0" y2="20" stroke="#e6edf3" strokeWidth="2.5" />
      {closed ? (
        <line x1="-8" y1="20" x2="8" y2="20" stroke="#3fb950" strokeWidth="2.5" />
      ) : (
        <line x1="-8" y1="28" x2="8" y2="12" stroke="#f0883e" strokeWidth="2.5" />
      )}
      <line x1="0" y1="36" x2="0" y2="52" stroke="#e6edf3" strokeWidth="2.5" />
    </g>
  );
}

export default function MiniTecSldWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: MiniTecSldWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);

  const gpu1p = useMeas(P.gpu1, "activePowerKw", refreshIntervalMs);
  const gpu2p = useMeas(P.gpu2, "activePowerKw", refreshIntervalMs);
  const gpu3p = useMeas(P.gpu3, "activePowerKw", refreshIntervalMs);
  const gpu1r = useMeas(P.gpu1, "running", refreshIntervalMs);
  const gpu2r = useMeas(P.gpu2, "running", refreshIntervalMs);
  const gpu3r = useMeas(P.gpu3, "running", refreshIntervalMs);

  const gasFlow = useMeas(P.grpb, "gasFlowRate", refreshIntervalMs);
  const fireAlarm = useMeas(P.grpb, "fireAlarm", refreshIntervalMs);
  const grpbOk = !asBool(fireAlarm);

  const dguRun = useMeas(P.dgu, "running", refreshIntervalMs);

  const breakerClosed = useMeas(P.rumb, "breakerClosed", refreshIntervalMs);
  const loadP = useMeas(P.load, "activePowerKw", refreshIntervalMs);

  const island = useMeas(P.hub, "islandMode", refreshIntervalMs);
  const freq = useMeas(P.hub, "gridFrequencyHz", refreshIntervalMs);
  const totalGen = useMeas(P.hub, "totalGenPowerKw", refreshIntervalMs);

  const islandOn = asBool(island);
  const busColor = islandOn ? "#3fb950" : "#58a6ff";

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-mini-tec-sld"
      editable={editable}
    >
      <svg
        className="mini-tec-sld-svg"
        viewBox="0 0 1200 400"
        role="img"
        aria-label="Однолинейная схема Мини-ТЭЦ"
        style={styles.body}
      >
        <defs>
          <linearGradient id="mt-bus" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor={busColor} stopOpacity="0.25" />
            <stop offset="50%" stopColor={busColor} />
            <stop offset="100%" stopColor={busColor} stopOpacity="0.25" />
          </linearGradient>
          <marker id="mt-gas-arr" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 Z" fill="#f0883e" />
          </marker>
          <filter id="mt-glow">
            <feGaussianBlur stdDeviation="2.5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <rect width="1200" height="400" fill="#0d1117" rx="8" />

        {islandOn && (
          <g>
            <rect x="420" y="8" width="360" height="22" rx="4" fill="rgba(63,185,80,0.15)" stroke="#3fb950" />
            <text
              x="600"
              y="23"
              textAnchor="middle"
              fill="#3fb950"
              fontFamily="Segoe UI, system-ui, sans-serif"
              fontSize="12"
              fontWeight="600"
            >
              ОСТРОВНОЙ РЕЖИМ — питание от собственной генерации
            </text>
          </g>
        )}

        {/* Gas tract */}
        <text x="28" y="128" fill="#f0883e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
          Газ
        </text>
        <line
          x1="52"
          y1="122"
          x2="118"
          y2="122"
          stroke="#f0883e"
          strokeWidth="3"
          markerEnd="url(#mt-gas-arr)"
        />

        {/* GRPB */}
        <g transform="translate(120,78)">
          <rect
            width="104"
            height="88"
            rx="6"
            fill="#161b22"
            stroke="#f0883e"
            strokeWidth="2"
          />
          <text
            x="52"
            y="26"
            textAnchor="middle"
            fill="#e6edf3"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="14"
            fontWeight="600"
          >
            ГРПБ
          </text>
          <text
            x="52"
            y="44"
            textAnchor="middle"
            fill="#8b949e"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="10"
          >
            газорегулирующий пункт
          </text>
          <text
            x="52"
            y="62"
            textAnchor="middle"
            fill="#f0883e"
            fontFamily="ui-monospace, Consolas, monospace"
            fontSize="11"
            fontWeight="600"
          >
            {fmtFlow(gasFlow)}
          </text>
          <circle cx="52" cy="78" r="5" fill={grpbOk ? "#3fb950" : "#f85149"} />
        </g>

        <line x1="224" y1="122" x2="248" y2="122" stroke="#58a6ff" strokeWidth="3" />

        {/* 10 kV bus */}
        <rect
          x="248"
          y="114"
          width="680"
          height="12"
          rx="4"
          fill="url(#mt-bus)"
          filter="url(#mt-glow)"
        />
        <text
          x="588"
          y="108"
          textAnchor="middle"
          fill={busColor}
          fontFamily="Segoe UI, system-ui, sans-serif"
          fontSize="11"
          fontWeight="600"
        >
          Сборные шины 10 кВ · {fmtHz(freq)} · ΣP {fmtKw(totalGen)}
        </text>

        <GenBlock x={268} y={24} label="ГПУ-1" ratedKw={1480} powerRaw={gpu1p} runningRaw={gpu1r} />
        <GenBlock x={418} y={24} label="ГПУ-2" ratedKw={1480} powerRaw={gpu2p} runningRaw={gpu2r} />
        <GenBlock x={568} y={24} label="ГПУ-3" ratedKw={1480} powerRaw={gpu3p} runningRaw={gpu3r} />

        {/* DGU branch */}
        <g transform="translate(488,156)">
          <line x1="56" y1="0" x2="56" y2="20" stroke="#58a6ff" strokeWidth="3" />
          <line
            x1="56"
            y1="20"
            x2="56"
            y2="44"
            stroke="#a371f7"
            strokeWidth="2"
            strokeDasharray="5 4"
          />
          <rect
            width="112"
            height="76"
            rx="6"
            fill="#161b22"
            stroke={asBool(dguRun) ? "#a371f7" : "#484f58"}
            strokeWidth="2"
          />
          <text
            x="56"
            y="26"
            textAnchor="middle"
            fill="#e6edf3"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="14"
            fontWeight="600"
          >
            ДГУ
          </text>
          <text
            x="56"
            y="44"
            textAnchor="middle"
            fill="#8b949e"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="10"
          >
            резерв ~500 kW
          </text>
          <text
            x="56"
            y="62"
            textAnchor="middle"
            fill={asBool(dguRun) ? "#a371f7" : "#8b949e"}
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="11"
            fontWeight="600"
          >
            {asBool(dguRun) ? "В работе" : "Резерв"}
          </text>
        </g>

        {/* RUMB + breaker */}
        <g transform="translate(748,72)">
          <line x1="0" y1="50" x2="20" y2="50" stroke="#58a6ff" strokeWidth="3" />
          <BreakerSymbol x={20} y={18} closed={asBool(breakerClosed)} />
          <line x1="20" y1="70" x2="44" y2="70" stroke="#58a6ff" strokeWidth="3" />
          <circle cx="62" cy="70" r="14" fill="none" stroke="#58a6ff" strokeWidth="2" />
          <circle cx="78" cy="70" r="14" fill="none" stroke="#58a6ff" strokeWidth="2" />
          <rect
            x="44"
            y="8"
            width="108"
            height="84"
            rx="6"
            fill="#161b22"
            stroke="#58a6ff"
            strokeWidth="2"
          />
          <text
            x="98"
            y="34"
            textAnchor="middle"
            fill="#e6edf3"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="14"
            fontWeight="600"
          >
            РУМБ
          </text>
          <text
            x="98"
            y="52"
            textAnchor="middle"
            fill="#8b949e"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="10"
          >
            10 / 0,4 кВ
          </text>
          <text
            x="98"
            y="72"
            textAnchor="middle"
            fill={asBool(breakerClosed) ? "#3fb950" : "#f0883e"}
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="10"
            fontWeight="600"
          >
            {asBool(breakerClosed) ? "ВК включён" : "ВК отключён"}
          </text>
        </g>

        {/* Load */}
        <g transform="translate(920,56)">
          <line x1="0" y1="64" x2="24" y2="64" stroke="#58a6ff" strokeWidth="3" />
          <rect
            x="24"
            y="12"
            width="128"
            height="104"
            rx="6"
            fill="#161b22"
            stroke="#d29922"
            strokeWidth="2"
          />
          <text
            x="88"
            y="40"
            textAnchor="middle"
            fill="#e6edf3"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="14"
            fontWeight="600"
          >
            Нагрузка
          </text>
          <text
            x="88"
            y="58"
            textAnchor="middle"
            fill="#8b949e"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="10"
          >
            нагрузочный модуль
          </text>
          <text
            x="88"
            y="78"
            textAnchor="middle"
            fill="#d29922"
            fontFamily="ui-monospace, Consolas, monospace"
            fontSize="13"
            fontWeight="700"
          >
            {fmtKw(loadP)}
          </text>
          <text
            x="88"
            y="98"
            textAnchor="middle"
            fill="#8b949e"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="9"
          >
            потребители
          </text>
        </g>

        {/* Legend */}
        <g transform="translate(24,300)">
          <rect width="1152" height="84" rx="6" fill="#161b22" stroke="#30363d" />
          <text
            x="20"
            y="24"
            fill="#e6edf3"
            fontFamily="Segoe UI, system-ui, sans-serif"
            fontSize="12"
            fontWeight="600"
          >
            Условные обозначения
          </text>
          <circle cx="36" cy="46" r="5" fill="#3fb950" />
          <text x="48" y="50" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            генерация / в норме
          </text>
          <circle cx="210" cy="46" r="5" fill="#f0883e" />
          <text x="222" y="50" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            газовый тракт
          </text>
          <circle cx="360" cy="46" r="5" fill="#a371f7" />
          <text x="372" y="50" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            резерв ДГУ
          </text>
          <line x1="36" y1="68" x2="76" y2="68" stroke="#58a6ff" strokeWidth="3" />
          <text x="86" y="72" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            силовая шина 10 кВ
          </text>
          <rect x="520" y="36" width="14" height="14" rx="2" fill="none" stroke="#d29922" strokeWidth="2" />
          <text x="542" y="48" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            нагрузка
          </text>
          <text x="680" y="48" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            Номинал: 3×1480 kW ГПУ + резерв ДГУ
          </text>
          <text x="680" y="68" fill="#8b949e" fontFamily="Segoe UI, system-ui, sans-serif" fontSize="11">
            Значения обновляются в реальном времени
          </text>
        </g>
      </svg>
    </DashWidgetShell>
  );
}
