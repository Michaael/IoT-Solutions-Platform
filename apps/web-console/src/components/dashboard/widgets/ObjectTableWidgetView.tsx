import { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { ObjectTableColumn, ObjectTableWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useDashboardContext, triggerDashboardOpen } from "../DashboardContext";
import { parseJsonObject, parseWidgetJsonArray, matchesNamePattern, objectTableValueField, formatObjectTableCell } from "../dashboardUtils";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ObjectTableWidgetViewProps {
  widget: ObjectTableWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ObjectTableWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ObjectTableWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection, setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const parsedColumns = useMemo(
    () => parseWidgetJsonArray<ObjectTableColumn>(widget.columnsJson),
    [widget.columnsJson]
  );

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const rows = useMemo(() => {
    const list = children.data ?? [];
    return list.filter((obj) => {
      const leaf = obj.path.split(".").pop() ?? "";
      if (widget.namePattern && !matchesNamePattern(leaf, widget.namePattern)) {
        return false;
      }
      if (widget.objectType && obj.type !== widget.objectType) {
        return false;
      }
      return true;
    });
  }, [children.data, widget.namePattern, widget.objectType]);

  const selectedPath = widget.selectionKey ? selection[widget.selectionKey] : undefined;

  useEffect(() => {
    if (!widget.selectionKey || editable || selectedPath) return;
    const first = rows[0];
    if (first) {
      setSelection(widget.selectionKey, first.path);
    }
  }, [rows, editable, selectedPath, setSelection, widget.selectionKey]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-table"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">{t("view.specifyParentPath")}</p>
      ) : children.isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <div className="dash-table-wrap" style={styles.body}>
          <table className="dash-object-table" style={styles.table}>
            <thead>
              <tr>
                <th>{t("view.objectColumn")}</th>
                {parsedColumns.map((col) => (
                  <th key={col.variable ?? col.objectField ?? col.label}>{col.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((obj) => (
                <ObjectTableRow
                  key={obj.path}
                  path={obj.path}
                  displayName={obj.displayName}
                  columns={parsedColumns}
                  selected={selectedPath === obj.path}
                  refreshIntervalMs={refreshIntervalMs}
                  onSelect={() => {
                    if (editable) {
                      return;
                    }
                    const targetKey =
                      widget.rowSelectionKey ?? widget.selectionKey;
                    const openOptions = {
                      selection: targetKey
                        ? { [targetKey]: obj.path }
                        : undefined,
                      params: parseJsonObject(widget.rowParamsJson),
                    };
                    if (widget.selectionKey) {
                      setSelection(widget.selectionKey, obj.path);
                    }
                    triggerDashboardOpen(
                      widget.rowOpenMode,
                      widget.rowTargetDashboard,
                      widget.title,
                      { navigateToDashboard, openDashboardModal },
                      openOptions
                    );
                  }}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </DashWidgetShell>
  );
}

function ObjectTableRow({
  path,
  displayName,
  columns,
  selected,
  refreshIntervalMs,
  onSelect,
}: {
  path: string;
  displayName: string;
  columns: ObjectTableColumn[];
  selected: boolean;
  refreshIntervalMs: number;
  onSelect: () => void;
}) {
  const vars = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <tr
      className={selected ? "selected" : ""}
      onClick={onSelect}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onSelect()}
    >
      <td>{displayName}</td>
      {columns.map((col) => {
        let raw: unknown;
        if (!col.variable) {
          if (col.objectField === "path" || col.field === "path") {
            raw = path;
          } else {
            raw = displayName;
          }
        } else {
          const variable = vars.data?.find((v) => v.name === col.variable);
          raw = readFieldValue(
            variable?.value?.rows[0],
            objectTableValueField(col)
          );
        }
        const text = formatObjectTableCell(raw, col);
        return (
          <td key={`${col.variable ?? col.objectField ?? col.label}-${col.field ?? "value"}`}>
            {text}
          </td>
        );
      })}
    </tr>
  );
}
