import { useQuery } from "@tanstack/react-query";
import { fetchObjects } from "../../../api";
import type { ObjectTreeWidget } from "../../../types/dashboard";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ObjectTreeWidgetViewProps {
  widget: ObjectTreeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ObjectTreeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ObjectTreeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection, setSelection } = useDashboardContext();
  const selectedPath = widget.selectionKey ? selection[widget.selectionKey] : undefined;

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-tree"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">Укажите parentPath</p>
      ) : (
        <ul className="dash-object-tree" style={styles.body}>
          {(children.data ?? []).map((obj) => (
            <li key={obj.path}>
              <button
                type="button"
                className={`dash-tree-item ${selectedPath === obj.path ? "selected" : ""}`}
                disabled={editable}
                onClick={() => {
                  if (widget.selectionKey) {
                    setSelection(widget.selectionKey, obj.path);
                  }
                }}
              >
                {obj.displayName}
              </button>
            </li>
          ))}
        </ul>
      )}
    </DashWidgetShell>
  );
}
