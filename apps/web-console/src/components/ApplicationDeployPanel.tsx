import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchDeployHistory,
  listFunctionVersions,
  rollbackDeploy,
  rollbackFunction,
} from "../api/applications";

interface ApplicationDeployPanelProps {
  appId: string;
  canManage: boolean;
}

export default function ApplicationDeployPanel({ appId, canManage }: ApplicationDeployPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [rollbackVersion, setRollbackVersion] = useState<string | null>(null);
  const [fnObjectPath, setFnObjectPath] = useState("root.platform.devices.demo-sensor-01");
  const [fnName, setFnName] = useState("");
  const [fnRollbackVersion, setFnRollbackVersion] = useState<string | null>(null);

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

  return (
    <div className="application-deploy-panel">
      <h3>{t("deploy.title")}</h3>
      <p className="op-muted">{t("deploy.subtitle", { appId })}</p>

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
                <span className="op-muted">{entry.deployedAt}</span>
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

      <h3>{t("deploy.functionVersionsTitle")}</h3>
      <p className="op-muted">{t("deploy.functionVersionsHint")}</p>
      <div className="form-grid">
        <label>
          objectPath
          <input
            value={fnObjectPath}
            onChange={(e) => setFnObjectPath(e.target.value)}
            placeholder="root.platform.devices..."
          />
        </label>
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
                {entry.deployedAt && <span className="op-muted">{entry.deployedAt}</span>}
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
    </div>
  );
}
