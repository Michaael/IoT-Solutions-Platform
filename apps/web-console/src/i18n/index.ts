import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import {
  detectInitialLocale,
  LOCALE_NAMESPACES,
  LOCALE_STORAGE_KEY,
  normalizeLocale,
  type AppLocale,
} from "./locales";

const localeModules = import.meta.glob<{ default: Record<string, string> }>(
  "../locales/*/*.json",
  { eager: true },
);

function buildResources(): Record<string, Record<string, Record<string, string>>> {
  const resources: Record<string, Record<string, Record<string, string>>> = {};
  for (const [path, module] of Object.entries(localeModules)) {
    const match = path.match(/locales\/([^/]+)\/([^/]+)\.json$/);
    if (!match) {
      continue;
    }
    const [, locale, namespace] = match;
    resources[locale] ??= {};
    resources[locale][namespace.replace(".json", "")] = module.default;
  }
  return resources;
}

export function persistLocale(locale: AppLocale): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // ignore
  }
  document.documentElement.lang = locale;
  const url = new URL(window.location.href);
  url.searchParams.set("lang", locale);
  window.history.replaceState({}, "", url.toString());
}

void i18n.use(initReactI18next).init({
  resources: buildResources(),
  lng: detectInitialLocale(),
  fallbackLng: "en",
  defaultNS: "common",
  ns: [...LOCALE_NAMESPACES],
  interpolation: { escapeValue: false },
  returnEmptyString: false,
});

document.documentElement.lang = i18n.language;
persistLocale(normalizeLocale(i18n.language) ?? "en");

i18n.on("languageChanged", (lng) => {
  const locale = normalizeLocale(lng) ?? "en";
  persistLocale(locale);
});

export default i18n;
