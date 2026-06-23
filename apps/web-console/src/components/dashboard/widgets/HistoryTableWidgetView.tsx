import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory } from "../../../api";
import type { HistoryTableWidget } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

const FIVE_MINUTES_MS = 5 * 60 * 1000;

interface HistoryTableWidgetViewProps {
  widget: HistoryTableWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function HistoryTableWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: HistoryTableWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const field = widget.valueField ?? "value";
  const decimals = widget.decimals ?? 2;
  const variableName = widget.variableName ?? "";

  const history = useQuery({
    queryKey: ["variable-history-table", objectPath, variableName, field],
    queryFn: () => {
      const from = new Date(Date.now() - FIVE_MINUTES_MS).toISOString();
      const to = new Date().toISOString();
      return fetchVariableHistory(objectPath, variableName, {
        field,
        from,
        to,
        limit: 500,
      });
    },
    enabled: Boolean(objectPath && variableName),
    refetchInterval: refreshIntervalMs,
  });

  const rows = useMemo(() => {
    const samples = history.data?.samples ?? [];
    return [...samples].reverse().map((sample) => ({
      ts: sample.ts,
      time: new Date(sample.ts).toLocaleTimeString(),
      value: sample.value,
    }));
  }, [history.data?.samples]);

  const average = useMemo(() => {
    const values = rows
      .map((row) => row.value)
      .filter((value): value is number => value != null && Number.isFinite(value));
    if (values.length === 0) {
      return null;
    }
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  }, [rows]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-history-table"
      editable={editable}
      footer={t("view.historyTable.footer")}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">{t("view.selectObject")}</p>
      ) : !variableName ? (
        <p className="hint">{t("view.specifyVariable")}</p>
      ) : history.isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : history.isError ? (
        <p className="hint">{t("view.historyLoadError")}</p>
      ) : rows.length === 0 ? (
        <p className="hint">{t("view.noHistoryPoints")}</p>
      ) : (
        <div className="dash-table-wrap" style={styles.body}>
          <table className="dash-object-table" style={styles.table}>
            <thead>
              <tr>
                <th>{t("view.historyTable.time")}</th>
                <th>{t("view.historyTable.value")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.ts}>
                  <td>{row.time}</td>
                  <td>
                    {row.value != null && Number.isFinite(row.value)
                      ? row.value.toFixed(decimals)
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="dash-history-table-avg">
                <td>
                  <strong>{t("view.historyTable.average")}</strong>
                </td>
                <td>
                  <strong>
                    {average != null ? average.toFixed(decimals) : "—"}
                  </strong>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </DashWidgetShell>
  );
}
