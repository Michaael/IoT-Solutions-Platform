import { useCallback, useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchDashboardContext, saveDashboardContext } from "../api";
import type { DashboardSession } from "../components/dashboard/DashboardContext";
import { mergeSession } from "../components/dashboard/DashboardContext";
import {
  DASHBOARD_CONTEXT_VARIABLE,
  patchFromSession,
  sessionFromServerContext,
  sessionsEqual,
} from "../utils/dashboardContext";
import { OBJECT_WS_EVENT, subscribeObjectPaths, type ObjectWsMessage } from "./useObjectWebSocket";

const SYNC_DEBOUNCE_MS = 300;

interface UseDashboardContextSyncOptions {
  path: string;
  enabled?: boolean;
  session: DashboardSession;
  onSessionChange: (next: DashboardSession) => void;
  updatedBy?: string;
}

/**
 * Mirrors {@code @dashboardContext} on the server: hydrate on load, debounced PUT on change, WS reconcile.
 */
export function useDashboardContextSync({
  path,
  enabled = true,
  session,
  onSessionChange,
  updatedBy,
}: UseDashboardContextSyncOptions) {
  const queryClient = useQueryClient();
  const sessionRef = useRef(session);
  const syncingRef = useRef(false);
  const debounceRef = useRef<number | undefined>(undefined);
  const hydratedRef = useRef(false);

  sessionRef.current = session;

  const contextQuery = useQuery({
    queryKey: ["dashboard-context", path],
    queryFn: () => fetchDashboardContext(path),
    enabled: enabled && Boolean(path),
    staleTime: 5_000,
  });

  useEffect(() => {
    subscribeObjectPaths([path]);
  }, [path]);

  useEffect(() => {
    if (!enabled || !contextQuery.data || hydratedRef.current) {
      return;
    }
    const serverSession = sessionFromServerContext(contextQuery.data.context);
    hydratedRef.current = true;
    if (!sessionsEqual(sessionRef.current, serverSession)) {
      onSessionChange(mergeSession(sessionRef.current, serverSession));
    }
  }, [contextQuery.data, enabled, onSessionChange]);

  const pushSession = useCallback(
    (next: DashboardSession) => {
      if (!enabled || syncingRef.current) {
        return;
      }
      window.clearTimeout(debounceRef.current);
      debounceRef.current = window.setTimeout(() => {
        syncingRef.current = true;
        void saveDashboardContext(path, patchFromSession(next), updatedBy)
          .then((view) => {
            queryClient.setQueryData(["dashboard-context", path], view);
          })
          .finally(() => {
            syncingRef.current = false;
          });
      }, SYNC_DEBOUNCE_MS);
    },
    [enabled, path, queryClient, updatedBy]
  );

  useEffect(() => {
    if (!enabled || !hydratedRef.current) {
      return;
    }
    pushSession(session);
  }, [enabled, session, pushSession]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const onWs = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (
        message.type !== "VARIABLE_UPDATED"
        || message.path !== path
        || message.variableName !== DASHBOARD_CONTEXT_VARIABLE
      ) {
        return;
      }
      if (syncingRef.current) {
        return;
      }
      void fetchDashboardContext(path).then((view) => {
        queryClient.setQueryData(["dashboard-context", path], view);
        const serverSession = sessionFromServerContext(view.context);
        if (!sessionsEqual(sessionRef.current, serverSession)) {
          onSessionChange(serverSession);
        }
      });
    };
    window.addEventListener(OBJECT_WS_EVENT, onWs);
    return () => window.removeEventListener(OBJECT_WS_EVENT, onWs);
  }, [enabled, onSessionChange, path, queryClient]);

  useEffect(
    () => () => {
      window.clearTimeout(debounceRef.current);
    },
    []
  );
}
