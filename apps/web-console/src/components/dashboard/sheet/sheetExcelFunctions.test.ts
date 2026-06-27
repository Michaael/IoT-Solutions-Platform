import { describe, expect, it } from "vitest";
import {
  excelCountif,
  excelMatch,
  excelSumif,
  excelVlookup,
  matchCriteria,
} from "./sheetExcelFunctions";

describe("sheetExcelFunctions", () => {
  const table = [
    ["A", 10, 100],
    ["B", 20, 200],
    ["C", 30, 300],
  ];

  it("matches criteria patterns", () => {
    expect(matchCriteria(">15", 20)).toBe(true);
    expect(matchCriteria(">15", 10)).toBe(false);
    expect(matchCriteria("A*", "Alpha")).toBe(true);
  });

  it("evaluates VLOOKUP exact match", () => {
    expect(excelVlookup("B", table, 2, false)).toBe(20);
    expect(excelVlookup("Z", table, 2, false)).toBe("#N/A");
  });

  it("evaluates MATCH exact", () => {
    expect(excelMatch("B", ["A", "B", "C"], 0)).toBe(2);
  });

  it("evaluates SUMIF and COUNTIF", () => {
    expect(excelSumif(["x", "y", "x"], "x", [1, 2, 3])).toBe(4);
    expect(excelCountif(["x", "y", "x"], "x")).toBe(2);
  });
});
