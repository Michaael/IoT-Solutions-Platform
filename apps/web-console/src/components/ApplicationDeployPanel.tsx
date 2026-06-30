import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createBundleObjects,
  deleteBundleObjects,
  fetchDeployHistory,
  fetchApplicationEventCatalog,
  listFunctionVersions,
  rollbackDeploy,
  rollbackFunction,
  updateBundleObjects,
} from "../api/applications";
import ApplicationBundlePanel from "./ApplicationBundlePanel";
import ApplicationLifecyclePanel from "./ApplicationLifecyclePanel";
import { ObjectPathField } from "../ui";

interface ApplicationDeployPanelProps {
  appId: string;
  canManage: boolean;
}

function formatDeployedAt(value: string | undefined): string {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ApplicationDeployPanel({ appId, canManage }: ApplicationDeployPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [rollbackVersion, setRollbackVersion] = useState<string | null>(null);
  const [fnObjectPath, setFnObjectPath] = useState("root.platform.devices.demo-sensor-01");
  const [fnName, setFnName] = useState("");
  const [fnRollbackVersion, setFnRollbackVersion] = useState<string | null>(null);
  const [lifecycleAction, setLifecycleAction] = useState<"create" | "update" | "delete" | null>(
    null
  );

  const historyQuery = useQuery({
    queryKey: ["deploy-history", appId],
    queryFn: () => fetchDeployHistory(appId),
    enabled: Boolean(appId),
  });

  const functionVersionsQuery = useQuery({
    queryKey: ["function-versions", appId, fnObjectPath, fnName],
    queryFn: () => listFunctionVersions(appId, fnObjectPath.trim(), fnName.trim()),
    enabled: Boolean(appId && fnObjectPath.trim() && fnName.trim()),
  });

  const eventCatalogQuery = useQuery({
    queryKey: ["application-event-catalog", appId],
    queryFn: () => fetchApplicationEventCatalog(appId),
    enabled: Boolean(appId),
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: string) => rollbackDeploy(appId, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const functionRollbackMutation = useMutation({
    mutationFn: (version: string) =>
      rollbackFunction(appId, fnObjectPath.trim(), fnName.trim(), version),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["function-versions", appId, fnObjectPath, fnName],
      });
      queryClient.invalidateQueries({ queryKey: ["object-editor", fnObjectPath] });
    },
  });

  const invalidateAfterLifecycle = () => {
    queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
    queryClient.invalidateQueries({ queryKey: ["objects"] });
  };

  const createObjectsMutation = useMutation({
    mutationFn: () => createBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const updateObjectsMutation = useMutation({
    mutationFn: () => updateBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const deleteObjectsMutation = useMutation({
    mutationFn: () => deleteBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const lifecycleMutation =
    lifecycleAction === "create"
      ? createObjectsMutation
      : lifecycleAction === "update"
        ? updateObjectsMutation
        : lifecycleAction === "delete"
          ? deleteObjectsMutation
          : null;

  const runLifecycle = (action: "create" | "update" | "delete") => {
    const confirmKey =
      action === "delete"
        ? "deploy.bundleObjectsDeleteConfirm"
        : action === "update"
          ? "deploy.bundleObjectsUpdateConfirm"
          : "deploy.bundleObjectsCreateConfirm";
    if (!window.confirm(t(confirmKey))) {
      return;
    }
    setLifecycleAction(action);
    if (action === "create") {
      createObjectsMutation.mutate();
    } else if (action === "update") {
      updateObjectsMutation.mutate();
    } else {
      deleteObjectsMutation.mutate();
    }
  };

  return (
    <div className="application-deploy-panel">
      <h3>{t("deploy.title")}</h3>
      <p className="op-muted">{t("deploy.subtitle", { appId })}</p>

      <ApplicationBundlePanel appId={appId} canManage={canManage} />

      {historyQuery.isLoading && <p className="op-muted">{t("deploy.loadingHistory")}</p>}
      {historyQuery.error && (
        <div className="op-alert op-alert-error">{String(historyQuery.error)}</div>
      )}

      {historyQuery.data && historyQuery.data.length === 0 && (
        <p className="op-muted">{t("deploy.emptyHistory")}</p>
      )}

      {historyQuery.data && historyQuery.data.length > 0 && (
        <ul className="deploy-history-list">
          {historyQuery.data.map((entry) => {
            const rollingBack =
              rollbackMutation.isPending && rollbackVersion === entry.version;
            return (
              <li key={entry.version} className={entry.active ? "active-version" : ""}>
                <span>
                  v{entry.version}
                  {entry.active ? ` (${t("deploy.active")})` : ""}
                </span>
                <span className="op-muted">{formatDeployedAt(entry.deployedAt)}</span>
                {canManage && !entry.active && (
                  <button
                    type="button"
                    className="btn"
                    disabled={rollbackMutation.isPending}
                    onClick={() => {
                      setRollbackVersion(entry.version);
                      rollbackMutation.mutate(entry.version);
                    }}
                  >
                    {rollingBack ? t("deploy.rollingBack") : t("deploy.rollbackBundle")}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {rollbackMutation.error && (
        <div className="op-alert op-alert-error">{String(rollbackMutation.error)}</div>
      )}
      {rollbackMutation.isSuccess && (
        <div className="op-alert op-alert-success">
          {t("deploy.rollbackSuccess")}
          {rollbackMutation.data?.rolledBackTo
            ? ` → ${t("deploy.versionLabel", { version: rollbackMutation.data.rolledBackTo })}`
            : ""}
        </div>
      )}

      <hr />

      <h3>{t("deploy.eventCatalogTitle")}</h3>
      <p className="op-muted">{t("deploy.eventCatalogHint")}</p>
      {eventCatalogQuery.isLoading && <p className="op-muted">{t("deploy.eventCatalogLoading")}</p>}
      {eventCatalogQuery.error && (
        <div className="op-alert op-alert-error">{String(eventCatalogQuery.error)}</div>
      )}
      {eventCatalogQuery.data && eventCatalogQuery.data.length === 0 && (
        <p className="op-muted">{t("deploy.eventCatalogEmpty")}</p>
      )}
      {eventCatalogQuery.data && eventCatalogQuery.data.length > 0 && (
        <table className="data-table compact">
          <thead>
            <tr>
              <th>{t("deploy.eventCatalogId")}</th>
              <th>{t("deploy.eventCatalogRoles")}</th>
            </tr>
          </thead>
          <tbody>
            {eventCatalogQuery.data.map((entry) => (
              <tr key={entry.id}>
                <td><code>{entry.id}</code></td>
                <td>
                  {(entry.roles ?? []).length > 0
                    ? entry.roles!.join(", ")
                    : t("deploy.eventCatalogAnyRole")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <hr />

      <h3>{t("deploy.bundleObjectsTitle")}</h3>
      <p className="op-muted">{t("deploy.bundleObjectsHint")}</p>
      {canManage && (
        <div className="bundle-object-actions">
          <button
            type="button"
            className="btn"
            disabled={Boolean(lifecycleMutation?.isPending)}
            onClick={() => runLifecycle("create")}
          >
            {lifecycleAction === "create" && createObjectsMutation.isPending
              ? t("deploy.bundleObjectsCreating")
              : t("deploy.bundleObjectsCreate")}
          </button>
          <button
            type="button"
            className="btn"
            disabled={Boolean(lifecycleMutation?.isPending)}
            onClick={() => runLifecycle("update")}
          >
            {lifecycleAction === "update" && updateObjectsMutation.isPending
              ? t("deploy.bundleObjectsUpdating")
              : t("deploy.bundleObjectsUpdate")}
          </button>
          <button
            type="button"
            className="btn danger"
            disabled={Boolean(lifecycleMutation?.isPending)}
            onClick={() => runLifecycle("delete")}
          >
            {lifecycleAction === "delete" && deleteObjectsMutation.isPending
              ? t("deploy.bundleObjectsDeleting")
              : t("deploy.bundleObjectsDelete")}
          </button>
        </div>
      )}

      {lifecycleMutation?.error && (
        <div className="op-alert op-alert-error">{String(lifecycleMutation.error)}</div>
      )}
      {lifecycleMutation?.isSuccess && lifecycleMutation.data && (
        <div className="op-alert op-alert-success">
          {t("deploy.bundleObjectsSuccess", {
            action: lifecycleMutation.data.action ?? lifecycleAction ?? "",
            status: lifecycleMutation.data.status ?? "OK",
          })}
          {(lifecycleMutation.data.applied?.length ?? 0) > 0 && (
            <span>
              {" "}
              {t("deploy.bundleObjectsAppliedCount", {
                count: lifecycleMutation.data.applied?.length ?? 0,
              })}
            </span>
          )}
          {(lifecycleMutation.data.removed?.length ?? 0) > 0 && (
            <span>
              {" "}
              {t("deploy.bundleObjectsRemovedCount", {
                count: lifecycleMutation.data.removed?.length ?? 0,
              })}
            </span>
          )}
          {(lifecycleMutation.data.skipped?.length ?? 0) > 0 && (
            <span>
              {" "}
              {t("deploy.bundleObjectsSkippedCount", {
                count: lifecycleMutation.data.skipped?.length ?? 0,
              })}
            </span>
          )}
        </div>
      )}

      <hr />

      <h3>{t("deploy.functionVersionsTitle")}</h3>
      <p className="op-muted">{t("deploy.functionVersionsHint")}</p>
      <div className="form-grid">
        <ObjectPathField
          label="objectPath"
          value={fnObjectPath}
          onChange={setFnObjectPath}
        />
        <label>
          functionName
          <input
            value={fnName}
            onChange={(e) => setFnName(e.target.value)}
            placeholder="myFunction"
          />
        </label>
      </div>

      {functionVersionsQuery.isFetching && fnName.trim() && (
        <p className="op-muted">{t("deploy.loadingVersions")}</p>
      )}
      {functionVersionsQuery.error && (
        <div className="op-alert op-alert-error">{String(functionVersionsQuery.error)}</div>
      )}

      {functionVersionsQuery.data && functionVersionsQuery.data.length > 0 && (
        <ul className="deploy-history-list">
          {functionVersionsQuery.data.map((entry) => {
            const rollingBack =
              functionRollbackMutation.isPending && fnRollbackVersion === entry.version;
            return (
              <li key={entry.version} className={entry.active ? "active-version" : ""}>
                <span>
                  v{entry.version}
                  {entry.active ? ` (${t("deploy.active")})` : ""}
                </span>
                {entry.deployedAt && (
                  <span className="op-muted">{formatDeployedAt(entry.deployedAt)}</span>
                )}
                {canManage && !entry.active && (
                  <button
                    type="button"
                    className="btn"
                    disabled={functionRollbackMutation.isPending}
                    onClick={() => {
                      setFnRollbackVersion(entry.version);
                      functionRollbackMutation.mutate(entry.version);
                    }}
                  >
                    {rollingBack ? t("deploy.deploying") : t("deploy.deployPrevious")}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {functionRollbackMutation.error && (
        <div className="op-alert op-alert-error">{String(functionRollbackMutation.error)}</div>
      )}
      {functionRollbackMutation.isSuccess && (
        <div className="op-alert op-alert-success">
          {t("deploy.functionActiveVersion", { version: functionRollbackMutation.data?.version })}
        </div>
      )}

      <ApplicationLifecyclePanel appId={appId} canManage={canManage} />
    </div>
  );
}
