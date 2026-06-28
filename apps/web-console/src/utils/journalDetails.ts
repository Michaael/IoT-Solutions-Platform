export interface AuditBeforeAfter {
  before?: unknown;
  after?: unknown;
}

export function formatJournalJson(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) {
      return "";
    }
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function parseAuditBeforeAfter(detailJson: string | null | undefined): AuditBeforeAfter {
  if (!detailJson?.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(detailJson) as AuditBeforeAfter;
    return {
      before: parsed.before,
      after: parsed.after,
    };
  } catch {
    return {};
  }
}

export function parseOptionalJson(value: string | null | undefined): unknown {
  if (!value?.trim()) {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}
