import { mimicDocumentToJson } from "../document";
import type { ScadaMimicDocument } from "../../types/scadaMimic";

const P = {
  gpu1: "root.platform.devices.mini-tec-plant.gpu-01",
  gpu2: "root.platform.devices.mini-tec-plant.gpu-02",
  gpu3: "root.platform.devices.mini-tec-plant.gpu-03",
  grpb: "root.platform.devices.mini-tec-plant.grpb",
  rumb: "root.platform.devices.mini-tec-plant.rumb-10kv",
  dgu: "root.platform.devices.mini-tec-plant.dgu",
  load: "root.platform.devices.mini-tec-plant.load-module",
  hub: "root.platform.devices.mini-tec-plant.station-hub",
} as const;

function bind(path: string, variableName: string, transform?: "bool" | "number") {
  return { objectPath: path, variableName, valueField: "value", transform };
}

export const MINI_TEC_SLD_DOCUMENT: ScadaMimicDocument = {
  version: 1,
  width: 1200,
  height: 400,
  background: "var(--bg)",
  grid: { size: 20, snap: false, visible: false },
  layers: [{ id: "layer-default", name: "Main", visible: true }],
  elements: [
    {
      id: "busbar-main",
      symbolId: "busbar.horizontal",
      layerId: "layer-default",
      x: 248,
      y: 114,
      props: {},
      bindings: { energized: bind(P.hub, "islandMode", "bool") },
    },
    {
      id: "gen-gpu1",
      symbolId: "gen.block",
      layerId: "layer-default",
      x: 268,
      y: 24,
      props: { label: "GPU-1", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu1, "running", "bool"),
        power: bind(P.gpu1, "activePowerKw", "number"),
      },
    },
    {
      id: "gen-gpu2",
      symbolId: "gen.block",
      layerId: "layer-default",
      x: 418,
      y: 24,
      props: { label: "GPU-2", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu2, "running", "bool"),
        power: bind(P.gpu2, "activePowerKw", "number"),
      },
    },
    {
      id: "gen-gpu3",
      symbolId: "gen.block",
      layerId: "layer-default",
      x: 568,
      y: 24,
      props: { label: "GPU-3", ratedKw: 1480 },
      bindings: {
        running: bind(P.gpu3, "running", "bool"),
        power: bind(P.gpu3, "activePowerKw", "number"),
      },
    },
    {
      id: "dgu-block",
      symbolId: "gen.block",
      layerId: "layer-default",
      x: 488,
      y: 176,
      props: { label: "DGU", ratedKw: 500 },
      bindings: {
        running: bind(P.dgu, "running", "bool"),
        power: bind(P.dgu, "activePowerKw", "number"),
      },
    },
    {
      id: "breaker-rumb",
      symbolId: "breaker",
      layerId: "layer-default",
      x: 872,
      y: 168,
      bindings: { closed: bind(P.rumb, "breakerClosed", "bool") },
      actions: [
        {
          id: "toggle-breaker",
          type: "invokeFunction",
          objectPath: P.rumb,
          functionName: "breaker_operate",
        },
      ],
    },
    {
      id: "load-block",
      symbolId: "load.block",
      layerId: "layer-default",
      x: 920,
      y: 240,
      props: { label: "Load" },
      bindings: { power: bind(P.load, "activePowerKw", "number") },
    },
    {
      id: "grpb-flow",
      symbolId: "data-block",
      layerId: "layer-default",
      x: 120,
      y: 78,
      props: { line1: "GRPB", line2: "Gas" },
      bindings: {
        line3: bind(P.grpb, "gasFlowRate", "number"),
      },
    },
    {
      id: "hub-freq",
      symbolId: "value-badge",
      layerId: "layer-default",
      x: 540,
      y: 88,
      props: { unit: " Hz", decimals: 2 },
      bindings: { value: bind(P.hub, "gridFrequencyHz", "number") },
    },
    {
      id: "hub-power",
      symbolId: "value-badge",
      layerId: "layer-default",
      x: 640,
      y: 88,
      props: { unit: " kW", decimals: 0 },
      bindings: { value: bind(P.hub, "totalGenPowerKw", "number") },
    },
    {
      id: "grpb-alarm",
      symbolId: "sensor.indicator",
      layerId: "layer-default",
      x: 200,
      y: 150,
      bindings: {
        state: {
          objectPath: P.grpb,
          variableName: "fireAlarm",
          valueField: "value",
          transform: "bool",
        },
      },
      formatRules: [
        {
          id: "alarm-red",
          bindingKey: "state",
          operator: "==",
          value: true,
          style: { fill: "#f85149" },
        },
      ],
    },
  ],
  connections: [
    {
      id: "conn-gen1-bus",
      layerId: "layer-default",
      from: { elementId: "gen-gpu1", port: "s" },
      to: { elementId: "busbar-main", port: "w" },
      points: [
        { x: 324, y: 136 },
        { x: 324, y: 120 },
        { x: 248, y: 120 },
      ],
    },
    {
      id: "conn-bus-load",
      layerId: "layer-default",
      from: { elementId: "busbar-main", port: "e" },
      to: { elementId: "breaker-rumb", port: "n" },
      points: [
        { x: 928, y: 120 },
        { x: 884, y: 120 },
        { x: 884, y: 168 },
      ],
    },
  ],
};

export const MINI_TEC_SLD_DOCUMENT_JSON = mimicDocumentToJson(MINI_TEC_SLD_DOCUMENT);
