import type { DataRecord, DataSchema } from "../types";

export const SCHEMA_FIELD_TYPES = [
  "BOOLEAN",
  "INTEGER",
  "LONG",
  "DOUBLE",
  "STRING",
  "DATETIME",
  "BINARY",
  "RECORD",
  "RECORD_LIST",
] as const;

export type SchemaFieldType = (typeof SCHEMA_FIELD_TYPES)[number];

export type SchemaField = DataSchema["fields"][number] & {
  nestedSchema?: DataSchema | null;
};

export function emptySchema(name = "schema"): DataSchema {
  return { name, fields: [] };
}

export function newSchemaField(name = "field"): SchemaField {
  return { name, type: "STRING", description: "", nullable: true };
}

export function cloneSchema(schema: DataSchema): DataSchema {
  return JSON.parse(JSON.stringify(schema)) as DataSchema;
}

export function schemaFromRecord(record: DataRecord | null | undefined, fallbackName: string): DataSchema {
  if (record?.schema?.fields) {
    return cloneSchema(record.schema);
  }
  return emptySchema(fallbackName);
}

export function syncRecordSchema(record: DataRecord, schema: DataSchema): DataRecord {
  const nextSchema = cloneSchema(schema);
  const rows = record.rows.map((row) => {
    const next: Record<string, unknown> = {};
    for (const field of nextSchema.fields) {
      next[field.name] = row[field.name] ?? defaultForFieldType(field.type);
    }
    return next;
  });
  if (rows.length === 0 && nextSchema.fields.length > 0) {
    const row: Record<string, unknown> = {};
    for (const field of nextSchema.fields) {
      row[field.name] = defaultForFieldType(field.type);
    }
    rows.push(row);
  }
  return { schema: nextSchema, rows };
}

export function defaultForFieldType(type: string): unknown {
  switch (type) {
    case "BOOLEAN":
      return false;
    case "INTEGER":
    case "LONG":
      return 0;
    case "DOUBLE":
      return 0;
    case "STRING":
    case "DATETIME":
      return "";
    case "RECORD":
    case "RECORD_LIST":
      return null;
    default:
      return null;
  }
}

export function isNestedFieldType(type: string): boolean {
  return type === "RECORD" || type === "RECORD_LIST";
}

export function fieldHasNestedEditor(field: DataSchema["fields"][number]): boolean {
  return isNestedFieldType(field.type) && Boolean(field.nestedSchema?.fields?.length);
}

export function fieldValueToRecord(
  field: DataSchema["fields"][number],
  value: unknown
): DataRecord {
  const schema = field.nestedSchema ?? { name: field.name, fields: [] };
  if (field.type === "RECORD_LIST") {
    const rows = Array.isArray(value)
      ? (value as Record<string, unknown>[])
      : value && typeof value === "object"
        ? [value as Record<string, unknown>]
        : [];
    return { schema, rows };
  }
  const row =
    value && typeof value === "object" && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  return { schema, rows: [row] };
}

export function normalizeFunctionDescriptor(fn: {
  name: string;
  description: string;
  inputSchema: DataSchema;
  outputSchema: DataSchema;
  sourceType?: string | null;
  sourceBody?: string | null;
  dataSourcePath?: string | null;
  version?: string | null;
}) {
  return {
    name: fn.name,
    description: fn.description ?? "",
    inputSchema: fn.inputSchema,
    outputSchema: fn.outputSchema,
    sourceType: fn.sourceType?.trim() || null,
    sourceBody: fn.sourceBody?.trim() || null,
    dataSourcePath: fn.dataSourcePath?.trim() || null,
    version: fn.version?.trim() || null,
  };
}
