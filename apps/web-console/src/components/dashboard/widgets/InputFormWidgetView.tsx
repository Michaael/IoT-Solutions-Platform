import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchObjects, fetchVariables, setVariable } from "../../../api";
import type { InputFormField, InputFormWidget } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";

interface InputFormWidgetViewProps {
  widget: InputFormWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function InputFormWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: InputFormWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const queryClient = useQueryClient();
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const fields = useMemo(
    () => parseJsonArray<InputFormField>(widget.fieldsJson, []),
    [widget.fieldsJson]
  );
  const [values, setValues] = useState<Record<string, string>>({});

  const variables = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath),
    refetchInterval: refreshIntervalMs,
  });

  const children = useQuery({
    queryKey: ["objects", fields.find((f) => f.optionsFrom)?.optionsFrom],
    queryFn: () => fetchObjects(fields.find((f) => f.optionsFrom)?.optionsFrom),
    enabled: fields.some((f) => Boolean(f.optionsFrom)),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      for (const field of fields) {
        const varName = field.variableName ?? field.name;
        const variable = variables.data?.find((v) => v.name === varName);
        if (!variable?.value) continue;
        const raw = values[field.name] ?? field.defaultValue ?? "";
        let next: unknown = raw;
        if (field.type === "number" || field.type === "slider") {
          next = Number(raw);
        } else if (field.type === "checkbox") {
          next = raw === "true";
        }
        const record = setFieldValue(cloneRecord(variable.value), "value", next);
        await setVariable(objectPath, varName, record);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-input-form"
      editable={editable}
    >
      {!objectPath ? (
        <p className="hint">{t("view.specifyObjectPath")}</p>
      ) : (
        <div className="function-form-fields" style={styles.body}>
          {fields.map((field) => (
            <label key={field.name}>
              {field.label}
              {field.type === "textarea" ? (
                <textarea
                  value={values[field.name] ?? field.defaultValue ?? ""}
                  onChange={(e) =>
                    setValues((prev) => ({ ...prev, [field.name]: e.target.value }))
                  }
                />
              ) : field.type === "select" ? (
                <select
                  value={values[field.name] ?? field.defaultValue ?? ""}
                  onChange={(e) =>
                    setValues((prev) => ({ ...prev, [field.name]: e.target.value }))
                  }
                >
                  {(field.staticOptions ?? children.data?.map((o) => o.path) ?? []).map(
                    (opt) => (
                      <option key={opt} value={opt}>
                        {opt}
                      </option>
                    )
                  )}
                </select>
              ) : field.type === "slider" ? (
                <input
                  type="range"
                  min={field.min ?? 0}
                  max={field.max ?? 100}
                  step={field.step ?? 1}
                  value={values[field.name] ?? field.defaultValue ?? field.min ?? 0}
                  onChange={(e) =>
                    setValues((prev) => ({ ...prev, [field.name]: e.target.value }))
                  }
                />
              ) : (
                <input
                  type={field.type === "number" ? "number" : field.type === "datetime" ? "datetime-local" : field.type === "time" ? "time" : "text"}
                  value={values[field.name] ?? field.defaultValue ?? ""}
                  onChange={(e) =>
                    setValues((prev) => ({ ...prev, [field.name]: e.target.value }))
                  }
                />
              )}
            </label>
          ))}
          <button
            type="button"
            className="btn primary"
            disabled={editable || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {widget.buttonLabel ?? t("view.apply")}
          </button>
        </div>
      )}
    </DashWidgetShell>
  );
}
