import { useTranslation } from "react-i18next";
import { useTheme, type ThemePreference } from "../theme";

const OPTIONS: ThemePreference[] = ["system", "light", "dark"];

export default function ThemeSwitcher() {
  const { t } = useTranslation("shell");
  const { preference, setPreference } = useTheme();

  return (
    <label className="theme-switcher" title={t("admin.theme.label")}>
      <span className="sr-only">{t("admin.theme.label")}</span>
      <select
        value={preference}
        aria-label={t("admin.theme.label")}
        onChange={(event) => setPreference(event.target.value as ThemePreference)}
      >
        {OPTIONS.map((option) => (
          <option key={option} value={option}>
            {t(`admin.theme.${option}`)}
          </option>
        ))}
      </select>
    </label>
  );
}
