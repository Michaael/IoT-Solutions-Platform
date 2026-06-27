import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariableHistory, fetchVariables } from "../../../api";
import type { SheetConfig } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import {
  isObjectWebSocketConnected,
  OBJECT_WS_EVENT,
  subscribeObjectPaths,
  subscribeObjectWebSocketConnection,
  type ObjectWsMessage,
} from "../../../hooks/useObjectWebSocket";
import {
  bindingCacheKey,
  histCacheKey,
  type IspfFormulaContext,
} from "./sheetFormulaEngine";
import {
  extractIspfFormulaVarRefs,
} from "./sheetIspfFormulaDeps";
import type { SheetValues } from "./sheetFormulaEngine";

interface BindingCellRef {
  address: string;
  objectPath: string;
  variableName: string;
  valueField?: string;
}

export function useSheetBindings(
  config: SheetConfig,
  objectPath: string,
  refreshIntervalMs: number,
  formulaContents: SheetValues = {}
): {
  externalByAddr: Map<string, number | string | boolean>;
  ispfContext: IspfFormulaContext;
} {
  const queryClient = useQueryClient();
  const { selection, params } = useDashboardContext();

  const wsConnected = useSyncExternalStore(
    subscribeObjectWebSocketConnection,
    isObjectWebSocketConnected,
    () => false
  );

  const bindingCells = useMemo((): BindingCellRef[] => {
    const refs: BindingCellRef[] = [];
    for (const [address, cell] of Object.entries(config.cells)) {
      if (cell.kind !== "binding" || !cell.variableName) {
        continue;
      }
      const path =
        cell.objectPath?.trim() ||
        resolveWidgetPath(objectPath, undefined, selection, undefined, params);
      refs.push({
        address,
        objectPath: path,
        variableName: cell.variableName,
        valueField: cell.valueField,
      });
    }
    return refs;
  }, [config.cells, objectPath, selection, params]);

  const formulaVarRefs = useMemo(
    () => extractIspfFormulaVarRefs(formulaContents, config, objectPath),
    [formulaContents, config, objectPath]
  );

  const watchedPaths = useMemo(() => {
    const paths = new Set<string>();
    for (const ref of bindingCells) {
      if (ref.objectPath) {
        paths.add(ref.objectPath);
      }
    }
    for (const ref of formulaVarRefs) {
      if (ref.objectPath) {
        paths.add(ref.objectPath);
      }
    }
    return [...paths];
  }, [bindingCells, formulaVarRefs]);

  const pathsKey = watchedPaths.join("|");
  const bindingVarsKey = bindingCells.map((b) => `${b.objectPath}:${b.variableName}`).join("|");
  const formulaVarsKey = formulaVarRefs
    .map((r) => `${r.objectPath}:${r.variableName}:${r.field}:${r.histMinutes ?? ""}`)
    .join("|");

  useEffect(() => {
    if (watchedPaths.length === 0) {
      return;
    }
    subscribeObjectPaths(watchedPaths);
    const retry = window.setInterval(() => subscribeObjectPaths(watchedPaths), 4000);
    return () => window.clearInterval(retry);
  }, [pathsKey, watchedPaths]);

  useEffect(() => {
    if (watchedPaths.length === 0) {
      return;
    }
    const watchedSet = new Set(watchedPaths);
    const onWs = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (message.type !== "VARIABLE_UPDATED" && message.type !== "EVENT_FIRED") {
        return;
      }
      if (!watchedSet.has(message.path)) {
        return;
      }
      void queryClient.invalidateQueries({ queryKey: ["sheet-bindings"] });
    };
    window.addEventListener(OBJECT_WS_EVENT, onWs);
    return () => window.removeEventListener(OBJECT_WS_EVENT, onWs);
  }, [pathsKey, queryClient, watchedPaths]);

  const queries = useQuery({
    queryKey: ["sheet-bindings", pathsKey, bindingVarsKey, formulaVarsKey],
    queryFn: async () => {
      const byPath = new Map<string, Awaited<ReturnType<typeof fetchVariables>>>();
      await Promise.all(
        watchedPaths.map(async (path) => {
          byPath.set(path, await fetchVariables(path));
        })
      );
      return byPath;
    },
    enabled: watchedPaths.length > 0,
    refetchInterval: wsConnected ? false : refreshIntervalMs,
  });

  const histRefs = useMemo(
    () => formulaVarRefs.filter((ref) => ref.histMinutes !== undefined),
    [formulaVarRefs]
  );

  const histQuery = useQuery({
    queryKey: ["sheet-bindings-hist", pathsKey, formulaVarsKey],
    queryFn: async () => {
      const histValues = new Map<string, number>();
      const to = new Date().toISOString();
      await Promise.all(
        histRefs.map(async (ref) => {
          const minutes = ref.histMinutes ?? 5;
          const from = new Date(Date.now() - minutes * 60 * 1000).toISOString();
          try {
            const resp = await fetchVariableHistory(ref.objectPath, ref.variableName, {
              from,
              to,
              limit: 1,
              field: ref.field,
            });
            const sample = resp.samples?.[0];
            if (sample && typeof sample.value === "number") {
              histValues.set(
                histCacheKey(ref.objectPath, ref.variableName, minutes),
                sample.value
              );
            }
          } catch {
            // historian optional
          }
        })
      );
      return histValues;
    },
    enabled: histRefs.length > 0,
    refetchInterval: wsConnected ? false : refreshIntervalMs,
  });

  return useMemo(() => {
    const externalByAddr = new Map<string, number | string | boolean>();
    const bindingValues = new Map<string, number | string | boolean>();
    const tableColumnSums = new Map<string, number>();
    const histValues = histQuery.data ?? new Map<string, number>();

    const byPath = queries.data;
    if (byPath) {
      for (const ref of bindingCells) {
        const vars = byPath.get(ref.objectPath) ?? [];
        const variable = vars.find((v) => v.name === ref.variableName);
        const raw = readFieldValue(variable?.value?.rows?.[0], ref.valueField);
        if (raw !== undefined && raw !== null) {
          const val =
            typeof raw === "number" || typeof raw === "boolean" ? raw : String(raw);
          externalByAddr.set(ref.address, val);
          bindingValues.set(
            bindingCacheKey(ref.objectPath, ref.variableName, ref.valueField ?? "value"),
            val
          );
        }
      }

      const sumVarNames = new Set<string>();
      for (const ref of formulaVarRefs) {
        if (ref.needsTableSum && ref.sumColumn) {
          sumVarNames.add(ref.variableName);
        }
      }
      for (const ref of bindingCells) {
        sumVarNames.add(ref.variableName);
      }

      for (const path of watchedPaths) {
        const vars = byPath.get(path) ?? [];
        for (const variable of vars) {
          if (!sumVarNames.has(variable.name)) {
            continue;
          }
          if (!variable.value?.rows?.length) {
            continue;
          }
          for (const field of ["int", "value", "string"] as const) {
            let sum = 0;
            let count = 0;
            for (const row of variable.value.rows) {
              const v = readFieldValue(row, field);
              const num = typeof v === "number" ? v : Number.parseFloat(String(v ?? ""));
              if (Number.isFinite(num)) {
                sum += num;
                count++;
              }
            }
            if (count > 0) {
              tableColumnSums.set(`${variable.name}|${field}`, sum);
            }
          }
        }
      }

      for (const ref of formulaVarRefs) {
        if (ref.histMinutes !== undefined) {
          continue;
        }
        const vars = byPath.get(ref.objectPath) ?? [];
        const variable = vars.find((v) => v.name === ref.variableName);
        const raw = readFieldValue(variable?.value?.rows?.[0], ref.field);
        if (raw === undefined || raw === null) {
          continue;
        }
        const val = typeof raw === "number" || typeof raw === "boolean" ? raw : String(raw);
        bindingValues.set(
          bindingCacheKey(ref.objectPath, ref.variableName, ref.field),
          val
        );
      }
    }

    return {
      externalByAddr,
      ispfContext: { bindingValues, tableColumnSums, histValues },
    };
  }, [bindingCells, formulaVarRefs, queries.data, histQuery.data, watchedPaths]);
}
