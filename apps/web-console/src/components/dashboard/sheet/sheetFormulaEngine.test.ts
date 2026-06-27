import { describe, expect, it } from "vitest";
import { a1ToRowCol, rowColToA1, defaultColLabels } from "./sheetAddress";
import { parseSheetConfig, DEFAULT_SHEET_CONFIG, FREE_SHEET_CONFIG } from "./sheetConfig";
import { bindingCacheKey, createSheetFormulaEngine, setIspfFormulaContext } from "./sheetFormulaEngine";
import { loadValuesFromVariable, loadValuesFromVariableRecord, loadMetaFromVariableRecord, saveValuesToVariableRecord, canWriteSheetValues } from "./sheetPersist";
import { mergeLoadedContents } from "./useSpreadsheetPersist";

describe("sheetAddress", () => {
  it("converts A1 and back", () => {
    expect(a1ToRowCol("A1")).toEqual({ row: 0, col: 0 });
    expect(a1ToRowCol("B2")).toEqual({ row: 1, col: 1 });
    expect(rowColToA1(1, 1)).toBe("B2");
  });

  it("builds default column labels", () => {
    expect(defaultColLabels(3)).toEqual(["A", "B", "C"]);
  });
});

describe("parseSheetConfig", () => {
  it("parses valid config", () => {
    const config = parseSheetConfig(
      JSON.stringify({ rows: 5, cols: 3, cells: { A1: { kind: "label", text: "X" } } })
    );
    expect(config?.rows).toBe(5);
    expect(config?.cells.A1?.text).toBe("X");
  });

  it("returns undefined for invalid json", () => {
    expect(parseSheetConfig("{bad")).toBeUndefined();
  });
});

describe("sheetFormulaEngine", () => {
  it("evaluates formula from input in configured mode", () => {
    const engine = createSheetFormulaEngine(DEFAULT_SHEET_CONFIG, "configured", { A2: "10" });
    expect(engine.getCellValue("B2")).toBe(20);
    engine.destroy();
  });

  it("recalculates after input change", () => {
    const engine = createSheetFormulaEngine(DEFAULT_SHEET_CONFIG, "configured", { A2: "5" });
    engine.setInputValue("A2", "20");
    expect(engine.getCellValue("B2")).toBe(40);
    engine.destroy();
  });

  it("setCellContent with formula in free mode", () => {
    const engine = createSheetFormulaEngine(FREE_SHEET_CONFIG, "free", { A1: "10" });
    engine.setCellContent("B1", "=A1*2");
    expect(engine.getCellValue("B1")).toBe(20);
    expect(engine.getCellEditContent("B1")).toBe("=A1*2");
    engine.destroy();
  });

  it("free mode allows editing empty cell C3", () => {
    const engine = createSheetFormulaEngine(
      { rows: 5, cols: 5, cells: {} },
      "free",
      {}
    );
    engine.setCellContent("C3", "42");
    expect(engine.getCellValue("C3")).toBe(42);
    engine.setCellContent("D3", "=C3*2");
    expect(engine.getCellValue("D3")).toBe(84);
    engine.destroy();
  });

  it("supports built-in aggregate formulas in free mode", () => {
    const engine = createSheetFormulaEngine(
      { rows: 10, cols: 5, cells: {} },
      "free",
      { A2: "10", A3: "5", C2: "=SUM(A2:A10)" }
    );
    expect(engine.getCellValue("C2")).toBe(15);
    engine.destroy();
  });

  it("keeps ISPF formula functions alongside built-in functions", () => {
    setIspfFormulaContext({
      bindingValues: new Map([
        [bindingCacheKey("root.platform.devices.demo-sensor-01", "temperature"), 21],
      ]),
      tableColumnSums: new Map(),
      histValues: new Map(),
    });
    const engine = createSheetFormulaEngine(
      { rows: 4, cols: 4, cells: {} },
      "free",
      {
        A1: "1",
        A2: "2",
        B1: '=ISPREF("root.platform.devices.demo-sensor-01","temperature")',
        B2: "=SUM(A1:A2)+B1",
      }
    );
    expect(engine.getCellValue("B1")).toBe(21);
    expect(engine.getCellValue("B2")).toBe(24);
    engine.destroy();
  });

  it("exports csv from grid", () => {
    const engine = createSheetFormulaEngine(
      { rows: 2, cols: 2, cells: {} },
      "free",
      { A1: "1", B1: "2" }
    );
    const csv = engine.exportCsv();
    expect(csv.split("\n")[0]).toContain("1");
    engine.destroy();
  });
});

