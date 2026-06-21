import { lazy, Suspense, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchWorkflow,
  runWorkflow,
  saveWorkflowBpmn,
  updateWorkflowStatus,
} from "../../api";
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

const STATUS_LABELS: Record<WorkflowLifecycleStatus, string> = {
  DRAFT: "Черновик",
  ACTIVE: "Активен",
  STOPPED: "Остановлен",
};

type BpmnTab = "diagram" | "xml";

export default function WorkflowBuilder({
  path,
  onClose,
  onOpenProperties,
}: WorkflowBuilderProps) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">("view");
  const [bpmnTab, setBpmnTab] = useState<BpmnTab>("diagram");
  const [draftBpmn, setDraftBpmn] = useState<string | null>(null);

  const workflow = useQuery({
    queryKey: ["workflow", path],
    queryFn: () => fetchWorkflow(path),
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

  if (workflow.isLoading) {
    return <div className="workflow-shell loading">Загрузка workflow…</div>;
  }

  if (workflow.error) {
    return (
      <div className="workflow-shell error">
        Не удалось загрузить workflow: {(workflow.error as Error).message}
      </div>
    );
  }

  const status = workflow.data?.status ?? "DRAFT";

  return (
    <div className="workflow-shell">
      <header className="dashboard-toolbar workflow-toolbar">
        <div>
          <div className="dashboard-kicker">Workflow · BPMN / NATS</div>
          <h2>{workflow.data?.title ?? path}</h2>
          <code className="path-code">{path}</code>
          <div className="workflow-status-row">
            <span className={`workflow-pill status-${status.toLowerCase()}`}>
              {STATUS_LABELS[status]}
            </span>
            {workflow.data?.lastRunAt && (
              <span className="hint">Последний запуск: {workflow.data.lastRunAt}</span>
            )}
          </div>
        </div>
        <div className="dashboard-toolbar-actions">
          <button
            type="button"
            className={`btn ${mode === "view" ? "primary" : ""}`}
            onClick={() => setMode("view")}
          >
            Просмотр
          </button>
          <button
            type="button"
            className={`btn ${mode === "edit" ? "primary" : ""}`}
            onClick={() => {
              setMode("edit");
              setBpmnTab("diagram");
            }}
          >
            Редактор
          </button>
          {status !== "ACTIVE" && (
            <button
              type="button"
              className="btn"
              disabled={statusMutation.isPending}
              onClick={() => statusMutation.mutate("ACTIVE")}
            >
              Активировать
            </button>
          )}
          {status === "ACTIVE" && (
            <button
              type="button"
              className="btn"
              disabled={statusMutation.isPending}
              onClick={() => statusMutation.mutate("STOPPED")}
            >
              Остановить
            </button>
          )}
          <button
            type="button"
            className="btn primary"
            disabled={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            Запустить
          </button>
          {onOpenProperties && (
            <button type="button" className="btn" onClick={onOpenProperties}>
              Свойства
            </button>
          )}
          {dirty && (
            <button
              type="button"
              className="btn primary"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Сохранить BPMN
            </button>
          )}
          <button type="button" className="btn" onClick={onClose}>
            Закрыть
          </button>
        </div>
      </header>

      <div className="workflow-body">
        <section className="workflow-panel workflow-side-panel">
          <h3>Триггер</h3>
          <pre className="workflow-code-block">{workflow.data?.triggerJson}</pre>

          {mode === "edit" && (
            <Suspense fallback={null}>
              <WorkflowIspfActionsReference />
            </Suspense>
          )}

          <h3>Экземпляр</h3>
          <div className="workflow-instance-grid">
            <div>
              <span className="field-label">ID</span>
              <div>{instance.instanceId ?? "—"}</div>
            </div>
            <div>
              <span className="field-label">Статус</span>
              <div>{instance.status ?? "—"}</div>
            </div>
            <div>
              <span className="field-label">Узел</span>
              <div className="mono">{instance.currentNodeId ?? "—"}</div>
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
            <h3>BPMN 2.0</h3>
            {mode === "edit" && (
              <div className="workflow-bpmn-tabs">
                <button
                  type="button"
                  className={`btn ${bpmnTab === "diagram" ? "primary" : ""}`}
                  onClick={() => setBpmnTab("diagram")}
                >
                  Диаграмма
                </button>
                <button
                  type="button"
                  className={`btn ${bpmnTab === "xml" ? "primary" : ""}`}
                  onClick={() => setBpmnTab("xml")}
                >
                  Исходник XML
                </button>
              </div>
            )}
          </div>

          {mode === "edit" && bpmnTab === "diagram" && (
            <p className="hint bpmn-hint">
              ISPF-атрибуты (<code>ispf:*</code>, messageTask) сохраняются при редактировании топологии.
              Для точной правки атрибутов используйте вкладку «Исходник XML».
            </p>
          )}

          {mode === "view" && (
            <Suspense fallback={<p className="hint">Загрузка диаграммы…</p>}>
              <BpmnDiagramViewer xml={bpmnXml} />
            </Suspense>
          )}

          {mode === "edit" && bpmnTab === "diagram" && (
            <Suspense fallback={<p className="hint">Загрузка редактора…</p>}>
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
