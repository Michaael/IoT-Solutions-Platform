import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { createCorrelator } from "../../api";
import type { CreateCorrelatorPayload, CorrelatorPatternType } from "../../types/automation";

interface CreateCorrelatorDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: CreateCorrelatorPayload = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  patternType: "COUNT",
  eventName: "thresholdExceeded",
  secondEventName: "",
  windowSeconds: 0,
  minOccurrences: 1,
  cooldownSeconds: 120,
  actionType: "RUN_WORKFLOW",
  actionTarget: "root.platform.workflows.demo-alarm-handler",
  enabled: true,
};

export default function CreateCorrelatorDialog({ onClose, onCreated }: CreateCorrelatorDialogProps) {
  const [form, setForm] = useState<CreateCorrelatorPayload>({ ...DEFAULT });
  const isSequence = form.patternType === "SEQUENCE";
  const isEventChain = form.patternType === "EVENT_CHAIN";
  const needsSecondEvent = isSequence || isEventChain;

  const mutation = useMutation({
    mutationFn: () =>
      createCorrelator({
        ...form,
        objectPath: form.objectPath?.trim() || undefined,
        secondEventName: needsSecondEvent ? form.secondEventName?.trim() || undefined : undefined,
      }),
    onSuccess: () => onCreated(),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Новый коррелятор событий</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate();
          }}
        >
          <label>
            Имя *
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
            />
          </label>
          <label>
            Паттерн
            <select
              value={form.patternType ?? "COUNT"}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  patternType: e.target.value as CorrelatorPatternType,
                  windowSeconds: e.target.value === "SEQUENCE" && f.windowSeconds <= 0 ? 300 : f.windowSeconds,
                }))
              }
            >
              <option value="COUNT">COUNT — одно событие (N раз за окно)</option>
              <option value="SEQUENCE">SEQUENCE — A → B за окно</option>
              <option value="EVENT_CHAIN">EVENT_CHAIN — цепочка 3+ событий (через запятую)</option>
            </select>
          </label>
          <label>
            {isSequence ? "Событие A *" : "Событие *"}
            <input
              value={form.eventName}
              onChange={(e) => setForm((f) => ({ ...f, eventName: e.target.value }))}
              required
            />
          </label>
          {needsSecondEvent && (
            <label>
              {isEventChain ? "Цепочка событий (через запятую) *" : "Событие B *"}
              <input
                value={form.secondEventName ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, secondEventName: e.target.value }))}
                required
                placeholder={isEventChain ? "eventA,eventB,eventC" : "alarmActive"}
              />
            </label>
          )}
          <label className="full">
            Путь объекта
            <input
              value={form.objectPath ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, objectPath: e.target.value }))}
              placeholder="* — любой объект"
            />
          </label>
          <label>
            Окно (сек)
            <input
              type="number"
              min={isSequence ? 1 : 0}
              value={form.windowSeconds}
              onChange={(e) => setForm((f) => ({ ...f, windowSeconds: Number(e.target.value) }))}
            />
          </label>
          {!needsSecondEvent && (
            <label>
              Мин. повторений
              <input
                type="number"
                min={1}
                value={form.minOccurrences}
                onChange={(e) => setForm((f) => ({ ...f, minOccurrences: Number(e.target.value) }))}
              />
            </label>
          )}
          <label>
            Cooldown (сек)
            <input
              type="number"
              min={0}
              value={form.cooldownSeconds}
              onChange={(e) => setForm((f) => ({ ...f, cooldownSeconds: Number(e.target.value) }))}
            />
          </label>
          <label>
            Действие
            <select
              value={form.actionType}
              onChange={(e) =>
                setForm((f) => ({ ...f, actionType: e.target.value as CreateCorrelatorPayload["actionType"] }))
              }
            >
              <option value="RUN_WORKFLOW">RUN_WORKFLOW — запустить workflow</option>
              <option value="FIRE_EVENT">FIRE_EVENT — опубликовать событие</option>
            </select>
          </label>
          <label className="full">
            {form.actionType === "FIRE_EVENT" ? "Имя события (actionTarget) *" : "Цель (путь workflow) *"}
            <input
              value={form.actionTarget}
              onChange={(e) => setForm((f) => ({ ...f, actionTarget: e.target.value }))}
              required
            />
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
            Включено
          </label>
          {mutation.error && (
            <p className="hint error full">{(mutation.error as Error).message}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>Отмена</button>
            <button type="submit" className="btn primary" disabled={mutation.isPending || !form.name}>
              Создать
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
