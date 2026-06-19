import { getStoredRole } from "../auth/role";
import {
  ANIMA_OPERATOR_WIRE_PROFILE,
  type BffInvokeRequest,
  type BffWireResponse,
  isBffOk,
} from "../types/bff";

export async function bffInvoke<T = unknown>(request: BffInvokeRequest): Promise<BffWireResponse<T>> {
  const response = await fetch("/api/v1/bff/invoke", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-ISPF-Role": getStoredRole(),
    },
    body: JSON.stringify({
      ...request,
      wireProfile: request.wireProfile ?? ANIMA_OPERATOR_WIRE_PROFILE,
    }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `BFF invoke failed: ${response.status}`);
  }
  return response.json();
}

export function assertBffOk<T>(wire: BffWireResponse<T>): T {
  if (!isBffOk(wire)) {
    throw new Error(wire.error_message || wire.error_code || "BFF error");
  }
  return wire.result;
}

export function toBffInput(
  values: Record<string, unknown> | undefined,
  schemaName = "in"
): NonNullable<BffInvokeRequest["input"]> {
  const row = values ?? {};
  const fields = Object.keys(row).map((name) => ({ name, type: "STRING" }));
  return {
    schema: { name: schemaName, fields },
    rows: [row],
  };
}
