import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMimic, invokeFunction, setVariable } from "../../../api";
import type { ScadaMimicWidget } from "../../../types/dashboard";
import type { MimicAction, MimicElement } from "../../../types/scadaMimic";
import { parseMimicDocument } from "../../../scada/document";
import {
  collectBindingPaths,
  resolveBindingValue,
  resolveDocumentBindings,
} from "../../../scada/bindingResolver";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { useDashboardContext } from "../DashboardContext";
import { resolveWidgetPath } from "../dashboardUtils";
import DashWidgetShell from "../DashWidgetShell";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import { asBool } from "../../../scada/utils";
import ScadaMimicCanvas from "../../scada/ScadaMimicCanvas";

interface ScadaMimicWidgetViewProps {
  widget: ScadaMimicWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4;

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
}

function defaultViewState(defaultZoom: number | undefined) {
  return {
    panX: 0,
    panY: 0,
    zoom: clampZoom(defaultZoom ?? 1),
  };
}

function isViewChanged(
  view: { panX: number; panY: number; zoom: number },
  baseline: { panX: number; panY: number; zoom: number }
): boolean {
  return (
    Math.abs(view.panX - baseline.panX) > 0.5
    || Math.abs(view.panY - baseline.panY) > 0.5
    || Math.abs(view.zoom - baseline.zoom) > 0.001
  );
}

export default function ScadaMimicWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ScadaMimicWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const session = useDashboardContext();
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<string | null>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState(() => defaultViewState(widget.defaultZoom));

  const panEnabled = widget.panEnabled !== false;
  const documentKey = `${widget.mimicPath ?? ""}:${widget.diagramJson ?? ""}`;
  const baselineView = useMemo(
    () => defaultViewState(widget.defaultZoom),
    [widget.defaultZoom]
  );

  useEffect(() => {
    setView(defaultViewState(widget.defaultZoom));
  }, [documentKey, widget.defaultZoom]);

  const mimicQuery = useQuery({
    queryKey: ["mimic", widget.mimicPath],
    queryFn: () => fetchMimic(widget.mimicPath!),
    enabled: Boolean(widget.mimicPath?.trim()),
  });

  const document = useMemo(() => {
    if (widget.mimicPath?.trim() && mimicQuery.data?.diagramJson) {
      return parseMimicDocument(mimicQuery.data.diagramJson);
    }
    return parseMimicDocument(widget.diagramJson);
  }, [widget.diagramJson, widget.mimicPath, mimicQuery.data?.diagramJson]);

  const bindingPaths = useMemo(
    () => collectBindingPaths(document.elements, document.connections),
    [document]
  );

  const variablesBatch = useVariablesBatchQuery(bindingPaths, refreshIntervalMs, bindingPaths.length > 0);

  const variablesByPath = variablesBatch.data ?? {};

  const resolved = useMemo(
    () => resolveDocumentBindings(document.elements, document.connections, session, variablesByPath),
    [document, session, variablesByPath]
  );

  const actionMutation = useMutation({
    mutationFn: async (action: MimicAction) => {
      const objectPath = resolveWidgetPath(
        action.objectPath,
        action.selectionKey,
        session.selection,
        undefined,
        session.params
      );
      if (action.type === "invokeFunction") {
        if (!objectPath || !action.functionName) throw new Error(t("error.objectAndFunctionRequired"));
        return invokeFunction(objectPath, action.functionName);
      }
      if (!objectPath || !action.variableName) throw new Error(t("scadaMimic.error.bindingRequired"));
      const variables = variablesByPath[objectPath];
      const variable = variables?.find((v) => v.name === action.variableName);
      if (!variable?.value) throw new Error(t("scadaMimic.error.variableNotLoaded"));
      const field = action.valueField ?? "value";
      if (action.type === "toggleVariable") {
        const active = asBool(
          resolveBindingValue(
            {
              variableName: action.variableName,
              objectPath: action.objectPath,
              selectionKey: action.selectionKey,
              valueField: field,
            },
            session,
            variablesByPath
          )
        );
        const record = setFieldValue(cloneRecord(variable.value), field, !active);
        return setVariable(objectPath, action.variableName, record);
      }
      const record = setFieldValue(cloneRecord(variable.value), field, action.value);
      return setVariable(objectPath, action.variableName, record);
    },
    onSuccess: () => {
      setMessage(t("view.done"));
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["variables-batch"] });
    },
  });

  const handleElementClick = useCallback(
    (element: MimicElement) => {
      const action = element.actions?.[0];
      if (!action) return;
      if (action.confirmMessage && !window.confirm(action.confirmMessage)) return;
      actionMutation.mutate(action);
    },
    [actionMutation]
  );

  const hasActions = document.elements.some((el) => (el.actions?.length ?? 0) > 0);
  const viewChanged = panEnabled && isViewChanged(view, baselineView);

  const resetView = useCallback(() => {
    setView(baselineView);
  }, [baselineView]);

  useEffect(() => {
    const node = viewportRef.current;
    if (!node || !panEnabled) return;

    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      event.stopPropagation();

      const rect = node.getBoundingClientRect();
      const mx = event.clientX - rect.left;
      const my = event.clientY - rect.top;
      const factor = event.deltaY < 0 ? 1.12 : 1 / 1.12;

      setView((current) => {
        const nextZoom = clampZoom(current.zoom * factor);
        if (nextZoom === current.zoom) return current;
        return {
          zoom: nextZoom,
          panX: mx - ((mx - current.panX) / current.zoom) * nextZoom,
          panY: my - ((my - current.panY) / current.zoom) * nextZoom,
        };
      });
    };

    node.addEventListener("wheel", onWheel, { passive: false });
    return () => node.removeEventListener("wheel", onWheel);
  }, [panEnabled]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-scada-mimic"
      editable={editable}
    >
      <div ref={viewportRef} className="scada-mimic-viewport">
        {viewChanged && (
          <div className="scada-mimic-view-controls">
            <button
              type="button"
              className="scada-mimic-reset-zoom btn small"
              title={t("view.scadaMimic.resetZoom")}
              onClick={(e) => {
                e.stopPropagation();
                resetView();
              }}
              onMouseDown={(e) => e.stopPropagation()}
            >
              {t("view.scadaMimic.resetZoom")}
            </button>
          </div>
        )}
        <ScadaMimicCanvas
          document={document}
          valuesByElementId={resolved.byElementId}
          valuesByConnectionId={resolved.byConnectionId}
          editable={editable}
          onElementClick={hasActions ? handleElementClick : undefined}
          viewTransform={
            panEnabled
              ? { panX: view.panX, panY: view.panY, zoom: view.zoom }
              : baselineView
          }
        />
      </div>
      {message && <p className="widget-message success">{message}</p>}
      {actionMutation.isError && (
        <p className="widget-message error">{(actionMutation.error as Error).message}</p>
      )}
    </DashWidgetShell>
  );
}
