import { describe, expect, it } from "vitest";
import {
  formatObjectTableCell,
  matchesNamePattern,
  objectTableValueField,
  parseWidgetJsonArray,
} from "./dashboardUtils";

describe("parseWidgetJsonArray", () => {
  it("parses JSON string columnsJson", () => {
    const cols = parseWidgetJsonArray<{ variable: string }>(
      '[{"variable":"currentRate","label":"Rate"}]'
    );
    expect(cols).toHaveLength(1);
    expect(cols[0]?.variable).toBe("currentRate");
  });

  it("accepts already-parsed array from agent layout", () => {
    const cols = parseWidgetJsonArray<{ variable: string }>([
      { variable: "currencyName", label: "Currency" },
    ]);
    expect(cols).toHaveLength(1);
    expect(cols[0]?.variable).toBe("currencyName");
  });
});

describe("object table helpers", () => {
  it("maps variable field name to value", () => {
    expect(objectTableValueField({ variable: "activePowerKw", field: "activePowerKw" })).toBe(
      "value"
    );
    expect(objectTableValueField({ variable: "activePowerKw", field: "value" })).toBe("value");
  });

  it("matches gpu glob", () => {
    expect(matchesNamePattern("gpu-01", "gpu-*")).toBe(true);
    expect(matchesNamePattern("grpb", "gpu-*")).toBe(false);
  });

  it("formats booleans", () => {
    expect(formatObjectTableCell(true, { trueLabel: "Run", falseLabel: "Stop" })).toBe("Run");
    expect(formatObjectTableCell(false, { trueLabel: "Run", falseLabel: "Stop" })).toBe("Stop");
  });
});
