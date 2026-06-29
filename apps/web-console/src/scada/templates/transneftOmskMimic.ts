import { DEFAULT_LAYER_ID, mimicDocumentToJson } from "../document";
import type { MimicBinding, MimicConnection, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";

export const TRANSNEFT_OMSK_MIMIC_PATH = "root.platform.mimics.transneft-omsk-rdp";
export const TRANSNEFT_OMSK_DASHBOARD_PATH = "root.platform.dashboards.transneft-omsk-rdp";

const P = {
  hub: "root.platform.devices.transneft-omsk-rdp.rdp-hub",
  tank: (n: number) => `root.platform.devices.transneft-omsk-rdp.tank-${n}`,
} as const;

interface TankSpec {
  n: number;
  stroke: string;
  liquid: string;
}

const YELLOW_TANKS: TankSpec[] = [
  { n: 11, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 12, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 13, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 14, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 15, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 16, stroke: "#d4a72c", liquid: "#9e6a03" },
  { n: 17, stroke: "#d4a72c", liquid: "#9e6a03" },
];

const BLUE_TANKS: TankSpec[] = [
  { n: 18, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 19, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 20, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 21, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 22, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 23, stroke: "#58a6ff", liquid: "#1f6feb" },
  { n: 24, stroke: "#58a6ff", liquid: "#1f6feb" },
];

function bind(path: string, variableName: string, transform?: MimicBinding["transform"]): MimicBinding {
  return { objectPath: path, variableName, valueField: "value", transform };
}

function el(partial: Omit<MimicElement, "layerId" | "bindings"> & Partial<Pick<MimicElement, "bindings">>): MimicElement {
  return {
    bindings: {},
    layerId: DEFAULT_LAYER_ID,
    ...partial,
  };
}

function tankElement(spec: TankSpec, x: number, y: number): MimicElement {
  const path = P.tank(spec.n);
  return el({
    id: `tank-${spec.n}`,
    symbolId: "tank.vertical",
    x,
    y,
    props: {
      label: String(spec.n),
      tankStroke: spec.stroke,
      liquidColor: spec.liquid,
    },
    bindings: {
      fillLevel: bind(path, "fillLevelMm", "number"),
      rate: bind(path, "rateMmPerHour", "number"),
      maxLevel: bind(path, "maxLevelMm", "number"),
    },
  });
}

function valveElement(id: string, x: number, y: number, tankPath: string): MimicElement {
  return el({
    id,
    symbolId: "valve.butterfly",
    x,
    y,
    props: { width: 28, height: 36 },
    bindings: {
      open: bind(tankPath, "valveOpen", "bool"),
    },
  });
}

function buildTankRow(
  tanks: TankSpec[],
  rowY: number,
  prefix: string
): { elements: MimicElement[]; connections: MimicConnection[] } {
  const elements: MimicElement[] = [];
  const connections: MimicConnection[] = [];
  const startX = 150;
  const step = 108;
  const pipeY = rowY + 128;
  const busId = `${prefix}-bus`;

  tanks.forEach((spec, i) => {
    const x = startX + i * step;
    const tankId = `tank-${spec.n}`;
    elements.push(tankElement(spec, x, rowY));

    if (i < tanks.length - 1) {
      elements.push(valveElement(`${prefix}-v${spec.n}`, x + step - 34, pipeY - 10, P.tank(spec.n)));
    }

    connections.push({
      id: `${prefix}-drop-${spec.n}`,
      layerId: DEFAULT_LAYER_ID,
      from: { elementId: tankId, port: "s" },
      to: { elementId: busId, port: "w" },
      points: [
        { x: x + 40, y: rowY + 120 },
        { x: x + 40, y: pipeY + 8 },
        { x: startX + 20, y: pipeY + 8 },
      ],
    });
  });

  elements.push(
    el({
      id: busId,
      symbolId: "pipe.segment",
      x: startX,
      y: pipeY,
      props: { width: step * (tanks.length - 1) + 40, height: 16, flowing: true },
    })
  );

  return { elements, connections };
}

function buildHeader(): MimicElement[] {
  return [
    el({
      id: "hdr-title",
      symbolId: "label",
      x: 320,
      y: 8,
      props: {
        text: "ОАО «Транссибнефть» — РДП Омск / РП «Омская»",
        fontSize: 14,
        width: 560,
        height: 20,
      },
    }),
    el({
      id: "hdr-product-y",
      symbolId: "label",
      x: 150,
      y: 36,
      props: { text: "Сернистая нефть", fontSize: 11, width: 140, height: 16 },
    }),
    el({
      id: "hdr-product-b",
      symbolId: "label",
      x: 150,
      y: 196,
      props: { text: "Безсерная нефть", fontSize: 11, width: 140, height: 16 },
    }),
  ];
}

function buildHubPanel(): MimicElement[] {
  return [
    el({
      id: "hub-data",
      symbolId: "data-block",
      x: 980,
      y: 120,
      props: {
        width: 120,
        height: 72,
        line1: "Коллектор РДП",
        line2: "P / Q / T",
      },
      bindings: {
        line3: bind(P.hub, "linePressureMpa", "number"),
        line4: bind(P.hub, "lineFlowM3h", "number"),
      },
    }),
    el({
      id: "hub-pressure",
      symbolId: "value-badge",
      x: 980,
      y: 208,
      props: { width: 88, height: 26, unit: " МПа", decimals: 2 },
      bindings: { value: bind(P.hub, "linePressureMpa", "number") },
    }),
    el({
      id: "hub-flow",
      symbolId: "value-badge",
      x: 1076,
      y: 208,
      props: { width: 96, height: 26, unit: " м³/ч", decimals: 0 },
      bindings: { value: bind(P.hub, "lineFlowM3h", "number") },
    }),
    el({
      id: "hub-temp",
      symbolId: "value-badge",
      x: 980,
      y: 242,
      props: { width: 88, height: 26, unit: " °C", decimals: 1 },
      bindings: { value: bind(P.hub, "lineTempC", "number") },
    }),
    el({
      id: "hub-volume",
      symbolId: "value-badge",
      x: 1076,
      y: 242,
      props: { width: 96, height: 26, unit: " м³", decimals: 0 },
      bindings: { value: bind(P.hub, "totalVolumeM3", "number") },
    }),
  ];
}

function buildSidebarLevels(): MimicElement[] {
  return YELLOW_TANKS.concat(BLUE_TANKS).map((spec, index) =>
    el({
      id: `level-${spec.n}`,
      symbolId: "value-badge",
      x: 12,
      y: 64 + index * 26,
      props: { width: 120, height: 22, unit: " мм", decimals: 0 },
      bindings: { value: bind(P.tank(spec.n), "fillLevelMm", "number") },
    })
  );
}

export const TRANSNEFT_OMSK_DOCUMENT: ScadaMimicDocument = (() => {
  const yellow = buildTankRow(YELLOW_TANKS, 56, "yellow");
  const blue = buildTankRow(BLUE_TANKS, 216, "blue");

  return {
    version: 1,
    width: 1400,
    height: 420,
    background: "#4a5159",
    grid: { size: 20, snap: false, visible: false },
    layers: [{ id: DEFAULT_LAYER_ID, name: "Main", visible: true }],
    elements: [
      ...buildHeader(),
      ...buildSidebarLevels(),
      ...yellow.elements,
      ...blue.elements,
      ...buildHubPanel(),
      el({
        id: "legend",
        symbolId: "label",
        x: 980,
        y: 286,
        props: {
          text: "Live demo: virtual tanks + RDP hub",
          fontSize: 10,
          width: 220,
          height: 16,
        },
      }),
    ],
    connections: [...yellow.connections, ...blue.connections],
  };
})();

export const TRANSNEFT_OMSK_DOCUMENT_JSON = mimicDocumentToJson(TRANSNEFT_OMSK_DOCUMENT);
