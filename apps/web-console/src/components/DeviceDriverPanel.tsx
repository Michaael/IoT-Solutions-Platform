import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariables } from "../api";
import {
  configureDriver,
  fetchDriverStatus,
  fetchDrivers,
  startDriver,
  stopDriver,
} from "../api/drivers";
import { formatDriverConfigJson } from "../utils/driverDefaults";
import DriverMaturityBadge, { formatDriverOptionLabel } from "./DriverMaturityBadge";
import type { VariableDto } from "../types";

interface DeviceDriverPanelProps {
  devicePath: string;
  canManage: boolean;
}

function variableString(variables: VariableDto[] | undefined, name: string): string {
  const variable = variables?.find((v) => v.name === name);
  const row = variable?.value?.rows?.[0] as Record<string, unknown> | undefined;
  if (!row) {
    return "";
  }
  const value = row.value;
  return value === null || value === undefined ? "" : String(value);
}

function variableInt(variables: VariableDto[] | undefined, name: string, fallback: number): number {
  const raw = variableString(variables, name);
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function prettyJson(raw: string, fallback: Record<string, unknown>): string {
  if (!raw.trim()) {
    return JSON.stringify(fallback, null, 2);
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function parseJsonObject(text: string, invalidJson: string, invalidObject: string): Record<string, string> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(invalidJson);
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error(invalidObject);
  }
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    result[key] = value === null || value === undefined ? "" : String(value);
  }
  return result;
}

function statusClass(status: string): string {
  switch (status) {
    case "RUNNING":
      return "driver-status-badge running";
    case "ERROR":
      return "driver-status-badge error";
    default:
      return "driver-status-badge stopped";
  }
}

