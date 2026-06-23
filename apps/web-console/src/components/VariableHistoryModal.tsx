import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchVariables } from "../api";
import { historizableFieldsFromVariable } from "../utils/variableHistoryFields";
import VariableHistoryPanel from "./VariableHistoryPanel";

interface VariableHistoryModalProps {
  objectPath: string;
  variableName: string;
  valueField?: string;
  title?: string;
  onClose: () => void;
}

export default function VariableHistoryModal({
  objectPath,
  variableName,
  valueField,
  title,
  onClose,
}: VariableHistoryModalProps) {
  const { t } = useTranslation("common");
  const variablesQuery = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath),
  });

  const variable = variablesQuery.data?.find((item) => item.name === variableName);
  const fields = variable
    ? historizableFieldsFromVariable(variable)
    : [valueField ?? "value"];

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal modal-wide modal-variable-history"
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <h3>{title ?? variableName}</h3>
          <button type="button" className="icon-btn" onClick={onClose} aria-label={t("action.close")}>
            ✕
          </button>
        </header>
        <p className="hint modal-variable-history-path">
          <code>{objectPath}</code> · <code>{variableName}</code>
        </p>
        <VariableHistoryPanel
          objectPath={objectPath}
          variableName={variableName}
          fields={fields}
          refreshIntervalMs={30_000}
        />
      </div>
    </div>
  );
}
