export function asBool(raw: unknown): boolean {
  return raw === true || raw === "true" || raw === 1 || raw === "1";
}

export function asNum(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim() !== "") {
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

export function fmtNum(raw: unknown, decimals = 0, suffix = ""): string {
  const n = asNum(raw);
  if (n == null) return "—";
  return `${decimals > 0 ? n.toFixed(decimals) : Math.round(n)}${suffix}`;
}

export function fmtText(raw: unknown, fallback = "—"): string {
  if (raw == null || raw === "") return fallback;
  return String(raw);
}

export function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

/** Static demo fallback when live binding is absent (editor / showcase templates). */
export function demoVal(
  values: Record<string, unknown>,
  props: Record<string, unknown>,
  key: string
): unknown {
  const v = values[key];
  if (v !== undefined && v !== null && v !== "") return v;
  return props[key];
}
