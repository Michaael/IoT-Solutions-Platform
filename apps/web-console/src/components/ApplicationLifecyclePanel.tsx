import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deployApplicationBinding,
  deployApplicationFunction,
  deployApplicationReport,
  fetchApplicationBindings,
  fetchApplicationDataStatus,
  fetchApplicationReports,
  migrateApplicationData,
  refreshApplicationBinding,
  seedApplicationData,
} from "../api/applications";
import { ObjectPathField } from "../ui";

interface ApplicationLifecyclePanelProps {
  appId: string;
  canManage: boolean;
}

const DEFAULT_MIGRATE_JSON = `{
  "version": "1",
  "scripts": [
    { "id": "001_init", "sql": "CREATE TABLE IF NOT EXISTS example (id SERIAL PRIMARY KEY);" }
  ]
}`;

const DEFAULT_FUNCTION_JSON = `{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myFunction",
  "version": "1",
  "descriptor": {
    "inputSchema": { "name": "in", "fields": [] },
    "outputSchema": { "name": "out", "fields": [] }
  },
  "source": { "type": "script", "body": "[]" }
}`;

const DEFAULT_REPORT_JSON = `{
  "reportId": "shift-summary",
  "title": "Shift summary",
  "reportType": "sql",
  "query": "SELECT 1 AS value",
  "columns": [{ "field": "value", "label": "Value" }]
}`;

function parseJson(text: string): unknown {
  return JSON.parse(text) as unknown;
}

