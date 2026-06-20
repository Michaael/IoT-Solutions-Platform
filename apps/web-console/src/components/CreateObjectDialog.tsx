import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { createObject } from "../api";
import { registerApplication } from "../api/applications";
import { createOperatorApp } from "../api/operatorApps";
import { fetchDrivers } from "../api/drivers";
import { formatDriverConfigJson } from "../utils/driverDefaults";
import {
  applicationObjectPath,
  operatorAppObjectPath,
  resolveCreateDialogMode,
} from "../utils/createObjectMode";
import type { ObjectType } from "../types";

const OBJECT_TYPES: ObjectType[] = [
  "CUSTOM",
  "DEVICE",
  "MODEL",
  "DASHBOARD",
  "WORKFLOW",
  "ALERT",
  "AGENT",
  "USER",
  "TENANT",
  "DRIVER",
];

const DEFAULT_POLL_INTERVAL_MS = 5000;

interface CreateObjectDialogProps {
  parentPath: string;
  onClose: () => void;
  onCreated: (path: string) => void;
}

export default function CreateObjectDialog({
  parentPath,
  onClose,
  onCreated,
}: CreateObjectDialogProps) {
  const mode = resolveCreateDialogMode(parentPath);
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<ObjectType>("CUSTOM");
  const [driverId, setDriverId] = useState("virtual");
  const [pollIntervalMs, setPollIntervalMs] = useState(DEFAULT_POLL_INTERVAL_MS);
  const [configPreview, setConfigPreview] = useState("{}");

  const dialogTitle = useMemo(() => {
    switch (mode) {
      case "application":
        return "Новое deploy-приложение";
      case "operator-app":
        return "Новое operator-приложение";
      default:
        return "Новый объект";
    }
  }, [mode]);

  const driversQuery = useQuery({
    queryKey: ["drivers"],
    queryFn: fetchDrivers,
    enabled: mode === "object",
  });

  const selectedDriver = useMemo(
    () => driversQuery.data?.find((driver) => driver.id === driverId),
    [driversQuery.data, driverId]
  );

  useEffect(() => {
    if (type !== "DEVICE") {
      return;
    }
    setConfigPreview(formatDriverConfigJson(selectedDriver));
  }, [type, selectedDriver]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === "application") {
        await registerApplication({
          appId: name,
          displayName: displayName || name,
        });
        return applicationObjectPath(name);
      }
      if (mode === "operator-app") {
        await createOperatorApp(name, displayName || name);
        return operatorAppObjectPath(name);
      }
      const obj = await createObject({
        parentPath,
        name,
        type,
        displayName: displayName || name,
        description,
        templateId:
          type === "DASHBOARD"
            ? "dashboard-v1"
            : type === "WORKFLOW"
              ? "workflow-v1"
              : undefined,
        driverId: type === "DEVICE" ? driverId : undefined,
        driverPollIntervalMs: type === "DEVICE" ? pollIntervalMs : undefined,
        autoStartDriver: false,
      });
      return obj.path;
    },
    onSuccess: (path) => onCreated(path),
  });

  const handleDriverChange = (nextDriverId: string) => {
    setDriverId(nextDriverId);
    const driver = driversQuery.data?.find((item) => item.id === nextDriverId);
    setConfigPreview(formatDriverConfigJson(driver));
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-create-object" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{dialogTitle}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </header>
        <div className="modal-body">
          <p className="hint">
            Родитель: <code>{parentPath}</code>
          </p>
          {mode === "application" && (
            <p className="hint">
              Deploy-приложение: регистрация в <code>Applications</code>, затем{" "}
              <code>POST /api/v1/applications/&#123;id&#125;/deploy</code> с bundle.
            </p>
          )}
          {mode === "operator-app" && (
            <p className="hint">
              Operator app для <code>?mode=operator&amp;app=&lt;id&gt;</code>. После создания
              выберите дашборды в дочернем объекте.
            </p>
          )}
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              mutation.mutate();
            }}
          >
            <label>
              {mode === "object" ? "Имя (сегмент пути) *" : "App ID *"}
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                pattern="[a-zA-Z0-9_-]+"
              />
            </label>
            <label>
              Отображаемое имя
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>

            {mode === "object" && (
              <label>
                Тип
                <select value={type} onChange={(e) => setType(e.target.value as ObjectType)}>
                  {OBJECT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {mode === "object" && type === "DEVICE" && (
              <>
                <label>
                  Драйвер *
                  <select
                    value={driverId}
                    onChange={(e) => handleDriverChange(e.target.value)}
                    disabled={driversQuery.isLoading}
                  >
                    {(driversQuery.data ?? []).map((driver) => (
                      <option key={driver.id} value={driver.id}>
                        {driver.id} — {driver.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Интервал опроса (мс)
                  <input
                    type="number"
                    min={500}
                    step={500}
                    value={pollIntervalMs}
                    onChange={(e) =>
                      setPollIntervalMs(Number(e.target.value) || DEFAULT_POLL_INTERVAL_MS)
                    }
                  />
                </label>
                {selectedDriver?.description && (
                  <p className="hint full">{selectedDriver.description}</p>
                )}
                <label className="full">
                  Конфигурация по умолчанию (driverConfigJson)
                  <textarea
                    className="mono readonly"
                    rows={4}
                    value={configPreview}
                    readOnly
                    spellCheck={false}
                  />
                </label>
                <p className="hint full">
                  При создании применяется модель <code>device-driver-v1</code> и сохраняются
                  настройки драйвера. Запустите драйвер на вкладке «Драйвер» после создания.
                </p>
              </>
            )}

            {mode === "object" && (
              <label className="full">
                Описание
                <textarea
                  rows={2}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>
            )}
            {mutation.error && (
              <p className="hint error full">{(mutation.error as Error).message}</p>
            )}
            <footer className="full form-actions">
              <button type="button" className="btn" onClick={onClose}>
                Отмена
              </button>
              <button
                type="submit"
                className="btn primary"
                disabled={mutation.isPending || !name}
              >
                Создать
              </button>
            </footer>
          </form>
        </div>
      </div>
    </div>
  );
}
