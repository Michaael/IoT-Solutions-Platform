import type {
  MimicElement,
  MimicLayer,
  ScadaMimicDocument,
} from "../types/scadaMimic";

export const DEFAULT_LAYER_ID = "layer-default";

export const EMPTY_MIMIC_DOCUMENT: ScadaMimicDocument = {
  version: 1,
  width: 1600,
  height: 900,
  background: "var(--bg)",
  grid: { size: 20, snap: false, visible: false },
  layers: [{ id: DEFAULT_LAYER_ID, name: "Main", visible: true }],
  elements: [],
  connections: [],
};

export function createMimicId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
}

export function parseMimicDocument(raw: string | undefined): ScadaMimicDocument {
  if (!raw?.trim()) {
    return { ...EMPTY_MIMIC_DOCUMENT, layers: [...EMPTY_MIMIC_DOCUMENT.layers] };
  }
  try {
    const parsed = JSON.parse(raw) as Partial<ScadaMimicDocument>;
    return normalizeMimicDocument(parsed);
  } catch {
    return { ...EMPTY_MIMIC_DOCUMENT, layers: [...EMPTY_MIMIC_DOCUMENT.layers] };
  }
}

export function normalizeMimicDocument(
  input: Partial<ScadaMimicDocument> | undefined
): ScadaMimicDocument {
  const base = { ...EMPTY_MIMIC_DOCUMENT, layers: [...EMPTY_MIMIC_DOCUMENT.layers] };
  if (!input || typeof input !== "object") {
    return base;
  }
  const layers =
    Array.isArray(input.layers) && input.layers.length > 0
      ? input.layers.map(normalizeLayer)
      : base.layers;
  return {
    version: 1,
    width: positiveNumber(input.width, base.width),
    height: positiveNumber(input.height, base.height),
    background: typeof input.background === "string" ? input.background : base.background,
    grid: normalizeGrid(input.grid, base.grid),
    layers,
    elements: Array.isArray(input.elements) ? input.elements.map(normalizeElement) : [],
    connections: Array.isArray(input.connections)
      ? input.connections.filter((c) => c && typeof c.id === "string")
      : [],
  };
}

function normalizeLayer(layer: MimicLayer): MimicLayer {
  return {
    id: layer.id || createMimicId("layer"),
    name: layer.name || "Layer",
    visible: layer.visible !== false,
    locked: layer.locked === true,
  };
}

function normalizeElement(el: MimicElement): MimicElement {
  return {
    id: el.id || createMimicId("el"),
    symbolId: el.symbolId || "label",
    layerId: el.layerId || DEFAULT_LAYER_ID,
    x: numberOrZero(el.x),
    y: numberOrZero(el.y),
    rotation: el.rotation,
    scale: el.scale,
    bindings: el.bindings && typeof el.bindings === "object" ? el.bindings : {},
    formatRules: Array.isArray(el.formatRules) ? el.formatRules : undefined,
    labels: Array.isArray(el.labels) ? el.labels : undefined,
    actions: Array.isArray(el.actions) ? el.actions : undefined,
    props: el.props && typeof el.props === "object" ? el.props : undefined,
  };
}

function normalizeGrid(
  grid: ScadaMimicDocument["grid"] | undefined,
  fallback: ScadaMimicDocument["grid"]
): ScadaMimicDocument["grid"] {
  if (!grid) return fallback;
  return {
    size: positiveNumber(grid.size, fallback?.size ?? 20),
    snap: grid.snap === true,
    visible: grid.visible === true,
  };
}

function positiveNumber(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : fallback;
}

function numberOrZero(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

export function mimicDocumentToJson(doc: ScadaMimicDocument): string {
  return JSON.stringify(doc, null, 2);
}

export function snapToGrid(value: number, gridSize: number, enabled: boolean): number {
  if (!enabled || gridSize <= 0) return Math.round(value);
  return Math.round(value / gridSize) * gridSize;
}

/** Snap canvas coordinate: grid step when `grid.snap`, otherwise nearest pixel. */
export function snapCanvasCoordinate(
  value: number,
  grid: ScadaMimicDocument["grid"] | undefined
): number {
  return snapToGrid(value, grid?.size ?? 20, grid?.snap === true);
}
