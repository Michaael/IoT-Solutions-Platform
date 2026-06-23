import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { claimWorkTask, completeWorkTask, fetchWorkQueue } from "../../../api";
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
  const styles = useWidgetStyles(widget.stylesJson);
  const operatorId = widget.operatorId ?? "operator";
  const queryClient = useQueryClient();

  const queue = useQuery({
    queryKey: ["work-queue"],
    queryFn: () => fetchWorkQueue(widget.maxItems ?? 20),
    refetchInterval: 5000,
  });

  const claimMutation = useMutation({
    mutationFn: (taskId: string) => claimWorkTask(taskId, operatorId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["work-queue"] }),
  });

  const completeMutation = useMutation({
    mutationFn: (taskId: string) => completeWorkTask(taskId, operatorId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["work-queue"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
  });

  const tasks = (queue.data ?? []).filter((t) => t.status !== "COMPLETED");
  const demoTasks =
    editable && tasks.length === 0 && !queue.isLoading
      ? parseDemoPreview<DemoWorkTask[]>(widget.demoPreviewJson) ?? []
      : [];
  const isDemo = demoTasks.length > 0;
  const displayTasks = isDemo ? demoTasks : tasks;
  const busy = claimMutation.isPending || completeMutation.isPending;

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
      {queue.isLoading && !isDemo && <p className="hint">Загрузка…</p>}
      {displayTasks.length === 0 && !queue.isLoading && (
        <p className="hint">Нет открытых задач</p>
      )}
      <ul className="dash-work-queue-list" style={styles.body}>
        {displayTasks.map((task) => (
          <WorkQueueRow
            key={task.id}
            task={task as WorkQueueItem}
            editable={editable}
            busy={busy}
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
  busy,
  onClaim,
  onComplete,
}: {
  task: WorkQueueItem;
  editable?: boolean;
  busy: boolean;
  onClaim: () => void;
  onComplete: () => void;
}) {
  return (
    <li className={`dash-work-queue-item status-${task.status.toLowerCase()}`}>
      <div>
        <strong>{task.title}</strong>
        <p className="hint">{task.instructions || task.workflowPath}</p>
      </div>
      {!editable && (
        <div className="dash-work-queue-actions">
          {task.status === "OPEN" && (
            <button type="button" className="btn small" disabled={busy} onClick={onClaim}>
              Взять
            </button>
          )}
          {(task.status === "OPEN" || task.status === "CLAIMED") && (
            <button type="button" className="btn primary small" disabled={busy} onClick={onComplete}>
              OK
            </button>
          )}
        </div>
      )}
    </li>
  );
}
