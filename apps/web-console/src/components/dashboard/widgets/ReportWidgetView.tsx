import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  downloadReportExportByPath,
  fetchReport,
  runReportByPath,
  type ReportExportFormat,
} from "../../../api/reports";
import type { ReportWidget } from "../../../types/dashboard";
import { parseJsonObject } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import BffDataTable from "../../operator/BffDataTable";
import ReportExportControls from "../../report/ReportExportControls";
import { filterReportExportOptions } from "../../report/reportExportOptions";

interface ReportWidgetViewProps {
  widget: ReportWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

function mergeReportRunParameters(
  widget: ReportWidget,
  sessionParams: Record<string, unknown>,
  defaultParameters?: Record<string, unknown>
): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...(defaultParameters ?? {}) };
  const staticParams = parseJsonObject(widget.parametersJson);
  if (staticParams) {
    Object.assign(merged, staticParams);
  }
  const contextMap = parseJsonObject(widget.contextParamsJson);
  if (contextMap) {
    for (const [reportParam, sessionKeyRaw] of Object.entries(contextMap)) {
      const sessionKey = String(sessionKeyRaw).trim();
      if (!sessionKey) continue;
      const value = sessionParams[sessionKey];
      if (value !== undefined && value !== null && String(value).trim() !== "") {
        merged[reportParam] = value;
      }
    }
  }
  return merged;
}

function exportEnabled(widget: ReportWidget, format: ReportExportFormat): boolean {
  if (format === "csv") return widget.showCsv !== false;
  if (format === "html") return widget.showHtml !== false;
  if (format === "xlsx") return widget.showXlsx !== false;
  if (format === "pdf") return widget.showPdf !== false;
  return false;
}

export default function ReportWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ReportWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const { params: sessionParams } = useDashboardContext();
  const [exportBusy, setExportBusy] = useState(false);

  const reportMetaQuery = useQuery({
    queryKey: ["report", widget.reportPath],
    queryFn: () => fetchReport(widget.reportPath),
    enabled: Boolean(widget.reportPath?.trim()),
  });

  const runParameters = useMemo(
    () =>
      mergeReportRunParameters(
        widget,
        sessionParams,
        reportMetaQuery.data?.defaultParameters
      ),
    [widget, sessionParams, reportMetaQuery.data?.defaultParameters]
  );

  const runQuery = useQuery({
    queryKey: ["report-widget", widget.reportPath, runParameters],
    queryFn: () => runReportByPath(widget.reportPath, runParameters),
    enabled: Boolean(widget.reportPath?.trim()),
    refetchInterval: editable ? false : refreshIntervalMs,
  });

  const labels = useMemo(() => {
    const map: Record<string, string> = {};
    for (const col of runQuery.data?.columns ?? []) {
      map[col.field] = col.label;
    }
    return map;
  }, [runQuery.data?.columns]);

  const exportParams = Object.fromEntries(
    Object.entries(runParameters).map(([key, value]) => [key, String(value)])
  );

  const exportOptions = useMemo(
    () => filterReportExportOptions((format) => exportEnabled(widget, format)),
    [widget]
  );

  async function handleExport(format: ReportExportFormat) {
    setExportBusy(true);
    try {
      await downloadReportExportByPath(widget.reportPath, format, exportParams);
    } finally {
      setExportBusy(false);
    }
  }

  return (
    <div className="dash-widget dash-report-widget">
      <div className="dash-widget-title">
        {widget.title}
        {exportOptions.length > 0 && !editable && (
          <span className="dash-report-toolbar">
            <ReportExportControls
              options={exportOptions}
              size="sm"
              busy={exportBusy}
              disabled={!widget.reportPath?.trim() || runQuery.isLoading}
              onExport={handleExport}
            />
          </span>
        )}
      </div>
      <div className="dash-widget-body">
        {!widget.reportPath?.trim() && <p className="hint">{t("view.specifyReportPath")}</p>}
        {editable && widget.reportPath?.trim() && (
          <p className="hint">{t("view.reportPreview")}</p>
        )}
        {runQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
        {runQuery.error && <p className="hint error">{(runQuery.error as Error).message}</p>}
        {runQuery.data && (
          <>
            {runQuery.data.truncated && widget.showTruncatedWarning !== false && (
              <div className="banner warning report-truncated-banner">
                {t("view.reportTruncated", { count: runQuery.data.rowCount })}
              </div>
            )}
            <BffDataTable
              rows={runQuery.data.rows}
              labels={labels}
              emptyMessage={widget.emptyMessage ?? t("empty.noRows")}
            />
          </>
        )}
      </div>
    </div>
  );
}
