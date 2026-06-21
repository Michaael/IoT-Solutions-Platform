import { WORKFLOW_ISPF_ACTIONS } from "../../types/automation";

export default function WorkflowIspfActionsReference() {
  return (
    <section className="workflow-ispf-reference">
      <h4>ISPF service task actions</h4>
      <p className="hint">
        На service task задайте атрибут <code>ispf:action</code> и поля из таблицы (в XML или через редактор).
      </p>
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
