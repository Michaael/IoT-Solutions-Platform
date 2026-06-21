import { describe, expect, it } from "vitest";
import { reportPathFromId } from "../api/reports";

describe("reports api", () => {
  it("builds platform report path from reportId", () => {
    expect(reportPathFromId("ready-items")).toBe("root.platform.reports.ready-items");
  });
});