describe("sheetPersist", () => {
  it("keeps imported free-grid session values authoritative", () => {
    const loaded = mergeLoadedContents(FREE_SHEET_CONFIG, "free", { C3: "imported" }, {});

    expect(loaded).toEqual({ C3: "imported" });
  });

  it("does not restore free-grid template seeds when variable meta exists without values", () => {
    const loaded = mergeLoadedContents(
      FREE_SHEET_CONFIG,
      "free",
      {},
      {},
      { persistMode: "variable", runtimeMeta: { rows: 12, cols: 8 } }
    );

    expect(loaded).toEqual({});
  });

  it("prefers variable record values over template seeds in free mode", () => {
    const loaded = mergeLoadedContents(
      FREE_SHEET_CONFIG,
      "free",
      {},
      { A1: "Product", B1: "Q1" },
      { persistMode: "variable" }
    );

    expect(loaded).toEqual({ A1: "Product", B1: "Q1" });
  });

  it("falls back to session cache in variable mode before template seeds", () => {
    const loaded = mergeLoadedContents(
      FREE_SHEET_CONFIG,
      "free",
      { A1: "Product" },
      {},
      { persistMode: "variable", runtimeMeta: { rows: 12, cols: 8 } }
    );

    expect(loaded).toEqual({ A1: "Product" });
  });

  it("keeps configured saved values compatible with template formulas", () => {
    const loaded = mergeLoadedContents(DEFAULT_SHEET_CONFIG, "configured", { A2: "42" }, {});

    expect(loaded.A2).toBe("42");

    const engine = createSheetFormulaEngine(DEFAULT_SHEET_CONFIG, "configured", loaded);
    expect(engine.getCellValue("B2")).toBe(84);
    engine.destroy();
  });

  it("round-trips cell values through variable record", () => {
    const loaded = loadValuesFromVariable(undefined, DEFAULT_SHEET_CONFIG, "configured");
    expect(loaded.A2).toBe("10");

    const saved = saveValuesToVariableRecord({ A2: "42", B2: "x" }, undefined);
    const restored = loadValuesFromVariable(saved, DEFAULT_SHEET_CONFIG, "configured");
    expect(restored.A2).toBe("42");
  });

  it("loads lab sheetValues nested RECORD_LIST shape", () => {
    const stored = {
      schema: {
        name: "sheetValues",
        fields: [
          {
            name: "rows",
            type: "RECORD_LIST",
            nestedSchema: {
              name: "sheetCellRow",
              fields: [
                { name: "cell", type: "STRING" },
                { name: "value", type: "STRING" },
              ],
            },
          },
        ],
      },
      rows: [{ rows: [{ cell: "A1", value: "ok" }] }],
    };
    expect(loadValuesFromVariableRecord(stored)).toEqual({ A1: "ok" });
    const saved = saveValuesToVariableRecord({ A1: "10", B1: "20" }, stored);
    expect(saved.rows).toEqual([
      { rows: [{ cell: "A1", value: "10" }, { cell: "B1", value: "20" }] },
    ]);
    expect(saved.schema).toEqual(stored.schema);
    expect(loadValuesFromVariableRecord(saved)).toEqual({ A1: "10", B1: "20" });
  });

  it("allows first write when variable value is null but writable", () => {
    expect(canWriteSheetValues({ writable: true, value: null })).toBe(true);
    expect(canWriteSheetValues({ writable: false, value: null })).toBe(false);
    expect(
      canWriteSheetValues({
        writable: true,
        value: { schema: { name: "x", fields: [{ name: "value", type: "STRING" }] }, rows: [] },
      })
    ).toBe(false);
  });

  it("round-trips formulas in free mode", () => {
    const saved = saveValuesToVariableRecord({ B2: "=A2*2", A2: "10" }, undefined);
    const restored = loadValuesFromVariable(saved, FREE_SHEET_CONFIG, "free");
    expect(restored.B2).toBe("=A2*2");
    expect(restored.A2).toBe("10");

    const engine = createSheetFormulaEngine(FREE_SHEET_CONFIG, "free", restored);
    expect(engine.getCellValue("B2")).toBe(20);
    engine.destroy();
  });

  it("round-trips layout meta through variable record metaJson", () => {
    const meta = {
      rows: 24,
      cols: 10,
      cellStyles: {
        A1: { style: { backgroundColor: "#ff0000", fontWeight: "bold" as const } },
      },
      mergedCells: [{ anchor: "B2", rowSpan: 2, colSpan: 2 }],
    };
    const saved = saveValuesToVariableRecord({ A1: "title" }, undefined, meta);
    expect(loadValuesFromVariableRecord(saved)).toEqual({ A1: "title" });
    expect(loadMetaFromVariableRecord(saved)).toEqual(meta);
    expect(saved.schema.fields.some((field) => field.name === "metaJson")).toBe(true);

    const resaved = saveValuesToVariableRecord({ A1: "updated" }, saved);
    expect(loadMetaFromVariableRecord(resaved)).toEqual(meta);
  });
});
