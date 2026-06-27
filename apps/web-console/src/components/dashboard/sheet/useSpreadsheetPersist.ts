import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { setVariable } from "../../../api";
import { getStoredSession, isAdminSession } from "../../../auth/session";
import { validateStoredSession } from "../../../auth/validateSession";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useDashboardContext } from "../DashboardContext";
import { defaultSessionKey, resolveSheetConfig, resolveSheetMode } from "./sheetConfig";
import type { SheetValues } from "./sheetFormulaEngine";
import {
  loadCellContents,
  loadMetaFromVariableRecord,
  loadValuesFromSession,
  loadValuesFromVariableRecord,
  saveValuesToSessionPatch,
  saveValuesToVariableRecord,
  canWriteSheetValues,
  hasSheetValuesSchema,
} from "./sheetPersist";
import {
  parseSheetRuntimeMeta,
  sheetMetaSessionKey,
  type SheetRuntimeMeta,
} from "./sheetRuntimeMeta";

const PERSIST_DEBOUNCE_MS = 400;

function isVariableAclDenied(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /write access denied/i.test(message);
}

function isVariableAuthDenied(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  if (isVariableAclDenied(error)) {
    return false;
  }
  return message.includes("403") || message.includes("401") || /forbidden/i.test(message);
}

export interface MergeLoadedContentsOptions {
  persistMode?: "session" | "variable";
  runtimeMeta?: SheetRuntimeMeta | null;
}

function hasRuntimeMetaShape(runtimeMeta: SheetRuntimeMeta | null | undefined): boolean {
  return Boolean(
    runtimeMeta &&
      (runtimeMeta.rows !== undefined ||
        runtimeMeta.cols !== undefined ||
        runtimeMeta.mergedCells?.length ||
        (runtimeMeta.cellStyles && Object.keys(runtimeMeta.cellStyles).length > 0))
  );
}

function mergeSeedsWithSaved(
  sheetConfig: ReturnType<typeof resolveSheetConfig>,
  sheetMode: ReturnType<typeof resolveSheetMode>,
  saved: SheetValues
): SheetValues {
  const seeds = loadCellContents(undefined, sheetConfig, sheetMode);
  return { ...seeds, ...saved };
}

export function mergeLoadedContents(
  sheetConfig: ReturnType<typeof resolveSheetConfig>,
  sheetMode: ReturnType<typeof resolveSheetMode>,
  sessionValues: SheetValues,
  recordValues: SheetValues,
  options: MergeLoadedContentsOptions = {}
): SheetValues {
  const persistMode = options.persistMode ?? "session";
  const hasMeta = hasRuntimeMetaShape(options.runtimeMeta);

  if (persistMode === "variable") {
    if (Object.keys(recordValues).length > 0) {
      return sheetMode === "free"
        ? { ...recordValues }
        : mergeSeedsWithSaved(sheetConfig, sheetMode, recordValues);
    }
    if (Object.keys(sessionValues).length > 0) {
      return sheetMode === "free"
        ? { ...sessionValues }
        : mergeSeedsWithSaved(sheetConfig, sheetMode, sessionValues);
    }
    if (sheetMode === "free" && hasMeta) {
      return {};
    }
    return loadCellContents(undefined, sheetConfig, sheetMode);
  }

  if (Object.keys(sessionValues).length > 0) {
    if (sheetMode === "free") {
      return { ...sessionValues };
    }
    return mergeSeedsWithSaved(sheetConfig, sheetMode, sessionValues);
  }
  if (Object.keys(recordValues).length > 0) {
    if (sheetMode === "free") {
      return { ...recordValues };
    }
    return mergeSeedsWithSaved(sheetConfig, sheetMode, recordValues);
  }
  if (sheetMode === "free" && hasMeta) {
    return {};
  }
  return loadCellContents(undefined, sheetConfig, sheetMode);
}

