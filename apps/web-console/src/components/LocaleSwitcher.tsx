import { useTranslation } from "react-i18next";
import { LOCALE_LABELS, SUPPORTED_LOCALES, type AppLocale } from "../i18n/locales";

export default function LocaleSwitcher() {
  const { i18n } = useTranslation();

  const current = (SUPPORTED_LOCALES.includes(i18n.language as AppLocale)
    ? i18n.language
    : "en") as AppLocale;

  return (
    <label className="locale-switcher">
      <span className="sr-only">Language</span>
      <select
        className="locale-switcher-select"
        value={current}
        onChange={(event) => void i18n.changeLanguage(event.target.value)}
        aria-label="Language"
      >
        {SUPPORTED_LOCALES.map((locale) => (
          <option key={locale} value={locale}>
            {LOCALE_LABELS[locale]}
          </option>
        ))}
      </select>
    </label>
  );
}
