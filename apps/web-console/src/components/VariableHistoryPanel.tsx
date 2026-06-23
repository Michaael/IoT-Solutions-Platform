import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { downloadVariableHistoryExport } from "../api";
import {
  type HistoryRange,
  historyRangeFrom,
  useVariableHistory,
} from "../hooks/useVariableHistory";

interface VariableHistoryPanelProps {
  objectPath: string;
  variableName: string;
  /** Numeric schema fields available for history (from variable definition). */
  fields?: string[];
  refreshIntervalMs?: number;
}

export default function VariableHistoryPanel({
  objectPath,
  variableName,
  fields = ["value"],
  refreshIntervalMs = 15_000,
}: VariableHistoryPanelProps) {
  const { t } = useTranslation("inspector");
  const rangeOptions: { id: HistoryRange; labelKey: string }[] = [
    { id: "1h", labelKey: "variables.historyPanel.range1h" },
    { id: "6h", labelKey: "variables.historyPanel.range6h" },
    { id: "24h", labelKey: "variables.historyPanel.range24h" },
    { id: "7d", labelKey: "variables.historyPanel.range7d" },
    { id: "all", labelKey: "variables.historyPanel.rangeAll" },
  ];
  const [range, setRange] = useState<HistoryRange>("24h");
  const [field, setField] = useState(fields[0] ?? "value");
  const [exportError, setExportError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<"csv" | "json" | null>(null);

  useEffect(() => {
    if (!fields.includes(field)) {
      setField(fields[0] ?? "value");
    }
  }, [fields, field]);

  const { points, stats, isLoading, isError, error, aggregated, bucket } = useVariableHistory(
    objectPath,
    variableName,
    {
      field,
      range,
      limit: 1000,
      refreshIntervalMs,
    }
  );

  const exportHistory = async (format: "csv" | "json") => {
    setExportError(null);
    setExporting(format);
    try {
      const from = historyRangeFrom(range);
      await downloadVariableHistoryExport(objectPath, variableName, {
        format,
        field,
        from,
        to: new Date().toISOString(),
        limit: 10_000,
      });
    } catch (err) {
      setExportError(String(err));
    } finally {
      setExporting(null);
    }
  };

  return (
    <div className="variable-history-panel">
      <div className="variable-history-toolbar">
        <div className="variable-history-stats">
          {stats.latest != null ? (
            <>
              <span className="variable-history-latest">{stats.latest.toFixed(2)}</span>
              {stats.min != null && stats.max != null && (
                <span className="variable-history-range">
                  min {stats.min.toFixed(2)} · max {stats.max.toFixed(2)}
                  {aggregated && bucket && (
                    <span className="variable-history-aggregate-hint"> · avg/{bucket}</span>
                  )}
                </span>
              )}
            </>
          ) : (
            <span className="hint">{t("variables.historyPanel.noData")}</span>
          )}
        </div>
        <div className="variable-history-controls">
          {fields.length > 1 && (
            <label className="variable-history-field-select">
              <span className="sr-only">{t("variables.historyPanel.field")}</span>
              <select value={field} onChange={(e) => setField(e.target.value)}>
                {fields.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="variable-history-ranges">
            {rangeOptions.map((option) => (
              <button
                key={option.id}
                type="button"
                className={`btn tiny ${range === option.id ? "primary" : ""}`}
                onClick={() => setRange(option.id)}
              >
                {t(option.labelKey)}
              </button>
            ))}
          </div>
          <div className="variable-history-export">
            <button
              type="button"
              className="btn tiny"
              disabled={exporting != null}
              onClick={() => exportHistory("csv")}
            >
              {exporting === "csv" ? "…" : "CSV"}
            </button>
            <button
              type="button"
              className="btn tiny"
              disabled={exporting != null}
              onClick={() => exportHistory("json")}
            >
              {exporting === "json" ? "…" : "JSON"}
            </button>
          </div>
        </div>
      </div>

      {exportError && <p className="hint error variable-history-export-error">{exportError}</p>}

      <div className="variable-history-chart">
        {isLoading && points.length === 0 ? (
          <p className="hint">{t("variables.historyPanel.loading")}</p>
        ) : isError ? (
          <p className="hint error">{(error as Error).message}</p>
        ) : points.length < 2 ? (
          <p className="hint">{t("variables.historyPanel.notEnoughPoints")}</p>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={points}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
              <XAxis
                dataKey="time"
                tick={{ fontSize: 10 }}
                minTickGap={24}
                interval="preserveStartEnd"
              />
              <YAxis tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
              <Tooltip
                contentStyle={{
                  background: "var(--bg-elevated)",
                  border: "1px solid var(--border)",
                  fontSize: "0.8rem",
                }}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#2f81f7"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
