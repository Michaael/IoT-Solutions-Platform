import { Fragment } from "react";
import { useTranslation } from "react-i18next";
import type { DataSchema } from "../../types";
import {
  isNestedFieldType,
  newSchemaField,
  SCHEMA_FIELD_TYPES,
  type SchemaField,
} from "../../utils/dataSchema";

interface DataSchemaEditorProps {
  value: DataSchema;
  onChange: (next: DataSchema) => void;
  disabled?: boolean;
  idPrefix?: string;
  showSchemaName?: boolean;
}

function patchField(fields: SchemaField[], index: number, patch: Partial<SchemaField>): SchemaField[] {
  return fields.map((field, i) => (i === index ? { ...field, ...patch } : field));
}

export default function DataSchemaEditor({
  value,
  onChange,
  disabled = false,
  idPrefix = "schema",
  showSchemaName = true,
}: DataSchemaEditorProps) {
  const { t } = useTranslation(["inspector", "common"]);

  function updateField(index: number, patch: Partial<SchemaField>) {
    onChange({ ...value, fields: patchField(value.fields, index, patch) });
  }

  function removeField(index: number) {
    onChange({ ...value, fields: value.fields.filter((_, i) => i !== index) });
  }

  function moveField(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= value.fields.length) {
      return;
    }
    const fields = [...value.fields];
    const [item] = fields.splice(index, 1);
    fields.splice(target, 0, item);
    onChange({ ...value, fields });
  }

  return (
    <div className="data-schema-editor">
      {showSchemaName && (
        <label className="field-block full">
          <span className="field-label">{t("schemaEditor.schemaName")}</span>
          <input
            value={value.name}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
          />
        </label>
      )}

      {value.fields.length === 0 && (
        <p className="hint">{t("schemaEditor.emptyFields")}</p>
      )}

      {value.fields.length > 0 && (
        <div className="table-scroll">
          <table className="data-table data-schema-fields-table">
            <thead>
              <tr>
                <th>{t("common:table.name")}</th>
                <th>{t("common:table.type")}</th>
                <th>{t("common:table.description")}</th>
                <th>{t("schemaEditor.nullable")}</th>
                <th aria-label={t("common:table.actions")} />
              </tr>
            </thead>
            <tbody>
              {value.fields.map((field, index) => (
                <Fragment key={`${idPrefix}-${field.name}-${index}`}>
                  <tr>
                    <td>
                      <input
                        className="model-inline-input mono"
                        value={field.name}
                        disabled={disabled}
                        onChange={(e) => updateField(index, { name: e.target.value })}
                      />
                    </td>
                    <td>
                      <select
                        value={field.type}
                        disabled={disabled}
                        onChange={(e) => {
                          const type = e.target.value;
                          const patch: Partial<SchemaField> = { type };
                          if (isNestedFieldType(type) && !field.nestedSchema) {
                            patch.nestedSchema = { name: `${field.name}Item`, fields: [] };
                          }
                          if (!isNestedFieldType(type)) {
                            patch.nestedSchema = undefined;
                          }
                          updateField(index, patch);
                        }}
                      >
                        {SCHEMA_FIELD_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        className="model-inline-input"
                        value={field.description ?? ""}
                        disabled={disabled}
                        placeholder={t("schemaEditor.descriptionPlaceholder")}
                        onChange={(e) => updateField(index, { description: e.target.value })}
                      />
                    </td>
                    <td className="schema-null-cell">
                      <input
                        type="checkbox"
                        checked={field.nullable !== false}
                        disabled={disabled}
                        onChange={(e) => updateField(index, { nullable: e.target.checked })}
                      />
                    </td>
                    <td className="schema-actions-cell">
                      {!disabled && (
                        <span className="schema-field-actions">
                          <button
                            type="button"
                            className="btn tiny"
                            title={t("schemaEditor.moveUp")}
                            disabled={index === 0}
                            onClick={() => moveField(index, -1)}
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            className="btn tiny"
                            title={t("schemaEditor.moveDown")}
                            disabled={index === value.fields.length - 1}
                            onClick={() => moveField(index, 1)}
                          >
                            ↓
                          </button>
                          <button
                            type="button"
                            className="btn tiny danger"
                            title={t("common:action.delete")}
                            onClick={() => removeField(index)}
                          >
                            ✕
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                  {isNestedFieldType(field.type) && field.nestedSchema && (
                    <tr className="schema-nested-row">
                      <td colSpan={5}>
                        <div className="schema-nested-panel">
                          <span className="field-label">{t("schemaEditor.nestedSchema")}</span>
                          <DataSchemaEditor
                            value={field.nestedSchema}
                            onChange={(nestedSchema) => updateField(index, { nestedSchema })}
                            disabled={disabled}
                            idPrefix={`${idPrefix}-nested-${index}`}
                            showSchemaName={false}
                          />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!disabled && (
        <button
          type="button"
          className="btn small"
          onClick={() =>
            onChange({
              ...value,
              fields: [...value.fields, newSchemaField(`field${value.fields.length + 1}`)],
            })
          }
        >
          {t("schemaEditor.addField")}
        </button>
      )}
    </div>
  );
}
