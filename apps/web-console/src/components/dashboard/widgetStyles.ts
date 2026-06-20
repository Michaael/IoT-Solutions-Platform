import { useMemo } from "react";
import type { CSSProperties } from "react";

/** Semantic element keys inside a dashboard widget. */
export type WidgetStyleKey =
  | "card"
  | "title"
  | "body"
  | "value"
  | "unit"
  | "meta"
  | "label"
  | "badge"
  | "dot"
  | "table"
  | "chart";

export type WidgetStylesMap = Partial<Record<WidgetStyleKey, CSSProperties>>;

const ALLOWED_CSS_KEYS = new Set([
  "alignItems",
  "alignSelf",
  "background",
  "backgroundColor",
  "border",
  "borderColor",
  "borderRadius",
  "borderWidth",
  "color",
  "display",
  "flex",
  "flexDirection",
  "flexWrap",
  "fontFamily",
  "fontSize",
  "fontStyle",
  "fontWeight",
  "gap",
  "height",
  "justifyContent",
  "letterSpacing",
  "lineHeight",
  "margin",
  "marginBottom",
  "marginTop",
  "maxHeight",
  "maxWidth",
  "minHeight",
  "minWidth",
  "opacity",
  "overflow",
  "overflowX",
  "overflowY",
  "padding",
  "paddingBottom",
  "paddingTop",
  "textAlign",
  "textDecoration",
  "textOverflow",
  "textTransform",
  "whiteSpace",
  "width",
  "wordBreak",
  "WebkitLineClamp",
]);

function sanitizeStyle(raw: unknown): CSSProperties | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return undefined;
  }
  const style: CSSProperties = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (!ALLOWED_CSS_KEYS.has(key)) continue;
    if (typeof value === "string" || typeof value === "number") {
      (style as Record<string, string | number>)[key] = value;
    }
  }
  return Object.keys(style).length > 0 ? style : undefined;
}

export function parseWidgetStyles(stylesJson?: string): WidgetStylesMap {
  if (!stylesJson?.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(stylesJson) as Record<string, unknown>;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    const result: WidgetStylesMap = {};
    for (const key of Object.keys(parsed) as WidgetStyleKey[]) {
      const style = sanitizeStyle(parsed[key]);
      if (style) {
        result[key] = style;
      }
    }
    return result;
  } catch {
    return {};
  }
}

export function useWidgetStyles(stylesJson?: string): WidgetStylesMap {
  return useMemo(() => parseWidgetStyles(stylesJson), [stylesJson]);
}

export function elementStyle(
  styles: WidgetStylesMap,
  key: WidgetStyleKey
): CSSProperties | undefined {
  return styles[key];
}

export const WIDGET_STYLE_KEYS_HINT =
  "card, title, body, value, unit, meta, label, badge, dot, table, chart";
