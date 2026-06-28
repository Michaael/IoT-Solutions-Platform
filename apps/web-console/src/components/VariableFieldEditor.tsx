import type { DataSchema } from "../types";

interface FieldEditorProps {
  field: DataSchema["fields"][number];
  value: unknown;
  disabled: boolean;
  onChange: (value: unknown) => void;
}

export default function VariableFieldEditor({
  field,
  value,
  disabled,
  onChange,
}: FieldEditorProps) {
  const id = `field-${field.name}`;

  switch (field.type) {
    case "BOOLEAN":
      return (
        <label className="field-inline" htmlFor={id}>
          <input
            id={id}
            type="checkbox"
            checked={Boolean(value)}
            disabled={disabled}
            onChange={(e) => onChange(e.target.checked)}
          />
          <span>{field.description || field.name}</span>
        </label>
      );
    case "INTEGER":
    case "LONG":
      return (
        <label className="field-block" htmlFor={id}>
          <span className="field-label">{field.description || field.name}</span>
          <input
            id={id}
            type="number"
            step={1}
            value={value === null || value === undefined ? "" : String(value)}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
          />
        </label>
      );
    case "DOUBLE":
      return (
        <label className="field-block" htmlFor={id}>
          <span className="field-label">{field.description || field.name}</span>
          <input
            id={id}
            type="number"
            step="any"
            value={value === null || value === undefined ? "" : String(value)}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
          />
        </label>
      );
    case "STRING":
    case "DATETIME":
      return (
        <label className="field-block" htmlFor={id}>
          <span className="field-label">
            {field.description || field.name}
            <span className="field-type-tag">{field.type}</span>
          </span>
          <input
            id={id}
            type={field.type === "DATETIME" ? "datetime-local" : "text"}
            value={value === null || value === undefined ? "" : String(value)}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value)}
          />
        </label>
      );
    case "RECORD":
    case "RECORD_LIST":
      return (
        <label className="field-block full" htmlFor={id}>
          <span className="field-label">
            {field.description || field.name} ({field.type})
          </span>
          <textarea
            id={id}
            rows={4}
            className="json-editor compact"
            value={value === null || value === undefined ? "" : JSON.stringify(value, null, 2)}
            disabled={disabled}
            onChange={(e) => {
              const raw = e.target.value.trim();
              if (!raw) {
                onChange(null);
                return;
              }
              try {
                onChange(JSON.parse(raw));
              } catch {
                onChange(raw);
              }
            }}
          />
        </label>
      );
    default:
      return (
        <label className="field-block full" htmlFor={id}>
          <span className="field-label">{field.name} ({field.type})</span>
          <textarea
            id={id}
            rows={3}
            className="json-editor compact"
            value={value === null || value === undefined ? "" : JSON.stringify(value, null, 2)}
            disabled={disabled}
            onChange={(e) => {
              try {
                onChange(JSON.parse(e.target.value));
              } catch {
                onChange(e.target.value);
              }
            }}
          />
        </label>
      );
  }
}
