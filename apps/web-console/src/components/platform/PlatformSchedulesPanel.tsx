import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchPlatformAppSchedules,
  upsertPlatformAppSchedule,
  type PlatformAppSchedule,
} from "../../api/platformAppSchedules";
import { ObjectPathField } from "../../ui";

const DEFAULT_ACTION_JSON = '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"poll"}';

function parseInvokeAction(json: string): { objectPath: string; functionName: string; hasObjectPath: boolean } {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return {
      objectPath: typeof parsed.objectPath === "string" ? parsed.objectPath : "",
      functionName: typeof parsed.functionName === "string" ? parsed.functionName : "",
      hasObjectPath: Object.prototype.hasOwnProperty.call(parsed, "objectPath"),
    };
  } catch {
    return { objectPath: "", functionName: "", hasObjectPath: false };
  }
}

function patchActionJson(
  json: string,
  patch: Partial<{ objectPath: string; functionName: string }>,
): string {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    if (patch.objectPath !== undefined) parsed.objectPath = patch.objectPath;
    if (patch.functionName !== undefined) parsed.functionName = patch.functionName;
    return JSON.stringify(parsed);
  } catch {
    return JSON.stringify({
      objectPath: patch.objectPath ?? "",
      functionName: patch.functionName ?? "",
    });
  }
}

export default function PlatformSchedulesPanel() {
  const { t } = useTranslation(["system", "platform"]);
  const tp = (key: string) => t(key, { ns: "platform" });
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<PlatformAppSchedule | null>(null);
  const [scheduleId, setScheduleId] = useState("");
  const [appId, setAppId] = useState("");
  const [intervalMs, setIntervalMs] = useState("60000");
  const [enabled, setEnabled] = useState(true);
  const [actionType, setActionType] = useState("invoke_function");
  const [actionJson, setActionJson] = useState(DEFAULT_ACTION_JSON);

  const schedulesQuery = useQuery({
    queryKey: ["platform-app-schedules"],
    queryFn: fetchPlatformAppSchedules,
  });

  const loadIntoForm = (row: PlatformAppSchedule) => {
    setEditing(row);
    setScheduleId(row.scheduleId);
    setAppId(row.appId);
    setIntervalMs(String(row.intervalMs));
    setEnabled(row.enabled);
    setActionType(row.actionType ?? "invoke_function");
    setActionJson(row.actionJson ?? DEFAULT_ACTION_JSON);
  };

  const resetForm = () => {
    setEditing(null);
    setScheduleId("");
    setAppId("");
    setIntervalMs("60000");
    setEnabled(true);
    setActionType("invoke_function");
    setActionJson(DEFAULT_ACTION_JSON);
  };

  const saveMutation = useMutation({
    mutationFn: () =>
      upsertPlatformAppSchedule({
        scheduleId: scheduleId.trim(),
        appId: appId.trim(),
        enabled,
        intervalMs: Number(intervalMs) || 60_000,
        action: { type: actionType.trim(), json: actionJson.trim() },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["platform-app-schedules"] });
      resetForm();
    },
  });

  const invokeAction = parseInvokeAction(actionJson);
  const showObjectPathField =
    actionType.trim() === "invoke_function" || invokeAction.hasObjectPath;

  return (
    <section className="system-panel platform-schedules-panel">
      <header>
        <h3>{t("appSchedules.title")}</h3>
        <p className="op-muted">{t("appSchedules.subtitle")}</p>
      </header>

      {schedulesQuery.isLoading && <p className="op-muted">{t("appSchedules.loading")}</p>}
      {schedulesQuery.error && (
        <div className="op-alert op-alert-error">{String(schedulesQuery.error)}</div>
      )}

      {schedulesQuery.data && (
        <div className="panel-card">
          <table className="data-table compact">
            <thead>
              <tr>
                <th>{t("appSchedules.column.id")}</th>
                <th>{t("appSchedules.column.app")}</th>
                <th>{t("appSchedules.column.interval")}</th>
                <th>{t("appSchedules.column.enabled")}</th>
                <th>{t("appSchedules.column.action")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {schedulesQuery.data.length === 0 && (
                <tr>
                  <td colSpan={6} className="op-muted">{t("appSchedules.empty")}</td>
                </tr>
              )}
              {schedulesQuery.data.map((row) => (
                <tr key={row.scheduleId}>
                  <td><code>{row.scheduleId}</code></td>
                  <td><code>{row.appId}</code></td>
                  <td>{row.intervalMs.toLocaleString()} ms</td>
                  <td>{row.enabled ? t("appSchedules.yes") : t("appSchedules.no")}</td>
                  <td><code>{row.actionType ?? "—"}</code></td>
                  <td>
                    <button type="button" className="btn small" onClick={() => loadIntoForm(row)}>
                      {t("appSchedules.edit")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="platform-schedules-form panel-card">
        <h4>{editing ? t("appSchedules.editTitle") : t("appSchedules.createTitle")}</h4>
        <div className="form-grid">
          <label>
            {t("appSchedules.column.id")} *
            <input value={scheduleId} onChange={(e) => setScheduleId(e.target.value)} required />
          </label>
          <label>
            {t("appSchedules.column.app")} *
            <input value={appId} onChange={(e) => setAppId(e.target.value)} required />
          </label>
          <label>
            {tp("schedule.intervalMs")} *
            <input value={intervalMs} onChange={(e) => setIntervalMs(e.target.value)} />
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            {tp("schedule.enabled")}
          </label>
          <label>
            {t("appSchedules.column.action")}
            <input value={actionType} onChange={(e) => setActionType(e.target.value)} />
          </label>
          {showObjectPathField && (
            <>
              <ObjectPathField
                className="full"
                label={tp("schedule.objectPath")}
                value={invokeAction.objectPath}
                onChange={(objectPath) =>
                  setActionJson(patchActionJson(actionJson, { objectPath }))
                }
              />
              <label className="full">
                {tp("schedule.functionName")}
                <input
                  value={invokeAction.functionName}
                  onChange={(e) =>
                    setActionJson(patchActionJson(actionJson, { functionName: e.target.value }))
                  }
                  placeholder="poll"
                />
              </label>
            </>
          )}
          <label className="full">
            {t("appSchedules.column.action")} JSON
            <textarea
              className="mono"
              rows={4}
              value={actionJson}
              onChange={(e) => setActionJson(e.target.value)}
            />
          </label>
        </div>
        <div className="form-actions">
          <button
            type="button"
            className="btn primary"
            disabled={saveMutation.isPending || !scheduleId.trim() || !appId.trim()}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("appSchedules.saving") : t("appSchedules.save")}
          </button>
          {editing && (
            <button type="button" className="btn" onClick={resetForm}>
              {t("appSchedules.cancel")}
            </button>
          )}
        </div>
        {saveMutation.error && (
          <div className="op-alert op-alert-error">{String(saveMutation.error)}</div>
        )}
        {saveMutation.isSuccess && (
          <div className="op-alert op-alert-success">{t("appSchedules.saved")}</div>
        )}
      </div>
    </section>
  );
}
