import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { CardGridWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface CardGridWidgetViewProps {
  widget: CardGridWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function CardGridWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: CardGridWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const variables = useMemo(() => {
    try {
      return widget.variablesJson ? (JSON.parse(widget.variablesJson) as string[]) : [];
    } catch {
      return [] as string[];
    }
  }, [widget.variablesJson]);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const handleCardClick = (path: string) => {
    if (editable) {
      return;
    }
    if (widget.cardSelectionKey) {
      setSelection(widget.cardSelectionKey, path);
    }
    triggerDashboardOpen(widget.cardOpenMode, widget.cardTargetDashboard, widget.title, {
      navigateToDashboard,
      openDashboardModal,
    });
  };

  const navigable = Boolean(widget.cardTargetDashboard?.trim()) && !editable;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-card-grid"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">Укажите parentPath</p>
      ) : (
        <div className="dash-card-grid" style={styles.body}>
          {(children.data ?? []).map((obj) => (
            <ObjectCard
              key={obj.path}
              path={obj.path}
              title={obj.displayName}
              variables={variables}
              refreshIntervalMs={refreshIntervalMs}
              navigable={navigable}
              onOpen={() => handleCardClick(obj.path)}
            />
          ))}
        </div>
      )}
    </DashWidgetShell>
  );
}

function ObjectCard({
  path,
  title,
  variables,
  refreshIntervalMs,
  navigable,
  onOpen,
}: {
  path: string;
  title: string;
  variables: string[];
  refreshIntervalMs: number;
  navigable: boolean;
  onOpen: () => void;
}) {
  const vars = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <article
      className={`dash-object-card ${navigable ? "clickable" : ""}`}
      onClick={navigable ? onOpen : undefined}
      onKeyDown={
        navigable
          ? (event) => {
              if (event.key === "Enter") {
                onOpen();
              }
            }
          : undefined
      }
      role={navigable ? "button" : undefined}
      tabIndex={navigable ? 0 : undefined}
    >
      <h4>{title}</h4>
      <dl>
        {variables.map((name) => {
          const variable = vars.data?.find((v) => v.name === name);
          const raw = readFieldValue(variable?.value?.rows[0], "value");
          return (
            <div key={name} className="dash-card-row">
              <dt>{name}</dt>
              <dd>{raw != null ? String(raw) : "—"}</dd>
            </div>
          );
        })}
      </dl>
    </article>
  );
}
