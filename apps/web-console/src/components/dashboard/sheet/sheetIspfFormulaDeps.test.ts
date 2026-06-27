import { describe, expect, it } from "vitest";
import { extractIspfFormulaVarRefs } from "./sheetIspfFormulaDeps";

describe("sheetIspfFormulaDeps", () => {
  it("extracts ISPREF, ISPSUM, ISPHIST references from formulas", () => {
    const refs = extractIspfFormulaVarRefs(
      {
        A1: '=ISPREF("root.platform.devices.demo-sensor-01","temperature")',
        B1: '=ISPSUM("ordersTable","int")',
        C1: "=ISPHIST(\"root.platform.devices.demo-sensor-01\",\"temperature\",15)",
      },
      { rows: 3, cols: 3, cells: {} },
      "root.platform.apps.demo"
    );
    expect(refs).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          objectPath: "root.platform.devices.demo-sensor-01",
          variableName: "temperature",
          field: "value",
        }),
        expect.objectContaining({
          objectPath: "root.platform.apps.demo",
          variableName: "ordersTable",
          needsTableSum: true,
          sumColumn: "int",
        }),
        expect.objectContaining({
          objectPath: "root.platform.devices.demo-sensor-01",
          variableName: "temperature",
          histMinutes: 15,
        }),
      ])
    );
  });
});
