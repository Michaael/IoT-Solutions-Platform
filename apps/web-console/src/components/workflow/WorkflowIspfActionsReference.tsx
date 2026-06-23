import { useTranslation } from "react-i18next";
import { WORKFLOW_ISPF_ACTIONS } from "../../types/automation";

export default function WorkflowIspfActionsReference() {
  const { t } = useTranslation("workflow");

  return (
    <section className="workflow-ispf-reference">
      <h4>{t("ispfReference.title")}</h4>
      <p className="hint">{t("ispfReference.hint")}</p>
      <ul className="workflow-ispf-action-list">
        {WORKFLOW_ISPF_ACTIONS.map((entry) => (
          <li key={entry.action}>
            <strong>{entry.action}</strong> — {entry.label}
            <div className="hint">{entry.attrs.join(", ")}</div>
          </li>
        ))}
      </ul>
    </section>
  );
}
