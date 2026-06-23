export const LOCALE_STORAGE_KEY = "ispf.ui.locale";

export type AppLocale = "en" | "ru" | "de" | "zh";

export const SUPPORTED_LOCALES: AppLocale[] = ["en", "ru", "de", "zh"];

export const LOCALE_LABELS: Record<AppLocale, string> = {
  en: "English",
  ru: "Русский",
  de: "Deutsch",
  zh: "中文",
};

export const LOCALE_NAMESPACES = [
  "common",
  "shell",
  "explorer",
  "inspector",
  "dashboard",
  "widgets",
  "workflow",
  "report",
  "operator",
  "federation",
  "system",
  "security",
  "ai",
  "automation",
  "platform",
  "runtime",
] as const;

export type LocaleNamespace = (typeof LOCALE_NAMESPACES)[number];

export function normalizeLocale(value: string | null | undefined): AppLocale | null {
  if (!value) {
    return null;
  }
  const lower = value.toLowerCase().split("-")[0];
  if (lower === "zh" || value.toLowerCase().startsWith("zh")) {
    return "zh";
  }
  if (SUPPORTED_LOCALES.includes(lower as AppLocale)) {
    return lower as AppLocale;
  }
  return null;
}

export function detectInitialLocale(): AppLocale {
  const params = new URLSearchParams(window.location.search);
  const fromUrl = normalizeLocale(params.get("lang"));
  if (fromUrl) {
    return fromUrl;
  }
  try {
    const stored = normalizeLocale(localStorage.getItem(LOCALE_STORAGE_KEY));
    if (stored) {
      return stored;
    }
  } catch {
    // ignore private mode
  }
  const browser = normalizeLocale(navigator.language);
  if (browser) {
    return browser;
  }
  return "en";
}
