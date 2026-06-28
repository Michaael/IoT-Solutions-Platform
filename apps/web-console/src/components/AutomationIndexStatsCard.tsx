import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAutomationIndexStats } from "../api/automationIndex";

export default function AutomationIndexStatsCard() {
  const { t } = useTranslation(["system", "common"]);
  const statsQuery = useQuery({
    queryKey: ["automation-index-stats"],
    queryFn: fetchAutomationIndexStats,
    refetchInterval: 30_000,
  });

  return (
    <section className="system-metrics-card automation-index-card">
      <h3>{t("automationIndex.title")}</h3>
      {statsQuery.isLoading && <p className="hint">{t("automationIndex.loading")}</p>}
      {statsQuery.error && (
        <div className="op-alert op-alert-error">{t("automationIndex.loadError")}</div>
      )}
      {statsQuery.data && (
        <table className="op-table system-metrics-table">
          <tbody>
            <tr>
              <th>{t("automationIndex.alertRulesIndexed")}</th>
              <td>{statsQuery.data.alertRulesIndexed}</td>
            </tr>
            <tr>
              <th>{t("automationIndex.correlatorsIndexed")}</th>
              <td>{statsQuery.data.correlatorsIndexed}</td>
            </tr>
            <tr>
              <th>{t("automationIndex.workflowTriggersIndexed")}</th>
              <td>{statsQuery.data.workflowTriggersIndexed}</td>
            </tr>
            <tr>
              <th>{t("automationIndex.lastRebuildAt")}</th>
              <td>
                {statsQuery.data.lastRebuildAt ? (
                  <time dateTime={statsQuery.data.lastRebuildAt}>
                    {new Date(statsQuery.data.lastRebuildAt).toLocaleString()}
                  </time>
                ) : (
                  t("common:empty.dash")
                )}
              </td>
            </tr>
          </tbody>
        </table>
      )}
    </section>
  );
}
