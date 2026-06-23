import { describe, expect, it } from "vitest";
import {
  buildDefaultParameters,
  parseColumnsJson,
  parseParametersText,
  validateColumns,
  validateParameters,
} from "./reportBuilderUtils";

describe("reportBuilderUtils", () => {
  it("parses parameters from lines and commas", () => {
    expect(parseParametersText("status\nregion")).toEqual(["status", "region"]);
    expect(parseParametersText("a, b , c")).toEqual(["a", "b", "c"]);
  });

  it("parses columns JSON", () => {
    const cols = parseColumnsJson('[{"field":"item_code","label":"Код"}]');
    expect(cols).toEqual([{ field: "item_code", label: "Код" }]);
  });

  it("rejects invalid column JSON", () => {
    expect(() => parseColumnsJson("{}")).toThrow(/array/i);
  });

  it("validates parameters", () => {
    expect(validateParameters(["status"])).toBeNull();
    expect(validateParameters(["bad-name"])).toMatch(/Недопустимое/);
    expect(validateParameters(["a", "a"])).toMatch(/Дублирующийся/);
  });

  it("validates columns", () => {
    expect(validateColumns([])).toMatch(/колонку/);
    expect(validateColumns([{ field: "x", label: "X" }])).toBeNull();
    expect(validateColumns([{ field: "", label: "X" }])).toMatch(/field/);
  });

  it("builds default parameters with coercion", () => {
    expect(
      buildDefaultParameters(["n", "flag", "name"], { n: "42", flag: "true", name: "demo" })
    ).toEqual({ n: 42, flag: true, name: "demo" });
  });
});
