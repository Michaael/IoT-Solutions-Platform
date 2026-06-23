import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { refreshWorkQueue } from "./workQueueCache";
import type { OperatorUi } from "../types/operatorUi";
import { collectOperatorAppWatchPaths } from "../utils/operatorSidebarScope";
import { OBJECT_WS_EVENT, subscribeObjectPaths, type ObjectWsMessage } from "./useObjectWebSocket";

export const OPERATOR_SIDEBAR_EVENTS_QUERY_KEY = "operator-sidebar";

export function useOperatorSidebarRefresh(appId?: string, ui?: OperatorUi) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!ui) {
      return;
    }
    const paths = collectOperatorAppWatchPaths(ui, appId);
    if (paths.length > 0) {
      subscribeObjectPaths(paths);
      const retry = window.setInterval(() => subscribeObjectPaths(paths), 4000);
      return () => window.clearInterval(retry);
    }
  }, [appId, ui]);

  useEffect(() => {
    if (!ui) {
      return;
    }
    const refresh = (message: ObjectWsMessage) => {
      if (
        message.type === "EVENT_FIRED" ||
        message.type === "VARIABLE_UPDATED" ||
        message.type === "UPDATED"
      ) {
        void queryClient.invalidateQueries({ queryKey: ["events", OPERATOR_SIDEBAR_EVENTS_QUERY_KEY] });
      }
      if (
        message.type === "VARIABLE_UPDATED" ||
        message.type === "EVENT_FIRED" ||
        message.type === "UPDATED"
      ) {
        void refreshWorkQueue(queryClient);
      }
    };

    const handler = (raw: Event) => {
      refresh((raw as CustomEvent<ObjectWsMessage>).detail);
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [queryClient, ui]);
}
