import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlatformMetrics, type PlatformMetricSection } from "../api/platformMetrics";

const METRIC_KEYS = [
  "uptimeMs", "uptimeHuman", "heapUsedBytes", "heapMaxBytes", "heapUsedMb", "heapMaxMb",
  "nonHeapUsedMb", "processors", "threadCount", "peakThreadCount", "poolName",
  "activeConnections", "idleConnections", "totalConnections", "threadsAwaitingConnection",
  "maxPoolSize", "poolAvailable", "objectNodes", "variables", "devices", "dashboards",
  "workflows", "applications", "models", "alerts", "deviceObjects", "activeDrivers",
  "connectedDrivers", "driversWithError", "stoppedDrivers", "websocketClients",
  "platformUsers", "enabledUsers", "activeAuthSessions", "enabled", "minIntervalMs",
  "defaultRetentionDays", "historizedVariables", "sampleCount", "oldestSampleAt",
  "newestSampleAt", "eventHistoryRecords", "workflowInstancesTotal", "workflowInstancesRunning",
  "workflowInstancesCompleted", "workflowInstancesFailed", "workflowInstancesCancelled",
  "alertRules", "eventCorrelators", "applicationFunctions", "applicationFunctionVersions",
  "platformSchedules", "platformSchedulesEnabled",
] as const;

function MetricSectionCard({ section }: { section: PlatformMetricSection }) {
  const { t } = useTranslation(["system", "common"]);
  const entries = Object.entries(section.values ?? {});

  const metricLabel = (key: string) => {
    const labelKey = `metrics.${key}`;
    return METRIC_KEYS.includes(key as (typeof METRIC_KEYS)[number])
      ? t(labelKey)
      : key;
  };

  const formatMetricValue = (value: unknown): string => {
    if (value == null) {
      return t("common:empty.dash");
    }
    if (typeof value === "boolean") {
      return value ? t("common:action.yes") : t("common:action.no");
    }
    if (typeof value === "number") {
      return Number.isInteger(value) ? String(value) : value.toLocaleString();
    }
    return String(value);
  };

  return (
    <section className="system-metrics-card">
      <h3>{section.title}</h3>
      <table className="op-table system-metrics-table">
        <tbody>
          {entries.map(([key, value]) => (
            <tr key={key}>
              <th>{metricLabel(key)}</th>
              <td>
                {typeof value === "string" && value.includes("T") && value.endsWith("Z") ? (
                  <time dateTime={value}>{new Date(value).toLocaleString()}</time>
                ) : (
                  formatMetricValue(value)
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export default function SystemMetricsView({ embedded = false }: { embedded?: boolean }) {
  const { t } = useTranslation("system");
  const metricsQuery = useQuery({
    queryKey: ["platform-metrics"],
    queryFn: fetchPlatformMetrics,
    refetchInterval: 30_000,
  });

  const content = (
    <>
      {!embedded && (
        <header className="system-metrics-header">
          <div>
            <h2>{t("title")}</h2>
            <p className="op-muted">{t("metrics.standaloneSubtitle")}</p>
          </div>
          <button
            type="button"
            className="btn"
            disabled={metricsQuery.isFetching}
            onClick={() => metricsQuery.refetch()}
          >
            {t("metrics.refresh")}
          </button>
        </header>
      )}

      {embedded && (
        <div className="system-embedded-toolbar">
          <button
            type="button"
            className="btn"
            disabled={metricsQuery.isFetching}
            onClick={() => metricsQuery.refetch()}
          >
            {t("metrics.refreshMetrics")}
          </button>
        </div>
      )}

      {metricsQuery.error && (
        <div className="op-alert op-alert-error">{String(metricsQuery.error)}</div>
      )}

      {metricsQuery.isLoading && <p className="hint">{t("metrics.loading")}</p>}

      {metricsQuery.data && (
        <>
          <p className="system-metrics-updated hint">
            {t("metrics.updatedAt", {
              time: new Date(metricsQuery.data.timestamp).toLocaleString(),
            })}
          </p>
          <div className="system-metrics-grid">
            {metricsQuery.data.sections.map((section) => (
              <MetricSectionCard key={section.id} section={section} />
            ))}
          </div>
        </>
      )}
    </>
  );

  if (embedded) {
    return <div className="system-metrics-embedded">{content}</div>;
  }

  return (
    <main className="main system-metrics-view">
      {content}
    </main>
  );
}
