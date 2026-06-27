import { describe, expect, it } from "vitest";
import {
  findUnsupportedFunctions,
  normalizeExcelFunctionNames,
  normalizeFormulaSyntax,
  normalizeFunctionName,
  normalizeImportedFormula,
} from "./sheetFormulaNormalize";

describe("sheetFormulaNormalize", () => {
  it("maps Russian function names", () => {
    expect(normalizeFunctionName("СУММ")).toBe("SUM");
    expect(normalizeFunctionName("ЕСЛИ")).toBe("IF");
  });

  it("normalizes Excel absolute refs and semicolon separators", () => {
    expect(normalizeFormulaSyntax("SUM($A$1;$B$2)")).toBe("SUM(A1,B2)");
  });

  it("rewrites localized names in a formula", () => {
    expect(normalizeExcelFunctionNames("СУММ(A1:A3)")).toBe("SUM(A1:A3)");
  });

  it("prepares imported formulas with leading equals stripped upstream", () => {
    expect(normalizeImportedFormula("ОКРУГЛ(A1;2)")).toBe("ROUND(A1,2)");
  });

  it("flags unsupported functions", () => {
    expect(findUnsupportedFunctions("=XLOOKUP(A1,B1:C10,2,0)")).toEqual(["XLOOKUP"]);
    expect(findUnsupportedFunctions("=SUM(A1:A3)+IF(A1>0,1,0)")).toEqual([]);
  });
});
