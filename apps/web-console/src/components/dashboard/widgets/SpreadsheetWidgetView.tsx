import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { setVariable } from "../../../api";
import { cloneRecord } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useEditorDemoRows } from "../widgetDemoPreview";

interface SpreadsheetWidgetViewProps {
  widget: SpreadsheetWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SpreadsheetWidgetView({
  widget,
  refreshIntervalMs,
  editable: editMode,
}: SpreadsheetWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const queryClient = useQueryClient();
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { variable, isLoading } = useBoundVariable(
    objectPath,
    widget.variableName,
    widget.valueField,
    refreshIntervalMs
  );

  const rows = variable?.value?.rows ?? [];
  const { rows: displayRows, isDemo } = useEditorDemoRows(
    widget,
    rows,
    editMode
  );
  const columns =
    displayRows.length > 0
      ? Object.keys(displayRows[0]).filter((k) => k !== "schema")
      : [];

  const mutation = useMutation({
    mutationFn: async (nextRows: Array<Record<string, unknown>>) => {
      if (!variable?.value) throw new Error("no variable");
      const record = { ...cloneRecord(variable.value), rows: nextRows };
      return setVariable(objectPath, widget.variableName, record);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-spreadsheet"
      editable={editMode}
      demo={isDemo}
    >
      {isLoading && !isDemo ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <div className="dash-table-wrap" style={styles.body}>
          <table className="dash-object-table">
            <thead>
              <tr>
                {columns.map((col) => (
                  <th key={col}>{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {displayRows.map((row, ri) => (
                <tr key={ri}>
                  {columns.map((col) => (
                    <td key={col}>
                      {widget.editable && !editMode && !isDemo ? (
                        <input
                          defaultValue={String(readFieldValue(row, col) ?? "")}
                          onBlur={(e) => {
                            const next = rows.map((r, i) =>
                              i === ri ? { ...r, [col]: e.target.value } : r
                            );
                            mutation.mutate(next);
                          }}
                        />
                      ) : (
                        String(readFieldValue(row, col) ?? "—")
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </DashWidgetShell>
  );
}
