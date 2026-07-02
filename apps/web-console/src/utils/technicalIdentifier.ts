export type TechnicalIdentifierKind =
  | "code"
  | "pathSegment"
  | "dottedName"
  | "securityName";

export const TECHNICAL_IDENTIFIER_PATTERNS: Record<TechnicalIdentifierKind, RegExp> = {
  code: /^[A-Za-z_][A-Za-z0-9_]*$/,
  pathSegment: /^[A-Za-z0-9_-]+$/,
  dottedName: /^[A-Za-z0-9._-]+$/,
  securityName: /^[A-Za-z0-9._-]{2,64}$/,
};

export function isTechnicalIdentifier(value: string, kind: TechnicalIdentifierKind): boolean {
  return value.length > 0 && value === value.trim() && TECHNICAL_IDENTIFIER_PATTERNS[kind].test(value);
}
