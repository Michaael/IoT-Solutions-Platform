import { useTranslation } from "react-i18next";
import type { DataRecord } from "../../types";
import { cloneRecord, setFieldValue } from "../../utils/record";
import { defaultForFieldType } from "../../utils/dataSchema";
import VariableFieldEditor from "../VariableFieldEditor";

interface DataRecordValueEditorProps {
  record: DataRecord;
  onChange: (next: DataRecord) => void;
  disabled?: boolean;
  allowMultipleRows?: boolean;
}

export default function DataRecordValueEditor({
  record,
  onChange,
  disabled = false,
  allowMultipleRows = false,
}: DataRecordValueEditorProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const multiRow = allowMultipleRows || record.rows.length > 1;

  function updateRow(rowIndex: number, nextRow: Record<string, unknown>) {
    const rows = record.rows.map((row, i) => (i === rowIndex ? nextRow : row));
    onChange({ schema: record.schema, rows });
  }

  function patchField(rowIndex: number, fieldName: string, value: unknown) {
    const base = record.rows[rowIndex] ?? {};
    const rowRecord = setFieldValue(
      { schema: record.schema, rows: [base] },
      fieldName,
      value
    );
    updateRow(rowIndex, rowRecord.rows[0] ?? {});
  }

  function addRow() {
    const row: Record<string, unknown> = {};
    for (const field of record.schema.fields) {
      row[field.name] = defaultForFieldType(field.type);
    }
    onChange({ schema: record.schema, rows: [...record.rows, row] });
  }

  function removeRow(index: number) {
    onChange({
      schema: record.schema,
      rows: record.rows.filter((_, i) => i !== index),
    });
  }

  if (record.schema.fields.length === 0) {
    return <p className="hint">{t("objectEditor.emptyVariableSchema")}</p>;
  }

  const rows = record.rows.length > 0 ? record.rows : [{}];

  return (
    <div className="data-record-value-editor">
      {rows.map((row, rowIndex) => (
        <article key={rowIndex} className="data-record-row-card">
          {multiRow && (
            <header className="data-record-row-header">
              <span className="field-label">
                {t("schemaEditor.rowLabel", { index: rowIndex + 1 })}
              </span>
              {!disabled && record.rows.length > 1 && (
                <button
                  type="button"
                  className="btn tiny danger"
                  onClick={() => removeRow(rowIndex)}
                >
                  {t("schemaEditor.removeRow")}
                </button>
              )}
            </header>
          )}
          <div className="property-fields">
            {record.schema.fields.map((field) => (
              <VariableFieldEditor
                key={`${rowIndex}-${field.name}`}
                field={field}
                value={row[field.name]}
                disabled={disabled}
                onChange={(val) => patchField(rowIndex, field.name, val)}
              />
            ))}
          </div>
        </article>
      ))}

      {!disabled && multiRow && (
        <button type="button" className="btn small" onClick={addRow}>
          {t("schemaEditor.addRow")}
        </button>
      )}
    </div>
  );
}

export function recordFromEditor(record: DataRecord): DataRecord {
  return cloneRecord(record);
}
