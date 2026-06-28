/**
 * Catalog of built-in platform binding functions.
 * Keep in sync with {@code PlatformBindingRegistry} (ispf-expression).
 */
export type PlatformBindingCategory = "signal" | "stateful" | "cross" | "function" | "aggregate";

export interface PlatformBindingEntry {
  id: string;
  name: string;
  snippet: string;
  stateful?: boolean;
  category: PlatformBindingCategory;
}

export const PLATFORM_BINDING_ENTRIES: PlatformBindingEntry[] = [
  {
    id: "selectField",
    name: "selectField",
    snippet: "selectField(sourceVar)",
    category: "signal",
  },
  {
    id: "scale",
    name: "scale",
    snippet: "scale(sourceVar, 0, 100, 0, 1)",
    category: "signal",
  },
  {
    id: "clamp",
    name: "clamp",
    snippet: "clamp(sourceVar, 0, 100)",
    category: "signal",
  },
  {
    id: "format",
    name: "format",
    snippet: 'format("%.1f", sourceVar)',
    category: "signal",
  },
  {
    id: "delta",
    name: "delta",
    snippet: "delta(sourceVar)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "rate",
    name: "rate",
    snippet: "rate(sourceVar)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "counterRate",
    name: "counterRate",
    snippet: "counterRate(ifInOctets)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "counterDelta",
    name: "counterDelta",
    snippet: "counterDelta(ifInOctets)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "movingAvg",
    name: "movingAvg",
    snippet: "movingAvg(sourceVar, 60)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "movingMin",
    name: "movingMin",
    snippet: "movingMin(sourceVar, 30)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "movingMax",
    name: "movingMax",
    snippet: "movingMax(sourceVar, 30)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "deadband",
    name: "deadband",
    snippet: "deadband(sourceVar, 1.0)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "hysteresis",
    name: "hysteresis",
    snippet: "hysteresis(sourceVar, 80, 70)",
    stateful: true,
    category: "stateful",
  },
  {
    id: "unitConvert",
    name: "unitConvert",
    snippet: "unitConvert(temperature, C, F)",
    category: "signal",
  },
  {
    id: "refAt",
    name: "refAt",
    snippet: 'refAt("root.platform.devices.demo", sourceVar)',
    category: "cross",
  },
  {
    id: "callFunction",
    name: "callFunction",
    snippet: "callFunction(myFunc, inputVar)",
    category: "function",
  },
  {
    id: "callFunctionAt",
    name: "callFunctionAt",
    snippet: 'callFunctionAt("root.remote", myFunc, inputVar)',
    category: "function",
  },
  {
    id: "sumRecordField",
    name: "sumRecordField",
    snippet: 'sumRecordField(tableVar, "amount")',
    category: "aggregate",
  },
];

export const PLATFORM_BINDING_NAMES = PLATFORM_BINDING_ENTRIES.map((entry) => entry.name);

export function filterPlatformBindings(query: string): PlatformBindingEntry[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return PLATFORM_BINDING_ENTRIES;
  }
  return PLATFORM_BINDING_ENTRIES.filter(
    (entry) =>
      entry.id.toLowerCase().includes(normalized) ||
      entry.snippet.toLowerCase().includes(normalized) ||
      entry.category.toLowerCase().includes(normalized)
  );
}

export function suggestPlatformBindingPrefix(input: string): PlatformBindingEntry[] {
  const trimmed = input.trim();
  const paren = trimmed.indexOf("(");
  const head = (paren >= 0 ? trimmed.slice(0, paren) : trimmed).trim().toLowerCase();
  if (!head) {
    return PLATFORM_BINDING_ENTRIES;
  }
  return PLATFORM_BINDING_ENTRIES.filter((entry) => entry.name.toLowerCase().startsWith(head));
}
