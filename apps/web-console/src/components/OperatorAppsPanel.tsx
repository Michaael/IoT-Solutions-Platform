import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchObjects } from "../api";
import {
  createOperatorApp,
  fetchOperatorAppUi,
  fetchOperatorApps,
  saveOperatorAppUi,
} from "../api/operatorApps";
import type { OperatorUi, OperatorUiDashboard } from "../types/operatorUi";
import type { OperatorAlarmBarConfig } from "../types/operatorAlarmBar";
import OperatorAlarmBarEditor from "./operator/OperatorAlarmBarEditor";
import {
  operatorAppLeafFromPath,
  resolveOperatorAppId,
} from "../utils/operatorAppsPath";

interface OperatorAppsPanelProps {
  canManage: boolean;
  selectedPath: string;
}

const DASHBOARD_PARENT = "root.platform.dashboards";

export default function OperatorAppsPanel({ canManage, selectedPath }: OperatorAppsPanelProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const pathLeaf = operatorAppLeafFromPath(selectedPath);

  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
  });
  const dashboardsQuery = useQuery({
    queryKey: ["objects", DASHBOARD_PARENT],
    queryFn: () => fetchObjects(DASHBOARD_PARENT),
  });

  const apps = appsQuery.data ?? [];
  const selectedAppId = useMemo(() => {
    if (!pathLeaf) {
      return "platform";
    }
    return resolveOperatorAppId(pathLeaf, apps);
  }, [apps, pathLeaf]);

  const [title, setTitle] = useState("");
  const [defaultDashboard, setDefaultDashboard] = useState("");
  const [selectedPaths, setSelectedPaths] = useState<string[]>([]);
  const [alarmBar, setAlarmBar] = useState<OperatorAlarmBarConfig | undefined>(undefined);

  const uiQuery = useQuery({
    queryKey: ["operator-app-ui", selectedAppId],
    queryFn: () => fetchOperatorAppUi(selectedAppId),
    enabled: Boolean(selectedAppId),
  });

  useEffect(() => {
    const ui = uiQuery.data;
    if (ui) {
      setTitle(ui.title);
      setDefaultDashboard(ui.defaultDashboard);
      setSelectedPaths(ui.dashboards.map((item) => item.path));
      setAlarmBar(ui.alarmBar);
      return;
    }
    if (pathLeaf) {
      setTitle(pathLeaf);
      setDefaultDashboard("");
      setSelectedPaths([]);
      setAlarmBar(undefined);
    }
  }, [uiQuery.data, pathLeaf]);

  const availableDashboards = useMemo(
    () => (dashboardsQuery.data ?? []).filter((obj) => obj.type === "DASHBOARD"),
    [dashboardsQuery.data]
  );

  const saveMutation = useMutation({
    mutationFn: async (ui: OperatorUi) => {
      try {
        return await saveOperatorAppUi(selectedAppId, ui);
      } catch (error) {
        if (!canManage) {
          throw error;
        }
        await createOperatorApp(selectedAppId, ui.title);
        return saveOperatorAppUi(selectedAppId, ui);
      }
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(["operator-app-ui", selectedAppId], saved);
      queryClient.invalidateQueries({ queryKey: ["operator-apps"] });
      queryClient.invalidateQueries({ queryKey: ["operator-ui", selectedAppId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const toggleDashboard = (path: string) => {
    setSelectedPaths((current) =>
      current.includes(path) ? current.filter((item) => item !== path) : [...current, path]
    );
  };

  const buildUi = (): OperatorUi | null => {
    const dashboards: OperatorUiDashboard[] = selectedPaths.map((path) => {
      const obj = availableDashboards.find((item) => item.path === path);
      return { path, title: obj?.displayName ?? path.split(".").pop() ?? path };
    });
    if (!title.trim() || dashboards.length === 0) {
      return null;
    }
    const resolvedDefault = dashboards.some((item) => item.path === defaultDashboard)
      ? defaultDashboard
      : dashboards[0].path;
    return {
      appId: selectedAppId,
      title: title.trim(),
      defaultDashboard: resolvedDefault,
      dashboards,
      alarmBar,
    };
  };

  const dirty = useMemo(() => {
    const ui = uiQuery.data;
    const draft = buildUi();
    if (!draft) {
      return false;
    }
    if (!ui) {
      return true;
    }
    return JSON.stringify(ui) !== JSON.stringify(draft);
  }, [uiQuery.data, title, defaultDashboard, selectedPaths, selectedAppId, availableDashboards, alarmBar]);

  const handleSave = () => {
    const ui = buildUi();
    if (!ui) {
      return;
    }
    saveMutation.mutate(ui);
  };

  return (
    <section className="operator-apps-panel automation-panel operator-apps-panel-main">
      <header className="automation-panel-head">
        <div>
          <h2>{title || selectedAppId}</h2>
          <p className="hint">
            {t("operatorApps.uiHint", {
              query: `?mode=operator&app=${selectedAppId}`,
              path: selectedPath,
            })}
          </p>
        </div>
      </header>

      {appsQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {appsQuery.error && <p className="hint error">{String(appsQuery.error)}</p>}

      <div className="operator-apps-editor">
        {uiQuery.isLoading && <p className="hint">{t("operatorApps.loadingConfig")}</p>}
        {uiQuery.error && <p className="hint error">{String(uiQuery.error)}</p>}

        {!uiQuery.isLoading && !uiQuery.error && !uiQuery.data && (
          <p className="hint">
            {t("operatorApps.noConfig", {
              adminSuffix: canManage ? "" : t("operatorApps.noConfigAdminSuffix"),
            })}
          </p>
        )}

        {(uiQuery.data || canManage) && !uiQuery.isLoading && (
          <div className="form-grid compact">
            <label>
              {t("operatorApps.appTitle")}
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                disabled={!canManage}
              />
            </label>
            <label>
              {t("operatorApps.defaultDashboard")}
              <select
                value={defaultDashboard}
                onChange={(e) => setDefaultDashboard(e.target.value)}
                disabled={!canManage || selectedPaths.length === 0}
              >
                {selectedPaths.map((path) => {
                  const obj = availableDashboards.find((item) => item.path === path);
                  return (
                    <option key={path} value={path}>
                      {obj?.displayName ?? path}
                    </option>
                  );
                })}
              </select>
            </label>

            <div className="full operator-apps-dashboards">
              <strong>{t("operatorApps.menuDashboards")}</strong>
              <p className="hint">
                {t("operatorApps.menuDashboardsHint", { path: DASHBOARD_PARENT })}
              </p>
              {dashboardsQuery.isLoading && <p className="hint">{t("operatorApps.loadingDashboards")}</p>}
              <ul className="operator-apps-dashboard-list">
                {availableDashboards.map((dashboard) => (
                  <li key={dashboard.path} className="operator-apps-dashboard-item">
                    <label className="operator-apps-dashboard-row">
                      <input
                        type="checkbox"
                        className="operator-apps-dashboard-check"
                        checked={selectedPaths.includes(dashboard.path)}
                        disabled={!canManage}
                        onChange={() => toggleDashboard(dashboard.path)}
                      />
                      <span className="operator-apps-dashboard-meta">
                        <span className="operator-apps-dashboard-title">
                          {dashboard.displayName}
                        </span>
                        <code className="path-code">{dashboard.path}</code>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </div>

            <OperatorAlarmBarEditor
              value={alarmBar}
              onChange={setAlarmBar}
              disabled={!canManage}
              dashboardPaths={selectedPaths}
            />

            {canManage && (
              <div className="full operator-apps-actions">
                <button
                  type="button"
                  className="btn primary"
                  disabled={!dirty || saveMutation.isPending || !buildUi()}
                  onClick={handleSave}
                >
                  {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
                </button>
                {saveMutation.error && (
                  <p className="hint error">{String(saveMutation.error)}</p>
                )}
                {saveMutation.isSuccess && !dirty && (
                  <p className="hint">{t("operatorApps.savedHint")}</p>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
