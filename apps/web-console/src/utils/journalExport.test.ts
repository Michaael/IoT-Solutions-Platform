import { describe, expect, it } from "vitest";
import { mapObjectAuditExportRow, rowsToCsv } from "./journalExport";

describe("rowsToCsv", () => {
  it("escapes commas and quotes", () => {
    const csv = rowsToCsv([
      { a: "hello", b: 'say "hi"' },
      { a: "x,y", b: "line\nbreak" },
    ]);
    expect(csv).toBe(
      'a,b\nhello,"say ""hi"""\n"x,y","line\nbreak"',
    );
  });

  it("serializes nested objects as JSON", () => {
    const csv = rowsToCsv([{ payload: { rows: [{ value: 1 }] } }]);
    expect(csv).toContain('"{""rows"":[{""value"":1}]}"');
  });
});

describe("mapObjectAuditExportRow", () => {
  it("includes audit fields", () => {
    const row = mapObjectAuditExportRow({
      id: "1",
      objectPath: "root.a",
      changeType: "UPDATE_INFO",
      field: "metadata",
      actor: "admin",
      occurredAt: "2026-01-01T00:00:00Z",
      revisionBefore: 1,
      revisionAfter: 2,
      summaryJson: "{}",
    });
    expect(row.changeType).toBe("UPDATE_INFO");
    expect(row.revisionAfter).toBe(2);
  });
});
