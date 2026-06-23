import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchObjectEditor, fetchObjects, invokeFunction } from "../../../api";
import type { FunctionFormField, FunctionFormWidget } from "../../../types/dashboard";
import { buildFunctionInput, resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface FunctionFormWidgetViewProps {
  widget: FunctionFormWidget;
  editable?: boolean;
}

export default function FunctionFormWidgetView({ widget, editable }: FunctionFormWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);

  const parsedFields = useMemo(() => {
    try {
      return widget.fieldsJson ? (JSON.parse(widget.fieldsJson) as FunctionFormField[]) : [];
    } catch {
      return [] as FunctionFormField[];
    }
  }, [widget.fieldsJson]);

  const [values, setValues] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const editor = useQuery({
    queryKey: ["object-editor", objectPath],
    queryFn: () => fetchObjectEditor(objectPath),
    enabled: Boolean(objectPath),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (!objectPath || !widget.functionName) {
        throw new Error(t("error.objectAndFunctionRequired"));
      }
      const fn = editor.data?.functions.find((f) => f.name === widget.functionName);
      const input = buildFunctionInput(parsedFields, values, fn?.inputSchema);
      return invokeFunction(objectPath, widget.functionName, input);
    },
    onSuccess: (result) => {
      const row = result.rows?.[0];
      if (row?.success === false) {
        setError(String(row.message ?? t("view.errorGeneric")));
        setMessage(null);
        return;
      }
      setMessage(String(row?.message ?? t("view.done")));
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["events"] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editable) return;
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) return;
    mutation.mutate();
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget function-form-widget"
      editable={editable}
    >
      {!objectPath && <p className="hint">{t("view.selectObjectInTable")}</p>}
      <form className="function-form-fields" style={styles.body} onSubmit={handleSubmit}>
        {parsedFields.map((field) => (
          <FunctionFormFieldInput
            key={field.name}
            field={field}
            value={values[field.name] ?? field.defaultValue ?? ""}
            disabled={editable || !objectPath}
            onChange={(v) => setValues((prev) => ({ ...prev, [field.name]: v }))}
          />
        ))}
        <button
          type="submit"
          className="btn primary function-widget-btn"
          disabled={editable || mutation.isPending || !objectPath}
        >
          {mutation.isPending ? "…" : widget.buttonLabel ?? widget.functionName}
        </button>
      </form>
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}

function FunctionFormFieldInput({
  field,
  value,
  disabled,
  onChange,
}: {
  field: FunctionFormField;
  value: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  const children = useQuery({
    queryKey: ["objects", field.optionsFrom],
    queryFn: () => fetchObjects(field.optionsFrom!),
    enabled: field.type === "select" && Boolean(field.optionsFrom),
  });

  if (field.type === "select") {
    const options = field.staticOptions?.length
      ? field.staticOptions
      : (children.data ?? []).map((obj) => obj.path.split(".").pop() ?? obj.displayName);
    return (
      <label className="function-form-label">
        {field.label}
        <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {options.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </label>
    );
  }

  return (
    <label className="function-form-label">
      {field.label}
      <input
        type={field.type === "number" ? "number" : "text"}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}
