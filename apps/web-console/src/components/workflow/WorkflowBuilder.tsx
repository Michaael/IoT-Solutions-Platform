import { lazy, Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelWorkflowInstance,
  fetchWorkflow,
  runWorkflow,
  saveWorkflowBpmn,
  signalWorkflowInstance,
  updateWorkflowOperatorApp,
  updateWorkflowStatus,
} from "../../api";
import { fetchAuthMe } from "../../api";
import { fetchOperatorApps } from "../../api/operatorApps";
import type { WorkflowLifecycleStatus } from "../../types/workflow";
import { parseInstanceState } from "../../types/workflow";
import { usePersistentTab } from "../../hooks/usePersistentTab";

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
const BPMN_TABS: readonly BpmnTab[] = ["diagram", "xml"];

export default function WorkflowBuilder({
  path,
  onClose,
  onOpenProperties,
}: WorkflowBuilderProps) {
  const { t } = useTranslation(["workflow", "common"]);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">("view");
  const [bpmnTab, setBpmnTab] = usePersistentTab<BpmnTab>(
    `workflow:${path}`,
    "diagram",
    BPMN_TABS
  );
  const [draftBpmn, setDraftBpmn] = useState<string | null>(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

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

  const authQuery = useQuery({
    queryKey: ["auth-me"],
    queryFn: fetchAuthMe,
    staleTime: 60_000,
  });

  const refreshWorkflow = (data: Awaited<ReturnType<typeof fetchWorkflow>>) => {
    queryClient.setQueryData(["workflow", path], data);
    queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
  };

  const cancelMutation = useMutation({
    mutationFn: (reason?: string) => {
      if (!instance.instanceId) {
        throw new Error("No instance");
      }
      return cancelWorkflowInstance(instance.instanceId, {
        reason: reason?.trim() || undefined,
        cancelledBy: authQuery.data?.principal,
      });
    },
    onSuccess: () => {
      setCancelModalOpen(false);
      setCancelReason("");
      void workflow.refetch().then((result) => {
        if (result.data) {
          refreshWorkflow(result.data);
        }
      });
    },
  });

  const signalMutation = useMutation({
    mutationFn: (signal: string) => {
      if (!instance.instanceId) {
        throw new Error("No instance");
      }
      return signalWorkflowInstance(instance.instanceId, {
        signal,
        operatorId: authQuery.data?.principal,
      });
    },
    onSuccess: () => {
      void workflow.refetch().then((result) => {
        if (result.data) {
          refreshWorkflow(result.data);
        }
      });
    },
  });

  const canCancel =
    instance.instanceId
    && (instance.status === "WAITING" || instance.status === "RUNNING");
  const canSignal =
    instance.instanceId
    && instance.status === "WAITING"
    && Boolean(instance.pendingSignalName?.trim());

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
          {!instance.instanceId ? (
            <p className="hint">{t("workflow:instance.empty")}</p>
          ) : (
            <>
              <div className="workflow-instance-grid">
                <div>
                  <span className="field-label">{t("common:table.id")}</span>
                  <div className="mono">{instance.instanceId}</div>
                </div>
                <div>
                  <span className="field-label">{t("workflow:instance.status")}</span>
                  <div>
                    {instance.status ? (
                      <span className={`workflow-pill status-${instance.status.toLowerCase()}`}>
                        {instance.status}
                      </span>
                    ) : (
                      t("common:empty.dash")
                    )}
                  </div>
                </div>
                <div>
                  <span className="field-label">{t("workflow:instance.node")}</span>
                  <div className="mono">{instance.currentNodeId ?? t("common:empty.dash")}</div>
                </div>
                {instance.pendingSignalName && (
                  <div>
                    <span className="field-label">{t("workflow:instance.pendingSignal")}</span>
                    <div className="mono">{instance.pendingSignalName}</div>
                  </div>
                )}
                {instance.assignee && (
                  <div>
                    <span className="field-label">{t("workflow:instance.assignee")}</span>
                    <div>{instance.assignee}</div>
                  </div>
                )}
              </div>
              {(canSignal || canCancel) && (
                <div className="workflow-instance-actions form-actions">
                  {canSignal && instance.pendingSignalName && (
                    <button
                      type="button"
                      className="btn primary"
                      disabled={signalMutation.isPending}
                      onClick={() => signalMutation.mutate(instance.pendingSignalName!)}
                    >
                      {signalMutation.isPending
                        ? t("workflow:instance.signaling")
                        : t("workflow:instance.signal", { signal: instance.pendingSignalName })}
                    </button>
                  )}
                  {canCancel && !cancelModalOpen && (
                    <button
                      type="button"
                      className="btn danger"
                      disabled={cancelMutation.isPending}
                      onClick={() => setCancelModalOpen(true)}
                    >
                      {t("workflow:instance.cancel")}
                    </button>
                  )}
                </div>
              )}
              {cancelModalOpen && (
                <div className="workflow-cancel-inline">
                  <label>
                    <span className="field-label">{t("workflow:instance.cancelReason")}</span>
                    <input
                      value={cancelReason}
                      onChange={(e) => setCancelReason(e.target.value)}
                      placeholder={t("workflow:instance.cancelReasonPlaceholder")}
                    />
                  </label>
                  <div className="form-actions">
                    <button
                      type="button"
                      className="btn danger"
                      disabled={cancelMutation.isPending}
                      onClick={() => cancelMutation.mutate(cancelReason)}
                    >
                      {cancelMutation.isPending
                        ? t("workflow:instance.cancelling")
                        : t("workflow:instance.confirmCancel")}
                    </button>
                    <button
                      type="button"
                      className="btn"
                      disabled={cancelMutation.isPending}
                      onClick={() => {
                        setCancelModalOpen(false);
                        setCancelReason("");
                      }}
                    >
                      {t("common:action.cancel")}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
          {(cancelMutation.error || signalMutation.error) && (
            <p className="hint error">
              {String(cancelMutation.error ?? signalMutation.error)}
            </p>
          )}
          {(cancelMutation.isSuccess || signalMutation.isSuccess) && (
            <p className="hint">{t("workflow:instance.actionDone")}</p>
          )}
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
