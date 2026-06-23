import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { claimWorkTask, completeWorkTask, fetchWorkQueue } from "../../api";
import { useOperatorSidebarRefresh } from "../../hooks/useOperatorSidebarRefresh";
import {
  applyWorkQueueTaskUpdate,
  refreshWorkQueue,
  workQueueQueryKey,
} from "../../hooks/workQueueCache";
import type { WorkQueueItem } from "../../types/operator";
import type { OperatorUi } from "../../types/operatorUi";
import { filterOperatorSidebarTasks } from "../../utils/operatorSidebarScope";

interface WorkQueuePanelProps {
  operatorId?: string;
  appId?: string;
  ui?: OperatorUi;
  operatorApps?: OperatorUi[];
}

export default function WorkQueuePanel({
  operatorId = "operator",
  appId,
  ui,
  operatorApps = [],
}: WorkQueuePanelProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);
  useOperatorSidebarRefresh(appId, ui);

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
    },
    onError: (error) => setActionError(String(error)),
  });

  const queue = useQuery({
    queryKey: workQueueQueryKey(appId),
    queryFn: () => fetchWorkQueue(50, appId),
    refetchInterval: 3000,
    refetchOnWindowFocus: true,
    refetchOnMount: "always",
    staleTime: 0,
  });

  const tasks = useMemo(() => {
    const open = (queue.data ?? []).filter((t) => t.status !== "COMPLETED");
    if (!appId || !ui) {
      return open;
    }
    return filterOperatorSidebarTasks(open, {
      appId,
      ui,
      operatorApps,
    });
  }, [appId, operatorApps, queue.data, ui]);

  return (
    <section className="work-queue-panel">
      <header className="work-queue-head">
        <h3>{t("workQueue.title")}</h3>
        <span className="badge">{tasks.length}</span>
      </header>
      {queue.isLoading && tasks.length === 0 && <p className="hint">{t("common:action.loading")}</p>}
      {queue.error && <p className="hint error">{t("workQueue.loadError")}</p>}
      {actionError && <p className="hint error">{actionError}</p>}
      {tasks.length === 0 && !queue.isLoading && (
        <p className="hint">{t("workQueue.empty")}</p>
      )}
      <ul className="work-queue-list">
        {tasks.map((task) => (
          <WorkQueueCard
            key={task.id}
            task={task}
            onClaim={() => claimMutation.mutate(task.id)}
            onComplete={() => completeMutation.mutate(task.id)}
            claimBusy={claimMutation.isPending && claimMutation.variables === task.id}
            completeBusy={completeMutation.isPending && completeMutation.variables === task.id}
            t={t}
          />
        ))}
      </ul>
    </section>
  );
}

function WorkQueueCard({
  task,
  onClaim,
  onComplete,
  claimBusy,
  completeBusy,
  t,
}: {
  task: WorkQueueItem;
  onClaim: () => void;
  onComplete: () => void;
  claimBusy: boolean;
  completeBusy: boolean;
  t: (key: string) => string;
}) {
  return (
    <li className={`work-queue-item status-${task.status.toLowerCase()}`}>
      <div className="work-queue-item-body">
        <strong>{task.title}</strong>
        <p className="hint">{task.instructions || task.workflowPath}</p>
        <p className="hint">
          {task.status}
          {task.assignee ? ` · ${task.assignee}` : ""}
        </p>
      </div>
      <div className="work-queue-actions">
        {task.status === "OPEN" && (
          <button
            type="button"
            className="btn small work-queue-action-btn"
            disabled={claimBusy || completeBusy}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              onClaim();
            }}
          >
            {claimBusy ? "…" : t("workQueue.claim")}
          </button>
        )}
        {(task.status === "OPEN" || task.status === "CLAIMED") && (
          <button
            type="button"
            className="btn primary small work-queue-action-btn"
            disabled={claimBusy || completeBusy}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              onComplete();
            }}
          >
            {completeBusy ? "…" : t("workQueue.complete")}
          </button>
        )}
      </div>
    </li>
  );
}
