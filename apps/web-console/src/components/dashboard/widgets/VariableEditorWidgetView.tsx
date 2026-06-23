import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariables, setVariable } from "../../../api";
import type { VariableEditorWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { ensureRecord, setFieldValue } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface VariableEditorWidgetViewProps {
  widget: VariableEditorWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function VariableEditorWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: VariableEditorWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const queryClient = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const filterNames = useMemo(() => {
    if (!widget.variablesJson?.trim()) {
      return null;
    }
    try {
      const parsed = JSON.parse(widget.variablesJson) as string[];
      return Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }, [widget.variablesJson]);

  const variablesQuery = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath),
    refetchInterval: refreshIntervalMs,
  });

  const variables = useMemo(() => {
    const all = variablesQuery.data ?? [];
    if (!filterNames || filterNames.length === 0) {
      return all;
    }
    const allowed = new Set(filterNames);
    return all.filter((item) => allowed.has(item.name));
  }, [filterNames, variablesQuery.data]);

  const mutation = useMutation({
    mutationFn: async ({ name, field, raw }: { name: string; field: string; raw: string }) => {
      const variable = variables.find((item) => item.name === name);
      if (!variable?.writable) {
        throw new Error(t("error.readOnlyVariable", { name }));
      }
      const record = setFieldValue(ensureRecord(variable), field, raw);
      return setVariable(objectPath, name, record);
    },
    onSuccess: () => {
      setMessage(t("view.saved"));
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-variable-editor"
      editable={editable}
    >
      {!objectPath ? (
        <p className="hint">{t("view.specifyObjectGeneric")}</p>
      ) : variablesQuery.isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : variables.length === 0 ? (
        <p className="hint">{t("view.noVariables")}</p>
      ) : (
        <div className="dash-variable-editor-list" style={styles.body}>
          {variables.map((variable) => {
            const row = variable.value?.rows[0];
            const primaryField =
              variable.value?.schema.fields[0]?.name ?? widget.valueField ?? "value";
            const current = readFieldValue(row, primaryField);
            const draftKey = `${variable.name}:${primaryField}`;
            const draft = drafts[draftKey] ?? (current != null ? String(current) : "");

            return (
              <label key={variable.name} className="dash-variable-editor-row">
                <span className="dash-variable-editor-name">{variable.name}</span>
                <input
                  value={draft}
                  disabled={editable || !variable.writable || mutation.isPending}
                  onChange={(event) =>
                    setDrafts((prev) => ({ ...prev, [draftKey]: event.target.value }))
                  }
                />
                <button
                  type="button"
                  className="btn small"
                  disabled={
                    editable ||
                    !variable.writable ||
                    mutation.isPending ||
                    draft === (current != null ? String(current) : "")
                  }
                  onClick={() =>
                    mutation.mutate({
                      name: variable.name,
                      field: primaryField,
                      raw: draft,
                    })
                  }
                >
                  {t("common:action.save")}
                </button>
              </label>
            );
          })}
        </div>
      )}
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}