export function useSpreadsheetPersist(
  widget: SpreadsheetWidget,
  objectPath: string,
  refreshIntervalMs: number
) {
  const queryClient = useQueryClient();
  const { params, setParams } = useDashboardContext();
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingPersistRef = useRef<{
    contents: SheetValues;
    meta?: SheetRuntimeMeta | null;
  } | null>(null);
  const skipSessionSyncRef = useRef(false);
  const latestContentsRef = useRef<SheetValues>({});
  const latestMetaRef = useRef<SheetRuntimeMeta | null>(null);
  const persistSnapshotRef = useRef<
    (() => { contents: SheetValues; meta: SheetRuntimeMeta | null }) | null
  >(null);
  const variableWriteBlockedRef = useRef(false);
  const variableWriteInFlightRef = useRef(false);
  const pendingVariablePersistRef = useRef<{
    contents: SheetValues;
    meta: SheetRuntimeMeta | null;
  } | null>(null);
  const lastVariablePayloadRef = useRef<string>("");

  const sheetConfig = useMemo(() => resolveSheetConfig(widget), [widget]);
  const sheetMode = resolveSheetMode(widget);
  const persistMode = widget.persistMode ?? "session";
  const sessionKey = defaultSessionKey(widget);
  const metaSessionKey = sheetMetaSessionKey(sessionKey);
  const valuesVarName = widget.valuesVariable?.trim() ?? "";

  const { variable: valuesVariable, isLoading: valuesLoading } = useBoundVariable(
    persistMode === "variable" && valuesVarName ? objectPath : "",
    persistMode === "variable" ? valuesVarName : "",
    widget.valueField,
    refreshIntervalMs
  );
  const valuesVariableRef = useRef(valuesVariable);
  valuesVariableRef.current = valuesVariable;
  const valuesLoadingRef = useRef(valuesLoading);
  valuesLoadingRef.current = valuesLoading;

  const sessionValues = useMemo(
    () => loadValuesFromSession(params, sessionKey),
    [params, sessionKey]
  );
  const hasSessionValues = Object.keys(sessionValues).length > 0;

  const recordValues = useMemo((): SheetValues => {
    if (persistMode !== "variable" || !valuesVarName) {
      return {};
    }
    if (!valuesVariable?.value) {
      return {};
    }
    if (sheetMode === "free") {
      return loadValuesFromVariableRecord(valuesVariable.value);
    }
    return loadCellContents(valuesVariable?.value ?? undefined, sheetConfig, sheetMode);
  }, [persistMode, valuesVarName, valuesVariable?.value, sheetConfig, sheetMode]);
  const hasRecordValues =
    persistMode === "variable" && Object.keys(recordValues).length > 0;

  const recordMeta = useMemo((): SheetRuntimeMeta | null => {
    if (persistMode !== "variable" || !valuesVariable?.value) {
      return null;
    }
    return loadMetaFromVariableRecord(valuesVariable.value);
  }, [persistMode, valuesVariable?.value]);

  const loadedMeta = useMemo(() => {
    const sessionMeta = parseSheetRuntimeMeta(params[metaSessionKey]);
    if (persistMode === "variable") {
      return recordMeta ?? sessionMeta;
    }
    return sessionMeta;
  }, [params, metaSessionKey, persistMode, recordMeta]);

  const loadedContents = useMemo(
    () =>
      mergeLoadedContents(sheetConfig, sheetMode, sessionValues, recordValues, {
        persistMode,
        runtimeMeta: loadedMeta,
      }),
    [sheetConfig, sheetMode, sessionValues, recordValues, persistMode, loadedMeta]
  );

  const sessionSnapshot = useMemo(() => JSON.stringify(sessionValues), [sessionValues]);
  const recordSnapshot = useMemo(() => JSON.stringify(recordValues), [recordValues]);
  const recordMetaSnapshot = useMemo(() => JSON.stringify(recordMeta ?? null), [recordMeta]);
  const metaSnapshot = useMemo(() => JSON.stringify(loadedMeta ?? null), [loadedMeta]);

  const [localContents, setLocalContents] = useState<SheetValues>(loadedContents);
  const [localMeta, setLocalMeta] = useState<SheetRuntimeMeta | null>(loadedMeta);
  const [persistWarning, setPersistWarning] = useState<string | null>(null);
  const authTokenRef = useRef<string | null>(getStoredSession()?.token ?? null);

  useEffect(() => {
    const token = getStoredSession()?.token ?? null;
    if (token !== authTokenRef.current) {
      authTokenRef.current = token;
      variableWriteBlockedRef.current = false;
      setPersistWarning(null);
    }
  });

  useEffect(() => {
    variableWriteBlockedRef.current = false;
    pendingVariablePersistRef.current = null;
    lastVariablePayloadRef.current = "";
    setPersistWarning(null);
  }, [widget.id, objectPath, valuesVarName, persistMode]);

  latestContentsRef.current = localContents;
  latestMetaRef.current = localMeta;

  const setTrackedLocalContents = useCallback((nextContents: SheetValues) => {
    latestContentsRef.current = nextContents;
    setLocalContents(nextContents);
  }, []);

  const setTrackedLocalMeta = useCallback((nextMeta: SheetRuntimeMeta | null) => {
    latestMetaRef.current = nextMeta;
    setLocalMeta(nextMeta);
  }, []);

  const registerPersistSnapshot = useCallback(
    (getter: () => { contents: SheetValues; meta: SheetRuntimeMeta | null }) => {
      persistSnapshotRef.current = getter;
    },
    []
  );

  const getPersistPayload = useCallback((): {
    contents: SheetValues;
    meta: SheetRuntimeMeta | null;
  } => {
    if (persistSnapshotRef.current) {
      return persistSnapshotRef.current();
    }
    return {
      contents: latestContentsRef.current,
      meta: latestMetaRef.current,
    };
  }, []);

  const buildSessionPatch = useCallback(
    (
      nextContents: SheetValues,
      nextMeta: SheetRuntimeMeta | null | undefined,
      allowEmptyContents: boolean
    ): Record<string, unknown> | null => {
      const patch: Record<string, unknown> = {};
      const savedContents = saveValuesToSessionPatch(nextContents);
      if (Object.keys(savedContents).length > 0 || allowEmptyContents) {
        patch[sessionKey] = savedContents;
      }
      if (nextMeta !== undefined) {
        patch[metaSessionKey] = nextMeta ?? {};
      }
      return Object.keys(patch).length > 0 ? patch : null;
    },
    [sessionKey, metaSessionKey]
  );

  useEffect(() => {
    if (skipSessionSyncRef.current) {
      skipSessionSyncRef.current = false;
      return;
    }
    setLocalContents(loadedContents);
    setLocalMeta(loadedMeta);
    // Sync only when persisted session/record/meta snapshots change — not on every params object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- loadedContents/loadedMeta derived from snapshots
  }, [widget.id, sessionKey, sessionSnapshot, recordSnapshot, recordMetaSnapshot, metaSnapshot]);

  const flushVariableWrite = useCallback(() => {
    if (persistMode !== "variable" || !valuesVarName || !objectPath) {
      return;
    }
    if (variableWriteBlockedRef.current) {
      return;
    }
    const session = getStoredSession();
    if (!session?.token) {
      setPersistWarning("spreadsheet.sessionExpired");
      return;
    }
    const varDto = valuesVariableRef.current;
    if (!varDto) {
      if (!valuesLoadingRef.current) {
        setPersistWarning("spreadsheet.valuesVariableNotFound");
      }
      return;
    }
    if (!varDto.writable) {
      setPersistWarning("spreadsheet.variableNotWritable");
      return;
    }
    const existing = varDto.value ?? undefined;
    if (existing && !hasSheetValuesSchema(existing)) {
      setPersistWarning("spreadsheet.variableSchemaInvalid");
      return;
    }
    if (!canWriteSheetValues(varDto)) {
      return;
    }
    const nextPersist = pendingVariablePersistRef.current;
    if (!nextPersist) {
      return;
    }
    pendingVariablePersistRef.current = null;

    const record = saveValuesToVariableRecord(
      nextPersist.contents,
      existing,
      nextPersist.meta ?? latestMetaRef.current
    );
    const payloadKey = JSON.stringify(record);
    if (payloadKey === lastVariablePayloadRef.current) {
      return;
    }
    if (variableWriteInFlightRef.current) {
      pendingVariablePersistRef.current = nextPersist;
      return;
    }

    variableWriteInFlightRef.current = true;
    lastVariablePayloadRef.current = payloadKey;

    void (async () => {
      const activeSession = await validateStoredSession();
      if (!activeSession) {
        lastVariablePayloadRef.current = "";
        variableWriteInFlightRef.current = false;
        pendingVariablePersistRef.current = nextPersist;
        setPersistWarning("spreadsheet.sessionExpired");
        return;
      }

      setVariable(objectPath, valuesVarName, record, { authToken: activeSession.token })
        .then(() => {
          setPersistWarning(null);
          queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
        })
        .catch(async (error) => {
          if (isVariableAclDenied(error)) {
            if (!isAdminSession(activeSession)) {
              variableWriteBlockedRef.current = true;
            }
            setPersistWarning("spreadsheet.variablePersistDenied");
            console.warn(
              `[spreadsheet] Variable write blocked for ${valuesVarName} on ${objectPath}; using dashboard session only.`
            );
            return;
          }
          if (isVariableAuthDenied(error)) {
            const stillValid = await validateStoredSession();
            if (!stillValid) {
              setPersistWarning("spreadsheet.sessionExpired");
              console.warn(
                `[spreadsheet] Auth rejected for ${valuesVarName} on ${objectPath}; re-login required.`
              );
            } else {
              setPersistWarning("spreadsheet.persistForbidden");
              console.error(
                `[spreadsheet] PUT forbidden for ${valuesVarName} on ${objectPath} despite valid session:`,
                error
              );
            }
            return;
          }
          lastVariablePayloadRef.current = "";
          console.error(
            `[spreadsheet] Failed to persist ${valuesVarName} on ${objectPath}:`,
            error
          );
        })
        .finally(() => {
          variableWriteInFlightRef.current = false;
          if (pendingVariablePersistRef.current && !variableWriteBlockedRef.current) {
            flushVariableWrite();
          }
        });
    })();
  }, [persistMode, valuesVarName, objectPath, queryClient]);

  useEffect(() => {
    if (persistMode !== "variable" || !pendingVariablePersistRef.current) {
      return;
    }
    if (!canWriteSheetValues(valuesVariable)) {
      return;
    }
    flushVariableWrite();
  }, [persistMode, valuesVariable, flushVariableWrite]);

  const queueVariablePersist = useCallback(
    (nextContents: SheetValues, nextMeta?: SheetRuntimeMeta | null) => {
      if (!valuesVarName || !objectPath || variableWriteBlockedRef.current) {
        return;
      }
      pendingVariablePersistRef.current = {
        contents: nextContents,
        meta: nextMeta ?? latestMetaRef.current,
      };
      flushVariableWrite();
    },
    [valuesVarName, objectPath, flushVariableWrite]
  );

  const persistNow = useCallback(
    (
      nextContents: SheetValues,
      nextMeta?: SheetRuntimeMeta | null,
      allowEmptyContents = true
    ) => {
      if (persistTimerRef.current) {
        clearTimeout(persistTimerRef.current);
        persistTimerRef.current = null;
      }
      pendingPersistRef.current = null;
      if (persistMode === "session") {
        const patch = buildSessionPatch(nextContents, nextMeta, allowEmptyContents);
        if (!patch) {
          return;
        }
        skipSessionSyncRef.current = true;
        setParams(patch);
        return;
      }
      skipSessionSyncRef.current = true;
      const patch = buildSessionPatch(nextContents, nextMeta, allowEmptyContents);
      if (patch) {
        setParams(patch);
      }
      if (
        Object.keys(nextContents).length > 0 ||
        allowEmptyContents ||
        hasRuntimeMetaShape(nextMeta ?? null)
      ) {
        queueVariablePersist(nextContents, nextMeta);
      }
    },
    [
      persistMode,
      buildSessionPatch,
      setParams,
      queueVariablePersist,
    ]
  );

  const flushPersist = useCallback(() => {
    const { contents, meta } = getPersistPayload();
    persistNow(contents, meta, false);
  }, [getPersistPayload, persistNow]);

  const schedulePersist = useCallback(
    (nextContents: SheetValues, nextMeta?: SheetRuntimeMeta | null) => {
      if (persistTimerRef.current) {
        clearTimeout(persistTimerRef.current);
      }
      pendingPersistRef.current = { contents: nextContents, meta: nextMeta };
      persistTimerRef.current = setTimeout(() => {
        const payload = getPersistPayload();
        persistNow(payload.contents, nextMeta ?? payload.meta);
      }, PERSIST_DEBOUNCE_MS);
    },
    [persistNow, getPersistPayload]
  );

  const persistNowRef = useRef(persistNow);
  persistNowRef.current = persistNow;
  const getPersistPayloadRef = useRef(getPersistPayload);
  getPersistPayloadRef.current = getPersistPayload;

  useEffect(() => {
    return () => {
      const { contents, meta } = getPersistPayloadRef.current();
      persistNowRef.current(contents, meta, false);
    };
  }, [widget.id]);

  const isLoading = persistMode === "variable" && valuesLoading && Boolean(valuesVarName);
  const canEdit = widget.editable !== false;

  return {
    sheetConfig,
    sheetMode,
    localContents,
    setLocalContents: setTrackedLocalContents,
    schedulePersist,
    persistNow,
    flushPersist,
    registerPersistSnapshot,
    localMeta,
    setLocalMeta: setTrackedLocalMeta,
    hasPersistedContents:
      hasSessionValues || hasRecordValues || (sheetMode === "free" && loadedMeta !== null),
    isLoading,
    canEdit,
    persistWarning,
  };
}
