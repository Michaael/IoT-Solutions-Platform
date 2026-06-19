import { useMutation, useQuery } from "@tanstack/react-query";
import { assertBffOk, bffInvoke, toBffInput } from "../../api/bff";
import type { OperatorManifestScreen } from "../../types/operatorManifest";
import BffDataTable from "./BffDataTable";

interface ManifestScreenProps {
  screen: OperatorManifestScreen;
  wireProfile: string;
  onStatus: (message: string | null) => void;
}

export default function ManifestScreen({ screen, wireProfile, onStatus }: ManifestScreenProps) {
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

  const actionMutation = useMutation({
    mutationFn: async (actionId: string) => {
      const action = screen.actions?.find((item) => item.id === actionId);
      if (!action) {
        throw new Error(`Unknown action: ${actionId}`);
      }
      const wire = await bffInvoke({
        objectPath: action.objectPath,
        functionName: action.functionName,
        input: toBffInput(action.input),
        wireProfile,
      });
      return { action, result: assertBffOk(wire) };
    },
    onSuccess: ({ action }) => {
      onStatus(action.successMessage ?? `Выполнено: ${action.label}`);
      tableQuery.refetch();
    },
    onError: (error) => onStatus(String(error)),
  });

  return (
    <section className="op-panel">
      <h2 className="op-panel-title">{screen.title}</h2>
      {screen.description && <p className="op-muted">{screen.description}</p>}

      {screen.actions && screen.actions.length > 0 && (
        <div className="op-toolbar">
          {screen.actions.map((action) => (
            <button
              key={action.id}
              type="button"
              className="btn primary"
              disabled={actionMutation.isPending}
              onClick={() => actionMutation.mutate(action.id)}
            >
              {action.label}
            </button>
          ))}
          {screen.table && (
            <button type="button" className="btn" onClick={() => tableQuery.refetch()}>
              Обновить
            </button>
          )}
        </div>
      )}

      {tableQuery.error && <div className="op-alert op-alert-error">{String(tableQuery.error)}</div>}
      {tableQuery.data && (
        <BffDataTable
          rows={tableQuery.data.rows}
          labels={tableQuery.data.labels}
          emptyMessage={screen.table?.emptyMessage}
        />
      )}
    </section>
  );
}
