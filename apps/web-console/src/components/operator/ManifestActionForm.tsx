import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { assertBffOk, bffInvoke, toBffInput } from "../../api/bff";
import { isActionVisible } from "../../api/manifestVisibility";
import type {
  OperatorManifestAction,
  OperatorManifestField,
  OperatorManifestOptionsFrom,
} from "../../types/operatorManifest";

interface ManifestActionFormProps {
  action: OperatorManifestAction;
  wireProfile: string;
  selectedRow: Record<string, unknown> | null;
  disabled?: boolean;
  onSubmit: (actionId: string, values: Record<string, unknown>) => void;
}

function useOptionsFrom(source: OperatorManifestOptionsFrom | undefined, wireProfile: string) {
  return useQuery({
    queryKey: ["manifest-options", source?.objectPath, source?.functionName, source?.input],
    enabled: Boolean(source),
    queryFn: async () => {
      const wire = await bffInvoke<Array<Record<string, unknown>> | Record<string, unknown>>({
        objectPath: source!.objectPath,
        functionName: source!.functionName,
        input: toBffInput(source!.input),
        wireProfile,
      });
      const result = assertBffOk(wire);
      const rows = Array.isArray(result) ? result : [result as Record<string, unknown>];
      let filtered = rows;
      if (source!.filterField) {
        filtered = rows.filter((row) => {
          const actual = row[source!.filterField!];
          const expected = source!.filterValue;
          if (typeof expected === "boolean") {
            return actual === expected || String(actual).toLowerCase() === String(expected);
          }
          return String(actual) === String(expected);
        });
      }
      return filtered.map((row) => ({
        value: String(row[source!.valueField] ?? ""),
        label: String(row[source!.labelField] ?? row[source!.valueField] ?? ""),
      }));
    },
  });
}

function FieldControl({
  field,
  value,
  onChange,
  wireProfile,
}: {
  field: OperatorManifestField;
  value: unknown;
  onChange: (next: unknown) => void;
  wireProfile: string;
}) {
  const { t } = useTranslation("platform");
  const optionsQuery = useOptionsFrom(field.optionsFrom, wireProfile);
  const options = field.options ?? optionsQuery.data ?? [];
  const locked = Boolean(field.readOnly);

  if (field.type === "TEXT") {
    return (
      <textarea
        className="op-input op-textarea"
        rows={3}
        placeholder={field.placeholder}
        value={String(value ?? "")}
        readOnly={locked}
        onChange={(event) => {
          if (!locked) {
            onChange(event.target.value);
          }
        }}
      />
    );
  }

  if (field.type === "SELECT" || field.optionsFrom || field.options) {
    return (
      <select
        className="op-input"
        value={String(value ?? "")}
        disabled={locked}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{t("sqlBinding.selectPlaceholder")}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }

  const inputType = field.type === "DOUBLE" || field.type === "LONG" || field.type === "INTEGER" ? "number" : "text";
  return (
    <input
      className="op-input"
      type={inputType}
      placeholder={field.placeholder}
      value={value === undefined || value === null ? "" : String(value)}
      readOnly={locked}
      onChange={(event) => {
        if (!locked) {
          onChange(field.type === "DOUBLE" ? event.target.value : event.target.value);
        }
      }}
    />
  );
}

export default function ManifestActionForm({
  action,
  wireProfile,
  selectedRow,
  disabled,
  onSubmit,
}: ManifestActionFormProps) {
  const [values, setValues] = useState<Record<string, unknown>>({});

  useEffect(() => {
    const next: Record<string, unknown> = {};
    for (const field of action.fields ?? []) {
      if (field.default !== undefined) {
        next[field.name] = field.default;
      }
      if (field.bindFromSelection && selectedRow) {
        next[field.name] = selectedRow[field.bindFromSelection];
      }
    }
    setValues(next);
  }, [action, selectedRow]);

  const visible = useMemo(() => isActionVisible(action, selectedRow), [action, selectedRow]);

  if (!visible || !action.fields || action.fields.length === 0) {
    return null;
  }

  const canSubmit = !disabled && !(action.requiresSelection && !selectedRow);

  return (
    <div className="op-action-card">
      <h3 className="op-action-title">{action.label}</h3>
      <div className="op-form-grid">
        {action.fields
          .filter((field) => !field.hidden)
          .map((field) => (
            <label key={field.name} className="op-field">
              <span className="op-field-label">
                {field.label ?? field.name}
                {field.required ? " *" : ""}
              </span>
              <FieldControl
                field={field}
                wireProfile={wireProfile}
                value={values[field.name]}
                onChange={(next) => setValues((prev) => ({ ...prev, [field.name]: next }))}
              />
            </label>
          ))}
      </div>
      <button
        type="button"
        className="btn primary"
        disabled={!canSubmit}
        onClick={() => onSubmit(action.id, values)}
      >
        {action.label}
      </button>
    </div>
  );
}
