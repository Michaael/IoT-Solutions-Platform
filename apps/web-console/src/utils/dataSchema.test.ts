import { describe, expect, it } from "vitest";
import { emptySchema, newSchemaField, syncRecordSchema } from "./dataSchema";

describe("dataSchema", () => {
  it("syncRecordSchema aligns rows with new fields", () => {
    const schema = {
      name: "test",
      fields: [
        { name: "a", type: "STRING" },
        { name: "b", type: "INTEGER" },
      ],
    };
    const record = syncRecordSchema(
      { schema: emptySchema("test"), rows: [{ a: "x" }] },
      schema
    );
    expect(record.schema.fields).toHaveLength(2);
    expect(record.rows[0]).toEqual({ a: "x", b: 0 });
  });

  it("newSchemaField defaults to STRING nullable", () => {
    const field = newSchemaField("foo");
    expect(field.type).toBe("STRING");
    expect(field.nullable).toBe(true);
  });
});
