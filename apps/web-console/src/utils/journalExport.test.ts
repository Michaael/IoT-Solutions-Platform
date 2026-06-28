import { describe, expect, it, vi } from "vitest";
import {
  downloadJournalExport,
  mapBindingInvokeExportRow,
  mapEventJournalExportRow,
  mapFunctionInvokeExportRow,
  mapObjectAuditExportRow,
  rowsToCsv,
} from "./journalExport";

describe("rowsToCsv", () => {
  it("returns empty string for no rows", () => {
    expect(rowsToCsv([])).toBe("");
  });

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

  it("collects union of columns across heterogeneous rows", () => {
    const csv = rowsToCsv([
      { id: "1", actor: "admin" },
      { id: "2", field: "metadata" },
    ]);
    expect(csv.split("\n")[0]).toBe("id,actor,field");
    expect(csv).toContain('2,,metadata');
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

describe("mapEventJournalExportRow", () => {
  it("preserves event payload for CSV export", () => {
    const row = mapEventJournalExportRow({
      id: "evt-1",
      timestamp: "2026-01-01T00:00:00Z",
      objectPath: "root.device",
      eventName: "alarmRaised",
      level: "WARNING",
      payload: { schema: {}, rows: [{ code: 42 }] },
    });
    expect(row.eventName).toBe("alarmRaised");
    expect(row.payload).toEqual({ schema: {}, rows: [{ code: 42 }] });
  });
});

describe("runtime journal mappers", () => {
  it("copies function invoke audit entries verbatim", () => {
    const entry = {
      id: "fn-1",
      correlationId: "corr-1",
      functionName: "resetCounter",
      objectPath: "root.device",
      appId: null,
      success: true,
      errorMessage: null,
      inputJson: null,
      outputJson: null,
      invokedAt: "2026-01-01T00:00:00Z",
    };
    expect(mapFunctionInvokeExportRow(entry)).toEqual(entry);
  });

  it("copies binding invoke audit entries verbatim", () => {
    const entry = {
      id: "bind-1",
      bindingKind: "rule",
      ruleId: "rule-a",
      ruleName: "Rule A",
      objectPath: "root.device",
      triggerKind: "startup",
      targetVariable: "output",
      success: false,
      changed: false,
      errorMessage: "division by zero",
      durationMs: 12,
      detailJson: null,
      invokedAt: "2026-01-01T00:00:00Z",
    };
    expect(mapBindingInvokeExportRow(entry)).toEqual(entry);
  });
});

describe("downloadJournalExport", () => {
  it("creates downloadable blob links for csv and json", () => {
    const click = vi.fn();
    const link = { href: "", download: "", click } as unknown as HTMLAnchorElement;
    const createElement = vi.fn(() => link);
    vi.stubGlobal("document", { createElement });
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:test"),
      revokeObjectURL: vi.fn(),
    });

    downloadJournalExport("events", "csv", [{ id: "1" }]);
    expect(link.download).toMatch(/^events-.*\.csv$/);
    expect(click).toHaveBeenCalledOnce();

    downloadJournalExport("events", "json", [{ id: "1" }]);
    expect(link.download).toMatch(/^events-.*\.json$/);
    expect(click).toHaveBeenCalledTimes(2);

    vi.unstubAllGlobals();
  });
});