export default function ApplicationLifecyclePanel({
  appId,
  canManage,
}: ApplicationLifecyclePanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [migrateText, setMigrateText] = useState(DEFAULT_MIGRATE_JSON);
  const [seedProfile, setSeedProfile] = useState("demo");
  const [functionText, setFunctionText] = useState(DEFAULT_FUNCTION_JSON);
  const [reportText, setReportText] = useState(DEFAULT_REPORT_JSON);
  const [bindingObjectPath, setBindingObjectPath] = useState("");
  const [bindingVariable, setBindingVariable] = useState("");
  const [bindingQuery, setBindingQuery] = useState("SELECT 1 AS value");

  const dataStatusQuery = useQuery({
    queryKey: ["application-data-status", appId],
    queryFn: () => fetchApplicationDataStatus(appId),
    enabled: Boolean(appId),
  });

  const bindingsQuery = useQuery({
    queryKey: ["application-bindings", appId],
    queryFn: () => fetchApplicationBindings(appId),
    enabled: Boolean(appId),
  });

  const reportsQuery = useQuery({
    queryKey: ["application-reports", appId],
    queryFn: () => fetchApplicationReports(appId),
    enabled: Boolean(appId),
  });

  const invalidateObjects = () => {
    queryClient.invalidateQueries({ queryKey: ["objects"] });
    queryClient.invalidateQueries({ queryKey: ["application-data-status", appId] });
    queryClient.invalidateQueries({ queryKey: ["application-bindings", appId] });
    queryClient.invalidateQueries({ queryKey: ["application-reports", appId] });
  };

  const migrateMutation = useMutation({
    mutationFn: () => {
      const parsed = parseJson(migrateText) as { version: string; scripts: { id: string; sql: string }[] };
      return migrateApplicationData(appId, parsed);
    },
    onSuccess: invalidateObjects,
  });

  const seedMutation = useMutation({
    mutationFn: () => seedApplicationData(appId, seedProfile.trim()),
    onSuccess: invalidateObjects,
  });

  const deployBindingMutation = useMutation({
    mutationFn: () =>
      deployApplicationBinding(appId, {
        objectPath: bindingObjectPath.trim(),
        variable: bindingVariable.trim(),
        query: bindingQuery.trim(),
        refresh: "interval",
        refreshIntervalMs: 60_000,
        enabled: true,
      }),
    onSuccess: () => {
      bindingsQuery.refetch();
      invalidateObjects();
    },
  });

  const refreshBindingMutation = useMutation({
    mutationFn: ({ objectPath, variable }: { objectPath: string; variable: string }) =>
      refreshApplicationBinding(appId, objectPath, variable),
    onSuccess: () => bindingsQuery.refetch(),
  });

  const deployFunctionMutation = useMutation({
    mutationFn: () => deployApplicationFunction(appId, parseJson(functionText) as Record<string, unknown>),
    onSuccess: invalidateObjects,
  });

  const deployReportMutation = useMutation({
    mutationFn: () => deployApplicationReport(appId, parseJson(reportText) as Record<string, unknown>),
    onSuccess: () => {
      reportsQuery.refetch();
      invalidateObjects();
    },
  });

  return (
    <div className="application-lifecycle-panel">
      <h3>{t("lifecycle.title")}</h3>
      <p className="op-muted">{t("lifecycle.subtitle")}</p>

      <section className="lifecycle-section panel-card">
        <h4>{t("lifecycle.dataTitle")}</h4>
        {dataStatusQuery.isLoading && <p className="op-muted">{t("lifecycle.loading")}</p>}
        {dataStatusQuery.error && (
          <div className="op-alert op-alert-error">{String(dataStatusQuery.error)}</div>
        )}
        {dataStatusQuery.data && (
          <dl className="lifecycle-dl">
            {dataStatusQuery.data.schemaName && (
              <div>
                <dt>{t("lifecycle.schema")}</dt>
                <dd><code>{dataStatusQuery.data.schemaName}</code></dd>
              </div>
            )}
            {dataStatusQuery.data.version && (
              <div>
                <dt>{t("lifecycle.version")}</dt>
                <dd><code>{dataStatusQuery.data.version}</code></dd>
              </div>
            )}
            {(dataStatusQuery.data.appliedMigrations?.length ?? 0) > 0 && (
              <div>
                <dt>{t("lifecycle.migrations")}</dt>
                <dd>{dataStatusQuery.data.appliedMigrations!.join(", ")}</dd>
              </div>
            )}
          </dl>
        )}
        {canManage && (
          <>
            <label className="full">
              {t("lifecycle.migratePayload")}
              <textarea
                className="textarea mono"
                rows={8}
                value={migrateText}
                onChange={(e) => setMigrateText(e.target.value)}
              />
            </label>
            <div className="form-actions">
              <button
                type="button"
                className="btn primary"
                disabled={migrateMutation.isPending}
                onClick={() => migrateMutation.mutate()}
              >
                {migrateMutation.isPending ? t("lifecycle.migrating") : t("lifecycle.migrate")}
              </button>
              <label className="inline-field">
                {t("lifecycle.seedProfile")}
                <input value={seedProfile} onChange={(e) => setSeedProfile(e.target.value)} />
              </label>
              <button
                type="button"
                className="btn"
                disabled={seedMutation.isPending || !seedProfile.trim()}
                onClick={() => seedMutation.mutate()}
              >
                {seedMutation.isPending ? t("lifecycle.seeding") : t("lifecycle.seed")}
              </button>
            </div>
          </>
        )}
        {migrateMutation.error && (
          <div className="op-alert op-alert-error">{String(migrateMutation.error)}</div>
        )}
        {seedMutation.error && (
          <div className="op-alert op-alert-error">{String(seedMutation.error)}</div>
        )}
        {migrateMutation.isSuccess && (
          <div className="op-alert op-alert-success">{t("lifecycle.migrateSuccess")}</div>
        )}
        {seedMutation.isSuccess && (
          <div className="op-alert op-alert-success">{t("lifecycle.seedSuccess")}</div>
        )}
      </section>

      <section className="lifecycle-section panel-card">
        <h4>{t("lifecycle.bindingsTitle")}</h4>
        {bindingsQuery.isLoading && <p className="op-muted">{t("lifecycle.loading")}</p>}
        {bindingsQuery.data && bindingsQuery.data.length === 0 && (
          <p className="op-muted">{t("lifecycle.bindingsEmpty")}</p>
        )}
        {bindingsQuery.data && bindingsQuery.data.length > 0 && (
          <table className="data-table compact">
            <thead>
              <tr>
                <th>{t("lifecycle.bindingPath")}</th>
                <th>{t("lifecycle.bindingVariable")}</th>
                <th>{t("lifecycle.bindingRefresh")}</th>
                {canManage && <th />}
              </tr>
            </thead>
            <tbody>
              {bindingsQuery.data.map((row) => {
                const key = `${row.objectPath ?? ""}:${row.variable ?? ""}`;
                const refreshing =
                  refreshBindingMutation.isPending
                  && refreshBindingMutation.variables?.objectPath === row.objectPath
                  && refreshBindingMutation.variables?.variable === row.variable;
                return (
                  <tr key={key}>
                    <td><code>{row.objectPath}</code></td>
                    <td><code>{row.variable}</code></td>
                    <td>{row.refresh ?? "—"}</td>
                    {canManage && (
                      <td>
                        <button
                          type="button"
                          className="btn small"
                          disabled={refreshBindingMutation.isPending}
                          onClick={() =>
                            refreshBindingMutation.mutate({
                              objectPath: row.objectPath ?? "",
                              variable: row.variable ?? "",
                            })
                          }
                        >
                          {refreshing ? t("lifecycle.refreshing") : t("lifecycle.refresh")}
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {canManage && (
          <div className="form-grid lifecycle-binding-form">
            <ObjectPathField
              className="full"
              label={t("lifecycle.field.objectPath")}
              value={bindingObjectPath}
              onChange={setBindingObjectPath}
              filterTypes={["DEVICE"]}
            />
            <label>
              {t("lifecycle.field.variable")}
              <input
                value={bindingVariable}
                onChange={(e) => setBindingVariable(e.target.value)}
              />
            </label>
            <label className="full">
              {t("lifecycle.field.query")}
              <textarea
                rows={3}
                className="mono"
                value={bindingQuery}
                onChange={(e) => setBindingQuery(e.target.value)}
              />
            </label>
            <button
              type="button"
              className="btn primary"
              disabled={
                deployBindingMutation.isPending
                || !bindingObjectPath.trim()
                || !bindingVariable.trim()
              }
              onClick={() => deployBindingMutation.mutate()}
            >
              {deployBindingMutation.isPending ? t("lifecycle.deploying") : t("lifecycle.deployBinding")}
            </button>
          </div>
        )}
        {deployBindingMutation.error && (
          <div className="op-alert op-alert-error">{String(deployBindingMutation.error)}</div>
        )}
      </section>

      <section className="lifecycle-section panel-card">
        <h4>{t("lifecycle.reportsTitle")}</h4>
        {reportsQuery.isLoading && <p className="op-muted">{t("lifecycle.loading")}</p>}
        {reportsQuery.data && reportsQuery.data.length === 0 && (
          <p className="op-muted">{t("lifecycle.reportsEmpty")}</p>
        )}
        {reportsQuery.data && reportsQuery.data.length > 0 && (
          <ul className="deploy-history-list">
            {reportsQuery.data.map((row) => (
              <li key={row.reportId ?? row.title}>
                <span><code>{row.reportId}</code> — {row.title ?? row.reportType}</span>
              </li>
            ))}
          </ul>
        )}
        {canManage && (
          <>
            <label className="full">
              {t("lifecycle.reportPayload")}
              <textarea
                className="textarea mono"
                rows={8}
                value={reportText}
                onChange={(e) => setReportText(e.target.value)}
              />
            </label>
            <button
              type="button"
              className="btn primary"
              disabled={deployReportMutation.isPending}
              onClick={() => deployReportMutation.mutate()}
            >
              {deployReportMutation.isPending ? t("lifecycle.deploying") : t("lifecycle.deployReport")}
            </button>
          </>
        )}
        {deployReportMutation.error && (
          <div className="op-alert op-alert-error">{String(deployReportMutation.error)}</div>
        )}
      </section>

      {canManage && (
        <section className="lifecycle-section panel-card">
          <h4>{t("lifecycle.functionTitle")}</h4>
          <label className="full">
            {t("lifecycle.functionPayload")}
            <textarea
              className="textarea mono"
              rows={10}
              value={functionText}
              onChange={(e) => setFunctionText(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="btn primary"
            disabled={deployFunctionMutation.isPending}
            onClick={() => deployFunctionMutation.mutate()}
          >
            {deployFunctionMutation.isPending ? t("lifecycle.deploying") : t("lifecycle.deployFunction")}
          </button>
          {deployFunctionMutation.error && (
            <div className="op-alert op-alert-error">{String(deployFunctionMutation.error)}</div>
          )}
          {deployFunctionMutation.isSuccess && (
            <div className="op-alert op-alert-success">{t("lifecycle.functionSuccess")}</div>
          )}
        </section>
      )}
    </div>
  );
}
