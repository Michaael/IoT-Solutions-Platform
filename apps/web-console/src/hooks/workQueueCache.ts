import type { QueryClient } from "@tanstack/react-query";
import type { WorkQueueItem } from "../types/operator";

export const WORK_QUEUE_QUERY_ROOT = "work-queue" as const;

export function workQueueQueryKey(appId?: string) {
  const normalized = appId?.trim();
  return normalized
    ? ([WORK_QUEUE_QUERY_ROOT, normalized] as const)
    : ([WORK_QUEUE_QUERY_ROOT] as const);
}

function patchWorkQueueList(
  current: WorkQueueItem[] | undefined,
  taskId: string,
  updated: WorkQueueItem
): WorkQueueItem[] {
  const list = current ?? [];
  if (updated.status === "COMPLETED") {
    return list.filter((task) => task.id !== taskId);
  }
  const index = list.findIndex((task) => task.id === taskId);
  if (index < 0) {
    return [updated, ...list];
  }
  const next = [...list];
  next[index] = updated;
  return next;
}

export function applyWorkQueueTaskUpdate(
  queryClient: QueryClient,
  taskId: string,
  updated: WorkQueueItem
) {
  queryClient.setQueriesData<WorkQueueItem[]>(
    { queryKey: [WORK_QUEUE_QUERY_ROOT] },
    (current) => patchWorkQueueList(current, taskId, updated)
  );
}

export async function refreshWorkQueue(queryClient: QueryClient) {
  await queryClient.refetchQueries({
    queryKey: [WORK_QUEUE_QUERY_ROOT],
    type: "active",
  });
}
