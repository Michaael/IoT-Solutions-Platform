import { describe, expect, it } from "vitest";
import { reportPathFromId, type ReportExportFormat } from "../api/reports";

describe("reports api", () => {
  it("builds platform report path from reportId", () => {
    expect(reportPathFromId("ready-items")).toBe("root.platform.reports.ready-items");
  });

  it("supports html export format type", () => {
    const format: ReportExportFormat = "html";
    expect(format).toBe("html");
  });
});