export default function DeviceDriverPanel({ devicePath, canManage }: DeviceDriverPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [driverId, setDriverId] = useState("virtual");
  const [pollIntervalMs, setPollIntervalMs] = useState(5000);
  const [configJson, setConfigJson] = useState("{}");
  const [mappingsJson, setMappingsJson] = useState("{}");
  const [formError, setFormError] = useState<string | null>(null);

  const variablesQuery = useQuery({
    queryKey: ["variables", devicePath],
    queryFn: () => fetchVariables(devicePath),
  });

  const driversQuery = useQuery({
    queryKey: ["drivers"],
    queryFn: fetchDrivers,
    enabled: canManage,
  });

  const statusQuery = useQuery({
    queryKey: ["driver-status", devicePath],
    queryFn: () => fetchDriverStatus(devicePath),
    enabled: Boolean(variableString(variablesQuery.data, "driverId")),
    refetchInterval: 3000,
    retry: false,
  });

  const hasBinding = Boolean(variableString(variablesQuery.data, "driverId"));

  useEffect(() => {
    if (!variablesQuery.data) {
      return;
    }
    const vars = variablesQuery.data;
    setDriverId(variableString(vars, "driverId") || "virtual");
    setPollIntervalMs(variableInt(vars, "driverPollIntervalMs", 5000));
    setConfigJson(prettyJson(variableString(vars, "driverConfigJson"), {}));
    setMappingsJson(prettyJson(variableString(vars, "driverPointMappingsJson"), {}));
  }, [variablesQuery.data]);

  const selectedDriver = useMemo(
    () => driversQuery.data?.find((d) => d.id === driverId),
    [driversQuery.data, driverId]
  );

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["driver-status", devicePath] });
    queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
  };

  const startMutation = useMutation({
    mutationFn: () => startDriver(devicePath),
    onSuccess: invalidate,
  });

  const stopMutation = useMutation({
    mutationFn: () => stopDriver(devicePath),
    onSuccess: invalidate,
  });

  const restartMutation = useMutation({
    mutationFn: async () => {
      await stopDriver(devicePath);
      return startDriver(devicePath);
    },
    onSuccess: invalidate,
  });

  const saveMutation = useMutation({
    mutationFn: (autoStart: boolean) => {
      setFormError(null);
      const configLabel = t("inspector:driver.configLabel");
      const mappingsLabel = t("inspector:driver.mappingsLabel");
      const configuration = parseJsonObject(
        configJson,
        t("common:error.invalidJsonLabel", { label: configLabel }),
        t("inspector:driver.invalidJsonObject", { label: configLabel }),
      );
      const pointMappings = parseJsonObject(
        mappingsJson,
        t("common:error.invalidJsonLabel", { label: mappingsLabel }),
        t("inspector:driver.invalidJsonObject", { label: mappingsLabel }),
      );
      if (!driverId.trim()) {
        throw new Error(t("inspector:driver.driverIdRequired"));
      }
      return configureDriver(devicePath, {
        driverId: driverId.trim(),
        pollIntervalMs,
        configuration,
        pointMappings,
        autoStart,
      });
    },
    onSuccess: invalidate,
    onError: (error: Error) => setFormError(error.message),
  });

  const actionError =
    startMutation.error
    ?? stopMutation.error
    ?? restartMutation.error
    ?? (saveMutation.error && !formError ? saveMutation.error : null);

  const isBusy =
    startMutation.isPending
    || stopMutation.isPending
    || restartMutation.isPending
    || saveMutation.isPending;

  const runtimeStatus =
    statusQuery.data?.status
    ?? (variableString(variablesQuery.data, "driverStatus") || "STOPPED");
  const connected = statusQuery.data?.connected ?? false;
  const lastError = statusQuery.data?.lastError ?? null;

  if (variablesQuery.isLoading) {
    return <p className="hint">{t("inspector:driver.loading")}</p>;
  }

  return (
    <section className="driver-panel">
      <header className="driver-panel-head">
        <div>
          <h3>
            {t("inspector:driver.title")}
            {selectedDriver && <DriverMaturityBadge maturity={selectedDriver.maturity} />}
          </h3>
          <p className="hint">
            {t("inspector:driver.subtitle")}
          </p>
        </div>
        <span className={statusClass(runtimeStatus)}>{runtimeStatus}</span>
      </header>

      <div className="driver-runtime-grid">
        <div className="driver-runtime-stat">
          <span className="driver-runtime-label">driverId</span>
          <code>
            {statusQuery.data?.driverId ?? (variableString(variablesQuery.data, "driverId") || "—")}
          </code>
        </div>
        <div className="driver-runtime-stat">
          <span className="driver-runtime-label">{t("inspector:driver.connection")}</span>
          <span className={connected ? "driver-connected yes" : "driver-connected no"}>
            {connected ? t("common:action.yes") : t("common:action.no")}
          </span>
        </div>
        <div className="driver-runtime-stat">
          <span className="driver-runtime-label">{t("inspector:driver.pollInterval")}</span>
          <span>{statusQuery.data?.pollIntervalMs ?? pollIntervalMs} ms</span>
        </div>
      </div>

      {lastError && (
        <p className="hint error driver-last-error">
          {t("inspector:driver.lastError", { message: lastError })}
        </p>
      )}

      {!hasBinding && (
        <p className="hint warning driver-hint-box">
          {t("inspector:driver.noDriverId")}
        </p>
      )}

      {canManage ? (
        <>
          <div className="driver-actions">
            <button
              type="button"
              className="btn primary"
              disabled={isBusy || !hasBinding}
              onClick={() => startMutation.mutate()}
            >
              {t("inspector:driver.start")}
            </button>
            <button
              type="button"
              className="btn"
              disabled={isBusy}
              onClick={() => stopMutation.mutate()}
            >
              {t("inspector:driver.stop")}
            </button>
            <button
              type="button"
              className="btn"
              disabled={isBusy || !hasBinding}
              onClick={() => restartMutation.mutate()}
            >
              {t("inspector:driver.restart")}
            </button>
          </div>

          <form
            className="driver-config-form"
            onSubmit={(e) => {
              e.preventDefault();
              saveMutation.mutate(false);
            }}
          >
            <h4>{t("inspector:driver.configuration")}</h4>
            <div className="form-grid">
              <label>
                {t("inspector:driver.driverLabel")}
                <select
                  value={driverId}
                  onChange={(e) => {
                    const nextId = e.target.value;
                    setDriverId(nextId);
                    const driver = driversQuery.data?.find((item) => item.id === nextId);
                    setConfigJson(formatDriverConfigJson(driver));
                  }}
                >
                  {(driversQuery.data ?? []).map((driver) => (
                    <option key={driver.id} value={driver.id}>
                      {formatDriverOptionLabel(driver.id, driver.name, driver.maturity)}
                    </option>
                  ))}
                  {driversQuery.data && !driversQuery.data.some((d) => d.id === driverId) && driverId && (
                    <option value={driverId}>{driverId}</option>
                  )}
                </select>
              </label>
              <label>
                {t("inspector:driver.pollIntervalMs")}
                <input
                  type="number"
                  min={500}
                  step={500}
                  value={pollIntervalMs}
                  onChange={(e) => setPollIntervalMs(Number(e.target.value) || 5000)}
                />
              </label>
              {selectedDriver?.description && (
                <p className="hint full">{selectedDriver.description}</p>
              )}
              <label className="full">
                driverConfigJson
                <textarea
                  className="mono"
                  rows={8}
                  value={configJson}
                  onChange={(e) => setConfigJson(e.target.value)}
                  spellCheck={false}
                />
              </label>
              <label className="full">
                driverPointMappingsJson
                <textarea
                  className="mono"
                  rows={6}
                  value={mappingsJson}
                  onChange={(e) => setMappingsJson(e.target.value)}
                  spellCheck={false}
                />
              </label>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn" disabled={isBusy}>
                {t("common:action.save")}
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={isBusy}
                onClick={() => saveMutation.mutate(true)}
              >
                {t("inspector:driver.saveAndStart")}
              </button>
            </div>
            {formError && <p className="hint error">{formError}</p>}
          </form>
        </>
      ) : (
        <p className="hint">{t("common:hint.adminOnly")}</p>
      )}

      {actionError && (
        <p className="hint error">{(actionError as Error).message}</p>
      )}
    </section>
  );
}
