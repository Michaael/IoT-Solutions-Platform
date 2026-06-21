import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { updateCorrelator, fetchVariables } from "../../api";
import type { CorrelatorActionType, CorrelatorPatternType, CreateCorrelatorPayload } from "../../types/automation";
import { variableBoolean, variableNumber, variableString } from "../../utils/variableFieldValue";

interface CorrelatorInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function CorrelatorInspector({ path, canManage = false }: CorrelatorInspectorProps) {
  const queryClient = useQueryClient();
  const variablesQuery = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
  });

  const variables = variablesQuery.data ?? [];
  const patternType = (variableString(variables, "patternType") || "COUNT") as CorrelatorPatternType;
  const actionType = (variableString(variables, "actionType") || "RUN_WORKFLOW") as CorrelatorActionType;

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<CreateCorrelatorPayload>) => updateCorrelator(path, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  if (variablesQuery.isLoading) {
    return <p className="hint">Загрузка коррелятора…</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>Коррелятор событий</h3>
          <p className="hint">События → workflow (COUNT или SEQUENCE)</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          const data = new FormData(e.currentTarget);
          saveMutation.mutate({
            objectPath: String(data.get("objectPath") ?? "") || undefined,
            patternType: String(data.get("patternType") ?? "COUNT") as CorrelatorPatternType,
            eventName: String(data.get("eventName") ?? ""),
            secondEventName: String(data.get("secondEventName") ?? "") || undefined,
            windowSeconds: Number(data.get("windowSeconds") ?? 0),
            minOccurrences: Number(data.get("minOccurrences") ?? 1),
            cooldownSeconds: Number(data.get("cooldownSeconds") ?? 120),
            actionType: String(data.get("actionType") ?? "RUN_WORKFLOW") as CorrelatorActionType,
            actionTarget: String(data.get("actionTarget") ?? ""),
            enabled: data.get("enabled") === "on",
          });
        }}
      >
        <label className="full">
          Фильтр объекта (пусто = любой)
          <input
            name="objectPath"
            defaultValue={variableString(variables, "targetObjectPath")}
            readOnly={!canManage}
          />
        </label>
        <label>
          Паттерн
          <select name="patternType" defaultValue={patternType} disabled={!canManage}>
            <option value="COUNT">COUNT</option>
            <option value="SEQUENCE">SEQUENCE</option>
            <option value="EVENT_CHAIN">EVENT_CHAIN</option>
          </select>
        </label>
        <label>
          Действие
          <select name="actionType" defaultValue={actionType} disabled={!canManage}>
            <option value="RUN_WORKFLOW">RUN_WORKFLOW</option>
            <option value="FIRE_EVENT">FIRE_EVENT</option>
          </select>
        </label>
        <label>
          Событие *
          <input
            name="eventName"
            defaultValue={variableString(variables, "eventName")}
            required
            readOnly={!canManage}
          />
        </label>
        <label>
          {actionType === "FIRE_EVENT" ? "Событие (actionTarget) *" : "Второе событие / цепочка"}
          <input
            name="secondEventName"
            defaultValue={variableString(variables, "secondEventName")}
            readOnly={!canManage}
            placeholder="eventB или eventA,eventB,eventC"
          />
        </label>
        <label>
          Окно (с)
          <input
            name="windowSeconds"
            type="number"
            min={0}
            defaultValue={variableNumber(variables, "windowSeconds")}
            readOnly={!canManage}
          />
        </label>
        <label>
          Min occurrences
          <input
            name="minOccurrences"
            type="number"
            min={1}
            defaultValue={variableNumber(variables, "minOccurrences", 1)}
            readOnly={!canManage}
          />
        </label>
        <label>
          Cooldown (с)
          <input
            name="cooldownSeconds"
            type="number"
            min={0}
            defaultValue={variableNumber(variables, "cooldownSeconds", 120)}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {actionType === "FIRE_EVENT" ? "Имя события (actionTarget) *" : "Workflow (actionTarget) *"}
          <input
            name="actionTarget"
            defaultValue={variableString(variables, "actionTarget")}
            required
            readOnly={!canManage}
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={variableBoolean(variables, "enabled", true)}
            disabled={!canManage}
          />
          Включено
        </label>
        {canManage && (
          <div className="form-actions full">
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              Сохранить
            </button>
          </div>
        )}
        {saveMutation.error && (
          <p className="hint error full">{String(saveMutation.error)}</p>
        )}
      </form>
    </section>
  );
}
