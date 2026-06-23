import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { claimWorkTask, completeWorkTask, fetchWorkQueue } from "../../../api";
import {
  applyWorkQueueTaskUpdate,
  refreshWorkQueue,
  workQueueQueryKey,
} from "../../../hooks/workQueueCache";
import type { WorkQueueWidget } from "../../../types/dashboard";
import type { WorkQueueItem } from "../../../types/operator";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { parseDemoPreview } from "../widgetDemoPreview";

interface DemoWorkTask {
  id: string;
  title: string;
  status: string;
  instructions?: string;
  workflowPath?: string;
}

interface WorkQueueWidgetViewProps {
  widget: WorkQueueWidget;
  editable?: boolean;
}

export default function WorkQueueWidgetView({ widget, editable }: WorkQueueWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common", "operator"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const operatorId = widget.operatorId ?? "operator";
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);

  const claimMutation = useMutation({
    mutationFn: (taskId: string) => claimWorkTask(taskId, operatorId),
    onMutate: () => setActionError(null),
    onSuccess: async (updated, taskId) => {
      applyWorkQueueTaskUpdate(queryClient, taskId, updated);
      await refreshWorkQueue(queryClient);
    },
    onError: (error) => setActionError(String(error)),
  });

  const completeMutation = useMutation({
    mutationFn: (taskId: string) => completeWorkTask(taskId, operatorId),
    onMutate: () => setActionError(null),
    onSuccess: async (updated, taskId) => {
      applyWorkQueueTaskUpdate(queryClient, taskId, updated);
      await refreshWorkQueue(queryClient);
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
      await queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
    onError: (error) => setActionError(String(error)),
  });

  const queue = useQuery({
    queryKey: workQueueQueryKey(),
    queryFn: () => fetchWorkQueue(50),
    refetchInterval: 3000,
    refetchOnWindowFocus: true,
    refetchOnMount: "always",
    staleTime: 0,
  });

  const maxItems = widget.maxItems ?? 20;
  const tasks = (queue.data ?? []).filter((t) => t.status !== "COMPLETED").slice(0, maxItems);
  const demoTasks =
    editable && tasks.length === 0 && !queue.isLoading
      ? parseDemoPreview<DemoWorkTask[]>(widget.demoPreviewJson) ?? []
      : [];
  const isDemo = demoTasks.length > 0;
  const displayTasks = isDemo ? demoTasks : tasks;

  return (
    <DashWidgetShell
      title={
        <>
          {widget.title}
          <span className="badge">{displayTasks.length}</span>
        </>
      }
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-work-queue"
      editable={editable}
      demo={isDemo}
    >
      {queue.isLoading && !isDemo && <p className="hint">{t("common:action.loading")}</p>}
      {actionError && <p className="hint error">{actionError}</p>}
      {displayTasks.length === 0 && !queue.isLoading && (
        <p className="hint">{t("view.noOpenTasks")}</p>
      )}
      <ul className="dash-work-queue-list" style={styles.body}>
        {displayTasks.map((task) => (
          <WorkQueueRow
            key={task.id}
            task={task as WorkQueueItem}
            editable={editable}
            claimBusy={claimMutation.isPending && claimMutation.variables === task.id}
            completeBusy={completeMutation.isPending && completeMutation.variables === task.id}
            onClaim={() => claimMutation.mutate(task.id)}
            onComplete={() => completeMutation.mutate(task.id)}
          />
        ))}
      </ul>
    </DashWidgetShell>
  );
}

function WorkQueueRow({
  task,
  editable,
  claimBusy,
  completeBusy,
  onClaim,
  onComplete,
}: {
  task: WorkQueueItem;
  editable?: boolean;
  claimBusy: boolean;
  completeBusy: boolean;
  onClaim: () => void;
  onComplete: () => void;
}) {
  const { t } = useTranslation(["widgets", "operator"]);
  const busy = claimBusy || completeBusy;
  return (
    <li className={`dash-work-queue-item status-${task.status.toLowerCase()}`}>
      <div>
        <strong>{task.title}</strong>
        <p className="hint">{task.instructions || task.workflowPath}</p>
      </div>
      {!editable && (
        <div className="dash-work-queue-actions">
          {task.status === "OPEN" && (
            <button
              type="button"
              className="btn small work-queue-action-btn"
              disabled={busy}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onClaim();
              }}
            >
              {claimBusy ? "…" : t("operator:workQueue.claim")}
            </button>
          )}
          {(task.status === "OPEN" || task.status === "CLAIMED") && (
            <button
              type="button"
              className="btn primary small work-queue-action-btn"
              disabled={busy}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onComplete();
              }}
            >
              {completeBusy ? "…" : "OK"}
            </button>
          )}
        </div>
      )}
    </li>
  );
}
