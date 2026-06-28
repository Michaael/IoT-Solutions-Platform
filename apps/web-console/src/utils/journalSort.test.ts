import { describe, expect, it } from "vitest";
import { sortByNewestFirst } from "./journalSort";

describe("sortByNewestFirst", () => {
  it("orders newest timestamps first", () => {
    const rows = [
      { id: "a", at: "2026-06-28T10:00:00Z" },
      { id: "c", at: "2026-06-28T12:00:00Z" },
      { id: "b", at: "2026-06-28T11:00:00Z" },
    ];
    expect(sortByNewestFirst(rows, (row) => row.at, (row) => row.id).map((row) => row.id)).toEqual([
      "c",
      "b",
      "a",
    ]);
  });
});
