import { lazy, Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchWorkflow,
  runWorkflow,
  saveWorkflowBpmn,
  updateWorkflowOperatorApp,
  updateWorkflowStatus,
} from "../../api";
import { fetchOperatorApps } from "../../api/operatorApps";
import type { WorkflowLifecycleStatus } from "../../types/workflow";
import { parseInstanceState } from "../../types/workflow";

const BpmnDiagramEditor = lazy(() => import("./BpmnDiagramEditor"));
const BpmnDiagramViewer = lazy(() => import("./BpmnDiagramViewer"));
const WorkflowIspfActionsReference = lazy(() => import("./WorkflowIspfActionsReference"));

interface WorkflowBuilderProps {
  path: string;
  onClose: () => void;
  onOpenProperties?: () => void;
}

const STATUS_KEYS: Record<WorkflowLifecycleStatus, string> = {
  DRAFT: "status.draft",
  ACTIVE: "status.active",
  STOPPED: "status.stopped",
};

type BpmnTab = "diagram" | "xml";

export default function WorkflowBuilder({
  path,
  onClose,
  onOpenProperties,
}: WorkflowBuilderProps) {
  const { t } = useTranslation(["workflow", "common"]);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">("view");
  const [bpmnTab, setBpmnTab] = useState<BpmnTab>("diagram");
  const [draftBpmn, setDraftBpmn] = useState<string | null>(null);

  const workflow = useQuery({
    queryKey: ["workflow", path],
    queryFn: () => fetchWorkflow(path),
  });

  const operatorApps = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
    staleTime: 60_000,
  });

  const bpmnXml = draftBpmn ?? workflow.data?.bpmnXml ?? "";
  const instance = useMemo(
    () => parseInstanceState(workflow.data?.instanceState),
    [workflow.data?.instanceState]
  );
  const dirty = draftBpmn !== null;

  const saveMutation = useMutation({
    mutationFn: () => saveWorkflowBpmn(path, bpmnXml),
    onSuccess: (data) => {
      setDraftBpmn(null);
      queryClient.setQueryData(["workflow", path], data);
    },
  });

  const statusMutation = useMutation({
    mutationFn: (status: WorkflowLifecycleStatus) => updateWorkflowStatus(path, status),
    onSuccess: (data) => queryClient.setQueryData(["workflow", path], data),
  });

  const runMutation = useMutation({
    mutationFn: () => runWorkflow(path),
    onSuccess: (data) => {
      queryClient.setQueryData(["workflow", path], data);
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    },
  });

  const operatorAppMutation = useMutation({
    mutationFn: (operatorAppId: string) => updateWorkflowOperatorApp(path, operatorAppId),
    onSuccess: (data) => queryClient.setQueryData(["workflow", path], data),
  });

  if (workflow.isLoading) {
    return <div className="workflow-shell loading">{t("workflow:loading")}</div>;
  }

  if (workflow.error) {
    return (
      <div className="workflow-shell error">
        {t("workflow:loadError", { message: (workflow.error as Error).message })}
      </div>
    );
  }

  const status = workflow.data?.status ?? "DRAFT";

  return (
    <div className="workflow-shell">
      <header className="dashboard-toolbar workflow-toolbar">
        <div>
          <div className="dashboard-kicker">{t("workflow:kicker")}</div>
          <h2>{workflow.data?.title ?? path}</h2>
          <code className="path-code">{path}</code>
          <div className="workflow-status-row">
            <span className={`workflow-pill status-${status.toLowerCase()}`}>
              {t(`workflow:${STATUS_KEYS[status]}`)}
            </span>
            {workflow.data?.lastRunAt && (
              <span className="hint">{t("workflow:lastRun", { time: workflow.data.lastRunAt })}</span>
            )}
          </div>
        </div>
        <div className="dashboard-toolbar-actions">
          <button
            type="button"
            className={`btn ${mode === "view" ? "primary" : ""}`}
            onClick={() => setMode("view")}
          >
            {t("common:action.view")}
          </button>
          <button
            type="button"
            className={`btn ${mode === "edit" ? "primary" : ""}`}
            onClick={() => {
              setMode("edit");
              setBpmnTab("diagram");
            }}
          >
            {t("common:action.editor")}
          </button>
          {status !== "ACTIVE" && (
            <button
              type="button"
              className="btn"
              disabled={statusMutation.isPending}
              onClick={() => statusMutation.mutate("ACTIVE")}
            >
              {t("workflow:activate")}
            </button>
          )}
          {status === "ACTIVE" && (
            <button
              type="button"
              className="btn"
              disabled={statusMutation.isPending}
              onClick={() => statusMutation.mutate("STOPPED")}
            >
              {t("workflow:stop")}
            </button>
          )}
          <button
            type="button"
            className="btn primary"
            disabled={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            {t("workflow:run")}
          </button>
          {onOpenProperties && (
            <button type="button" className="btn" onClick={onOpenProperties}>
              {t("common:action.properties")}
            </button>
          )}
          {dirty && (
            <button
              type="button"
              className="btn primary"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              {t("workflow:saveBpmn")}
            </button>
          )}
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.close")}
          </button>
        </div>
      </header>

      <div className="workflow-body">
        <section className="workflow-panel workflow-side-panel">
          <h3>{t("workflow:operatorApp.title")}</h3>
          <p className="hint">
            {t("workflow:operatorApp.hint")}
          </p>
          <label className="workflow-operator-app-field">
            <span className="field-label">{t("workflow:operatorApp.field")}</span>
            <select
              value={workflow.data?.operatorAppId ?? ""}
              disabled={operatorAppMutation.isPending}
              onChange={(e) => operatorAppMutation.mutate(e.target.value)}
            >
              <option value="">{t("common:empty.notAssigned")}</option>
              {(operatorApps.data ?? []).map((app) => (
                <option key={app.appId} value={app.appId}>
                  {app.title} ({app.appId})
                </option>
              ))}
            </select>
          </label>

          <h3>{t("workflow:trigger.title")}</h3>
          <pre className="workflow-code-block">{workflow.data?.triggerJson}</pre>

          {mode === "edit" && (
            <Suspense fallback={null}>
              <WorkflowIspfActionsReference />
            </Suspense>
          )}

          <h3>{t("workflow:instance.title")}</h3>
          <div className="workflow-instance-grid">
            <div>
              <span className="field-label">{t("common:table.id")}</span>
              <div>{instance.instanceId ?? t("common:empty.dash")}</div>
            </div>
            <div>
              <span className="field-label">{t("workflow:instance.status")}</span>
              <div>{instance.status ?? t("common:empty.dash")}</div>
            </div>
            <div>
              <span className="field-label">{t("workflow:instance.node")}</span>
              <div className="mono">{instance.currentNodeId ?? t("common:empty.dash")}</div>
            </div>
          </div>
          {instance.history && instance.history.length > 0 && (
            <pre className="workflow-code-block workflow-history">{instance.history.join("\n")}</pre>
          )}
          {instance.errorMessage && (
            <p className="hint error">{instance.errorMessage}</p>
          )}
        </section>

        <section className="workflow-panel workflow-panel-wide workflow-bpmn-panel">
          <div className="workflow-bpmn-head">
            <h3>{t("workflow:bpmn.title")}</h3>
            {mode === "edit" && (
              <div className="workflow-bpmn-tabs">
                <button
                  type="button"
                  className={`btn ${bpmnTab === "diagram" ? "primary" : ""}`}
                  onClick={() => setBpmnTab("diagram")}
                >
                  {t("workflow:bpmn.tab.diagram")}
                </button>
                <button
                  type="button"
                  className={`btn ${bpmnTab === "xml" ? "primary" : ""}`}
                  onClick={() => setBpmnTab("xml")}
                >
                  {t("workflow:bpmn.tab.xml")}
                </button>
              </div>
            )}
          </div>

          {mode === "edit" && bpmnTab === "diagram" && (
            <p className="hint bpmn-hint">
              {t("workflow:bpmn.hint")}
            </p>
          )}

          {mode === "view" && (
            <Suspense fallback={<p className="hint">{t("workflow:bpmn.loadingDiagram")}</p>}>
              <BpmnDiagramViewer xml={bpmnXml} />
            </Suspense>
          )}

          {mode === "edit" && bpmnTab === "diagram" && (
            <Suspense fallback={<p className="hint">{t("workflow:bpmn.loadingEditor")}</p>}>
              <BpmnDiagramEditor
                key={`${path}-diagram`}
                xml={bpmnXml}
                onChange={(next) => setDraftBpmn(next)}
              />
            </Suspense>
          )}

          {mode === "edit" && bpmnTab === "xml" && (
            <textarea
              className="workflow-bpmn-editor"
              value={bpmnXml}
              onChange={(e) => setDraftBpmn(e.target.value)}
              spellCheck={false}
            />
          )}
        </section>
      </div>
    </div>
  );
}
