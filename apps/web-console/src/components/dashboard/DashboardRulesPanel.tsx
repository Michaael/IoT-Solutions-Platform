import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import BindingRulesPanel from "../BindingRulesPanel";
import { dashboardRuleTemplates } from "./dashboardRuleTemplates";
import type { DashboardWidget } from "../../types/dashboard";

interface DashboardRulesPanelProps {
  path: string;
  widgets: DashboardWidget[];
  canManage?: boolean;
}

export default function DashboardRulesPanel({
  path,
  widgets,
  canManage = true,
}: DashboardRulesPanelProps) {
  const { t } = useTranslation("dashboard");
  const widgetIds = useMemo(() => widgets.map((widget) => widget.id), [widgets]);
  const templates = useMemo(() => dashboardRuleTemplates(widgetIds), [widgetIds]);

  return (
    <aside className="dashboard-rules-sidebar panel">
      <header className="dashboard-rules-sidebar__header">
        <h3>{t("rules.title")}</h3>
        <p className="hint">{t("rules.hint")}</p>
      </header>
      <BindingRulesPanel
        path={path}
        canManage={canManage}
        dashboardMode
        ruleTemplates={templates.map((template) => ({
          id: template.id,
          label: t(template.labelKey),
          rule: template.rule,
        }))}
      />
    </aside>
  );
}
