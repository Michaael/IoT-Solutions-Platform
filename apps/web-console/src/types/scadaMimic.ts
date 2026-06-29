/** SCADA mimic diagram document schema (v1). */

export type MimicRotation = 0 | 90 | 180 | 270;

export interface MimicGrid {
  size: number;
  snap: boolean;
  visible: boolean;
}

export interface MimicLayer {
  id: string;
  name: string;
  visible: boolean;
  locked?: boolean;
}

export interface MimicBinding {
  objectPath?: string;
  selectionKey?: string;
  variableName: string;
  valueField?: string;
  transform?: "bool" | "number" | "string";
}

export type MimicFormatOperator = ">" | ">=" | "<" | "<=" | "==" | "!=";

export interface MimicFormatRule {
  id: string;
  bindingKey: string;
  operator: MimicFormatOperator;
  value: string | number | boolean;
  style: Record<string, string>;
}

export interface MimicLabel {
  id: string;
  text?: string;
  bindingKey?: string;
  x: number;
  y: number;
  fontSize?: number;
}

export type MimicActionType = "setVariable" | "toggleVariable" | "invokeFunction";

export interface MimicAction {
  id: string;
  type: MimicActionType;
  objectPath?: string;
  selectionKey?: string;
  variableName?: string;
  valueField?: string;
  value?: string | number | boolean;
  functionName?: string;
  confirmMessage?: string;
}

export interface MimicElement {
  id: string;
  symbolId: string;
  layerId: string;
  x: number;
  y: number;
  rotation?: MimicRotation;
  scale?: number;
  bindings: Record<string, MimicBinding>;
  formatRules?: MimicFormatRule[];
  labels?: MimicLabel[];
  actions?: MimicAction[];
  props?: Record<string, unknown>;
}

export interface MimicConnectionEndpoint {
  elementId: string;
  port: string;
}

export interface MimicConnection {
  id: string;
  layerId: string;
  from: MimicConnectionEndpoint;
  to: MimicConnectionEndpoint;
  points: { x: number; y: number }[];
  bindings?: {
    flowing?: MimicBinding;
    alarm?: MimicBinding;
  };
  style?: {
    strokeWidth?: number;
    dash?: string;
    stroke?: string;
  };
}

export interface ScadaMimicDocument {
  version: 1;
  width: number;
  height: number;
  background?: string;
  grid?: MimicGrid;
  layers: MimicLayer[];
  elements: MimicElement[];
  connections: MimicConnection[];
}

export interface MimicPort {
  id: string;
  x: number;
  y: number;
  direction?: "n" | "s" | "e" | "w";
}

export type MimicBindingSlotType = "boolean" | "number" | "string" | "enum";

export interface MimicBindingSlot {
  key: string;
  labelKey: string;
  type: MimicBindingSlotType;
  optional?: boolean;
}

export interface SymbolDefinition {
  id: string;
  category: string;
  nameKey: string;
  defaultWidth: number;
  defaultHeight: number;
  ports: MimicPort[];
  bindingSchema: MimicBindingSlot[];
}

export interface SymbolRenderProps {
  width: number;
  height: number;
  values: Record<string, unknown>;
  props: Record<string, unknown>;
  styleOverrides: Record<string, string>;
  editable?: boolean;
  selected?: boolean;
  onClick?: () => void;
}
