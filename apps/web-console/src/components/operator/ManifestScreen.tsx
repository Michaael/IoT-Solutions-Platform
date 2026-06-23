import { useCallback, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { assertBffOk, bffInvoke, toBffInput } from "../../api/bff";
import { downloadReportExport, runReport, type ReportExportFormat } from "../../api/reports";
import ReportExportControls from "../report/ReportExportControls";
import { invokeInputFromAction, validateActionInput } from "../../api/manifestInput";
import { isActionVisible } from "../../api/manifestVisibility";
import type { OperatorManifestScreen } from "../../types/operatorManifest";
import { selectionKeyForTable } from "../../types/operatorManifest";
import BffDataTable from "./BffDataTable";
import ManifestActionForm from "./ManifestActionForm";

interface ManifestScreenProps {
  screen: OperatorManifestScreen;
  wireProfile: string;
  appId: string;
  onStatus: (message: string | null) => void;
}

export default function ManifestScreen({ screen, wireProfile, appId, onStatus }: ManifestScreenProps) {
  const selectionKey = selectionKeyForTable(screen.table);
  const [selectedRow, setSelectedRow] = useState<Record<string, unknown> | null>(null);
  const selectedKey = selectedRow ? String(selectedRow[selectionKey] ?? "") : null;

  const tableQuery = useQuery({
    queryKey: ["bff-table", screen.id, screen.table?.objectPath, screen.table?.functionName, screen.table?.input],
    enabled: Boolean(screen.table),
    refetchInterval: screen.table?.refreshIntervalMs,
    queryFn: async () => {
      const table = screen.table!;
      const wire = await bffInvoke<Array<Record<string, unknown>> | Record<string, unknown>>({
        objectPath: table.objectPath,
        functionName: table.functionName,
        input: toBffInput(table.input),
        wireProfile,
      });
      const result = assertBffOk(wire);
      if (Array.isArray(result)) {
        return { rows: result, labels: wire.result_field_labels };
      }
      return { rows: [result as Record<string, unknown>], labels: wire.result_field_labels };
    },
  });

  const reportQuery = useQuery({
    queryKey: ["app-report", appId, screen.report?.reportId, screen.report?.parameters],
    enabled: Boolean(screen.report),
    refetchInterval: screen.report?.refreshIntervalMs,
    queryFn: async () => {
      const report = screen.report!;
      const result = await runReport(appId, report.reportId, report.parameters);
      const labels = Object.fromEntries(result.columns.map((col) => [col.field, col.label]));
      return { rows: result.rows, labels, truncated: result.truncated };
    },
  });

  const [exportBusy, setExportBusy] = useState(false);

  const exportReport = useCallback(
    async (format: ReportExportFormat) => {
      if (!screen.report) {
        return;
      }
      setExportBusy(true);
      try {
        const params = Object.fromEntries(
          Object.entries(screen.report.parameters ?? {}).map(([key, value]) => [key, String(value)])
        );
        await downloadReportExport(appId, screen.report.reportId, format, params);
        onStatus(`${format.toUpperCase()} экспортирован`);
      } catch (error) {
        onStatus(String(error));
      } finally {
        setExportBusy(false);
      }
    },
    [appId, onStatus, screen.report]
  );

  const actionMutation = useMutation({
    mutationFn: async ({ actionId, formValues }: { actionId: string; formValues: Record<string, unknown> }) => {
      const action = screen.actions?.find((item) => item.id === actionId);
      if (!action) {
        throw new Error(`Unknown action: ${actionId}`);
      }
      const validationError = validateActionInput(action, formValues, selectedRow);
      if (validationError) {
        throw new Error(validationError);
      }
      const wire = await bffInvoke({
        objectPath: action.objectPath,
        functionName: action.functionName,
        input: invokeInputFromAction(action, formValues, selectedRow),
        wireProfile,
      });
      return { action, result: assertBffOk(wire) };
    },
    onSuccess: ({ action }) => {
      onStatus(action.successMessage ?? `Выполнено: ${action.label}`);
      tableQuery.refetch();
      setSelectedRow(null);
    },
    onError: (error) => onStatus(String(error)),
  });

  const runSimpleAction = useCallback(
    (actionId: string) => {
      const action = screen.actions?.find((item) => item.id === actionId);
      if (!action) {
        return;
      }
      const validationError = validateActionInput(action, {}, selectedRow);
      if (validationError) {
        onStatus(validationError);
        return;
      }
      actionMutation.mutate({ actionId, formValues: {} });
    },
    [actionMutation, onStatus, screen.actions, selectedRow]
  );

  const simpleActions = useMemo(
    () => screen.actions?.filter((action) => !action.fields || action.fields.length === 0) ?? [],
    [screen.actions]
  );

  const formActions = useMemo(
    () => screen.actions?.filter((action) => action.fields && action.fields.length > 0) ?? [],
    [screen.actions]
  );

  const selectable = Boolean(screen.table?.selectable);

  return (
    <section className="op-panel">
      <h2 className="op-panel-title">{screen.title}</h2>
      {screen.description && <p className="op-muted">{screen.description}</p>}

      {(simpleActions.length > 0 || screen.table || screen.report) && (
        <div className="op-toolbar">
          {simpleActions.map((action) => {
            const disabled =
              actionMutation.isPending ||
              (action.requiresSelection && !selectedRow) ||
              !isActionVisible(action, selectedRow);
            return (
              <button
                key={action.id}
                type="button"
                className="btn primary"
                disabled={Boolean(disabled)}
                onClick={() => runSimpleAction(action.id)}
              >
                {action.label}
              </button>
            );
          })}
          {screen.table && (
            <button type="button" className="btn" onClick={() => tableQuery.refetch()}>
              Обновить
            </button>
          )}
          {screen.report && (
            <>
              <button type="button" className="btn" onClick={() => reportQuery.refetch()}>
                Обновить
              </button>
              <ReportExportControls busy={exportBusy} onExport={exportReport} />
            </>
          )}
        </div>
      )}

      {formActions.map((action) => (
        <ManifestActionForm
          key={action.id}
          action={action}
          wireProfile={wireProfile}
          selectedRow={selectedRow}
          disabled={actionMutation.isPending}
          onSubmit={(actionId, formValues) => actionMutation.mutate({ actionId, formValues })}
        />
      ))}

      {selectable && (
        <p className="op-muted">
          {selectedRow
            ? `Выбрано: ${String(selectedRow.order_number ?? selectedRow[selectionKey] ?? "—")}`
            : "Выберите строку в таблице ниже."}
        </p>
      )}

      {tableQuery.error && <div className="op-alert op-alert-error">{String(tableQuery.error)}</div>}
      {reportQuery.error && <div className="op-alert op-alert-error">{String(reportQuery.error)}</div>}
      {reportQuery.data?.truncated && (
        <div className="op-alert op-alert-info">Показаны не все строки (лимит отчёта).</div>
      )}
      {tableQuery.data && (
        <BffDataTable
          rows={tableQuery.data.rows}
          labels={tableQuery.data.labels}
          emptyMessage={screen.table?.emptyMessage}
          selectable={selectable}
          selectionKey={selectionKey}
          selectedKey={selectedKey}
          onSelect={setSelectedRow}
        />
      )}

      {reportQuery.data && (
        <BffDataTable
          rows={reportQuery.data.rows}
          labels={reportQuery.data.labels}
          emptyMessage={screen.report?.emptyMessage}
        />
      )}
    </section>
  );
}
