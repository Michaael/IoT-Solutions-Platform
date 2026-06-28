import { describe, expect, it } from "vitest";
import {
  buildDemoNetworkFromLabels,
  parseDemoNetworkGraphPreview,
  parseNetworkGraphData,
  toCytoscapeElements,
} from "./networkGraphData";

describe("parseNetworkGraphData", () => {
  it("maps node and edge rows with default fields", () => {
    const data = parseNetworkGraphData(
      [
        { id: "gw", name: "Gateway" },
        { id: "s1", name: "Sensor 1" },
      ],
      [{ from: "gw", to: "s1" }],
      { labelField: "name" }
    );
    expect(data.nodes).toEqual([
      { id: "gw", label: "Gateway" },
      { id: "s1", label: "Sensor 1" },
    ]);
    expect(data.edges).toEqual([{ id: "edge-0", from: "gw", to: "s1" }]);
  });

  it("accepts source/target edge aliases", () => {
    const data = parseNetworkGraphData(
      [{ name: "A" }, { name: "B" }],
      [{ source: "A", target: "B" }],
      { labelField: "name" }
    );
    expect(data.edges[0]).toMatchObject({ from: "A", to: "B" });
  });
});

describe("buildDemoNetworkFromLabels", () => {
  it("builds star and chain edges up to edgeCount", () => {
    const data = buildDemoNetworkFromLabels(["Hub", "A", "B", "C"], 5);
    expect(data.nodes).toHaveLength(4);
    expect(data.edges.length).toBe(5);
  });
});

describe("parseDemoNetworkGraphPreview", () => {
  it("parses legacy string node list", () => {
    const data = parseDemoNetworkGraphPreview({
      nodes: ["Шлюз", "Датчик 1"],
      edges: 1,
    });
    expect(data?.nodes).toHaveLength(2);
    expect(data?.edges).toHaveLength(1);
  });

  it("parses structured nodes and edges", () => {
    const data = parseDemoNetworkGraphPreview({
      nodes: [
        { id: "gw", label: "Gateway" },
        { id: "s1", label: "Sensor" },
      ],
      edges: [{ from: "gw", to: "s1" }],
    });
    expect(data?.edges[0]).toMatchObject({ from: "gw", to: "s1" });
  });
});

describe("toCytoscapeElements", () => {
  it("maps to cytoscape element definitions", () => {
    const elements = toCytoscapeElements({
      nodes: [{ id: "a", label: "A" }],
      edges: [{ id: "e1", from: "a", to: "b" }],
    });
    expect(elements).toEqual([
      { data: { id: "a", label: "A" } },
      { data: { id: "e1", source: "a", target: "b" } },
    ]);
  });
});
