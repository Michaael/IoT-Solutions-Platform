import type { ElementDefinition } from "cytoscape";
import { readFieldValue } from "../types/dashboard";

export interface NetworkGraphNode {
  id: string;
  label: string;
}

export interface NetworkGraphEdge {
  id: string;
  from: string;
  to: string;
}

export interface NetworkGraphData {
  nodes: NetworkGraphNode[];
  edges: NetworkGraphEdge[];
}

export type NetworkGraphLayout = "cose" | "circle" | "grid" | "breadthfirst";

export interface NetworkGraphFieldConfig {
  labelField?: string;
  idField?: string;
  edgeFromField?: string;
  edgeToField?: string;
}

const EDGE_FROM_ALIASES = ["from", "source", "src", "start"];
const EDGE_TO_ALIASES = ["to", "target", "dst", "end"];

function firstField(row: Record<string, unknown>, fields: string[]): unknown {
  for (const field of fields) {
    const value = readFieldValue(row, field);
    if (value != null && String(value).trim() !== "") {
      return value;
    }
  }
  return undefined;
}

function nodeId(
  row: Record<string, unknown>,
  index: number,
  config: NetworkGraphFieldConfig
): string {
  const explicit = readFieldValue(row, config.idField ?? "id");
  if (explicit != null && String(explicit).trim() !== "") {
    return String(explicit);
  }
  const label = readFieldValue(row, config.labelField ?? "name");
  if (label != null && String(label).trim() !== "") {
    return String(label);
  }
  return `node-${index}`;
}

export function parseNetworkGraphNodes(
  rows: Record<string, unknown>[] | undefined,
  config: NetworkGraphFieldConfig
): NetworkGraphNode[] {
  if (!rows?.length) return [];
  return rows.map((row, index) => {
    const id = nodeId(row, index, config);
    const labelValue = readFieldValue(row, config.labelField ?? "name");
    const label =
      labelValue != null && String(labelValue).trim() !== ""
        ? String(labelValue)
        : id;
    return { id, label };
  });
}

export function parseNetworkGraphEdges(
  rows: Record<string, unknown>[] | undefined,
  config: NetworkGraphFieldConfig
): NetworkGraphEdge[] {
  if (!rows?.length) return [];
  const fromFields = [
    ...(config.edgeFromField ? [config.edgeFromField] : []),
    ...EDGE_FROM_ALIASES,
  ];
  const toFields = [
    ...(config.edgeToField ? [config.edgeToField] : []),
    ...EDGE_TO_ALIASES,
  ];
  const edges: NetworkGraphEdge[] = [];
  rows.forEach((row, index) => {
    const from = firstField(row, fromFields);
    const to = firstField(row, toFields);
    if (from == null || to == null) return;
    edges.push({
      id: `edge-${index}`,
      from: String(from),
      to: String(to),
    });
  });
  return edges;
}

export function parseNetworkGraphData(
  nodeRows: Record<string, unknown>[] | undefined,
  edgeRows: Record<string, unknown>[] | undefined,
  config: NetworkGraphFieldConfig
): NetworkGraphData {
  return {
    nodes: parseNetworkGraphNodes(nodeRows, config),
    edges: parseNetworkGraphEdges(edgeRows, config),
  };
}

export function buildDemoNetworkFromLabels(
  labels: string[],
  edgeCount: number
): NetworkGraphData {
  const nodes: NetworkGraphNode[] = labels.map((label, index) => ({
    id: `n${index}`,
    label,
  }));
  const edges: NetworkGraphEdge[] = [];
  if (nodes.length < 2) return { nodes, edges };

  let edgeIndex = 0;
  for (let i = 1; i < nodes.length && edges.length < edgeCount; i++) {
    edges.push({
      id: `edge-${edgeIndex++}`,
      from: nodes[0].id,
      to: nodes[i].id,
    });
  }
  for (let i = 1; i < nodes.length - 1 && edges.length < edgeCount; i++) {
    edges.push({
      id: `edge-${edgeIndex++}`,
      from: nodes[i].id,
      to: nodes[i + 1].id,
    });
  }
  return { nodes, edges };
}

interface DemoStructuredNode {
  id?: string;
  label?: string;
  name?: string;
}

interface DemoStructuredEdge {
  from?: string;
  source?: string;
  to?: string;
  target?: string;
}

export interface DemoNetworkGraphPreview {
  nodes?: Array<string | DemoStructuredNode>;
  edges?: number | DemoStructuredEdge[];
}

export function parseDemoNetworkGraphPreview(
  demo: DemoNetworkGraphPreview | null
): NetworkGraphData | null {
  if (!demo?.nodes?.length) return null;

  if (typeof demo.nodes[0] === "string") {
    const labels = demo.nodes as string[];
    const edgeCount =
      typeof demo.edges === "number" ? demo.edges : labels.length;
    return buildDemoNetworkFromLabels(labels, edgeCount);
  }

  const nodes: NetworkGraphNode[] = (demo.nodes as DemoStructuredNode[]).map(
    (node, index) => {
      const id = node.id ?? node.name ?? node.label ?? `n${index}`;
      const label = node.label ?? node.name ?? id;
      return { id: String(id), label: String(label) };
    }
  );
  const nodeIds = new Set(nodes.map((node) => node.id));

  let edges: NetworkGraphEdge[] = [];
  if (Array.isArray(demo.edges)) {
    edges = demo.edges
      .map((edge, index) => {
        const from = edge.from ?? edge.source;
        const to = edge.to ?? edge.target;
        if (!from || !to) return null;
        return {
          id: `edge-${index}`,
          from: String(from),
          to: String(to),
        };
      })
      .filter((edge): edge is NetworkGraphEdge => edge != null)
      .filter((edge) => nodeIds.has(edge.from) && nodeIds.has(edge.to));
  } else if (typeof demo.edges === "number") {
    edges = buildDemoNetworkFromLabels(
      nodes.map((node) => node.label),
      demo.edges
    ).edges;
  }

  return { nodes, edges };
}

export function toCytoscapeElements(data: NetworkGraphData): ElementDefinition[] {
  const nodeElements: ElementDefinition[] = data.nodes.map((node) => ({
    data: { id: node.id, label: node.label },
  }));
  const edgeElements: ElementDefinition[] = data.edges.map((edge) => ({
    data: { id: edge.id, source: edge.from, target: edge.to },
  }));
  return [...nodeElements, ...edgeElements];
}
