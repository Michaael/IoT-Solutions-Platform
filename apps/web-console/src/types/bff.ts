/** anima-operator-v1 wire envelope (REQ-PF-06). */

export const ANIMA_OPERATOR_WIRE_PROFILE = "anima-operator-v1" as const;

export type BffWireProfile = typeof ANIMA_OPERATOR_WIRE_PROFILE | string;

export interface BffWireResponse<T = unknown> {
  error_code: string;
  error_message: string;
  wireProfile?: string;
  result?: T;
  result_field_labels?: Record<string, string>;
}

export interface BffInvokeRequest {
  objectPath: string;
  functionName: string;
  input?: {
    schema: { name: string; fields: Array<{ name: string; type: string }> };
    rows: Array<Record<string, unknown>>;
  };
  wireProfile?: BffWireProfile;
}

export function isBffOk<T>(wire: BffWireResponse<T>): wire is BffWireResponse<T> & { result: T } {
  return wire.error_code === "OK";
}
