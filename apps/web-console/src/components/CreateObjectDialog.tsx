import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery } from "@tanstack/react-query";
import { createObject } from "../api";
import { fetchInstanceTypes, instantiateModel } from "../api/models";
import { registerApplication } from "../api/applications";
import { saveReportDefinition } from "../api/reports";
import { createOperatorApp } from "../api/operatorApps";
import { useDataSourceOptions } from "./platform/useDataSourceOptions";
import {
  createDataSource,
  createMigration,
  createSqlBinding,
} from "../api/platformSql";
import { fetchDrivers } from "../api/drivers";
import { formatDriverConfigJson } from "../utils/driverDefaults";
import DriverMaturityBadge, { formatDriverOptionLabel } from "./DriverMaturityBadge";
import {
  applicationObjectPath,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
  operatorAppObjectPath,
  resolveCreateDialogMode,
} from "../utils/createObjectMode";
import type { ModelDto } from "../types/models";
import type { ObjectType } from "../types";

const INSTANCE_TYPE_PREFIX = "instance:";

const OBJECT_TYPES: ObjectType[] = [
  "CUSTOM",
  "VISUAL_GROUP",
  "DEVICE",
  "MODEL",
  "DASHBOARD",
  "REPORT",
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
  const { t } = useTranslation(["explorer", "common", "platform"]);
  const mode = resolveCreateDialogMode(parentPath);
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<ObjectType>(() => defaultObjectTypeForParent(parentPath));
  const [typeSelection, setTypeSelection] = useState<string>(() => defaultObjectTypeForParent(parentPath));

  useEffect(() => {
    const defaultType = defaultObjectTypeForParent(parentPath);
    setType(defaultType);
    setTypeSelection(defaultType);
  }, [parentPath]);
  const [driverId, setDriverId] = useState("virtual");
  const [pollIntervalMs, setPollIntervalMs] = useState(DEFAULT_POLL_INTERVAL_MS);
  const [configPreview, setConfigPreview] = useState("{}");
  const [reportDataSourcePath, setReportDataSourcePath] = useState("root.platform.data-sources.demo");
  const [schemaName, setSchemaName] = useState("");
  const [scriptId, setScriptId] = useState("");
  const [migrationVersion, setMigrationVersion] = useState("1.0.0");
  const [migrationDataSourcePath, setMigrationDataSourcePath] = useState("");
  const [migrationSql, setMigrationSql] = useState("");
  const [bindingTargetPath, setBindingTargetPath] = useState("");
  const [bindingVariable, setBindingVariable] = useState("value");
  const [bindingDataSourcePath, setBindingDataSourcePath] = useState("");
  const [bindingQuery, setBindingQuery] = useState("SELECT 1 AS cnt");

  const dataSourcesQuery = useDataSourceOptions();

  const dialogTitle = useMemo(() => {
    switch (mode) {
      case "application":
        return t("dialog.newDeployApp");
      case "operator-app":
        return t("dialog.newOperatorApp");
      case "alert-rule":
        return t("dialog.newAlertRule");
      case "correlator":
        return t("dialog.newCorrelator");
      case "report":
        return t("dialog.newReport");
      case "data-source":
        return t("dialog.newDataSource");
      case "migration":
        return t("dialog.newMigration");
      case "sql-binding":
        return t("dialog.newSqlBinding");
      default:
        return t("dialog.newObject");
    }
  }, [mode, t]);

  const driversQuery = useQuery({
    queryKey: ["drivers"],
    queryFn: fetchDrivers,
    enabled: mode === "object",
  });

  const instanceTypeFilter = useMemo(
    () => instanceTypeFilterForParent(parentPath),
    [parentPath],
  );

  const instanceTypesQuery = useQuery({
    queryKey: ["instance-types", parentPath, instanceTypeFilter],
    queryFn: () => fetchInstanceTypes(instanceTypeFilter, parentPath),
    enabled: mode === "object",
  });

  const selectedInstanceModel = useMemo(() => {
    if (!typeSelection.startsWith(INSTANCE_TYPE_PREFIX)) {
      return null;
    }
    const modelId = typeSelection.slice(INSTANCE_TYPE_PREFIX.length);
    return instanceTypesQuery.data?.find((model) => model.id === modelId) ?? null;
  }, [typeSelection, instanceTypesQuery.data]);

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
      if (mode === "alert-rule") {
        const obj = await createObject({
          parentPath,
          name,
          type: "ALERT",
          displayName: displayName || name,
          description,
          templateId: "alert-rule-v1",
        });
        return obj.path;
      }
      if (mode === "correlator") {
        const obj = await createObject({
          parentPath,
          name,
          type: "CORRELATOR",
          displayName: displayName || name,
          description,
          templateId: "correlator-v1",
        });
        return obj.path;
      }
      if (mode === "report") {
        const obj = await createObject({
          parentPath,
          name,
          type: "REPORT",
          displayName: displayName || name,
          description,
          templateId: "report-v1",
        });
        const dataSourcePath = reportDataSourcePath.trim();
        if (dataSourcePath) {
          await saveReportDefinition(obj.path, {
            title: displayName || name,
            dataSourcePath,
            query: "SELECT 1 AS value",
            parameters: [],
            columns: [{ field: "value", label: "Value" }],
          });
        }
        return obj.path;
      }
      if (mode === "data-source") {
        const created = await createDataSource({
          name,
          displayName: displayName || name,
          schemaName: schemaName.trim(),
          description,
        });
        return created.path;
      }
      if (mode === "migration") {
        const created = await createMigration({
          scriptId: scriptId.trim() || name,
          version: migrationVersion.trim() || "1.0.0",
          dataSourcePath: migrationDataSourcePath.trim(),
          sql: migrationSql,
        });
        return created.path;
      }
      if (mode === "sql-binding") {
        const created = await createSqlBinding({
          bindingId: name,
          targetObjectPath: bindingTargetPath.trim(),
          variable: bindingVariable.trim() || "value",
          dataSourcePath: bindingDataSourcePath.trim(),
          query: bindingQuery,
          refresh: "manual",
          enabled: true,
        });
        return created.path;
      }
      if (selectedInstanceModel) {
        const obj = await instantiateModel(selectedInstanceModel.id, parentPath, name, {});
        return obj.path;
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
            : type === "REPORT"
              ? "report-v1"
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
            {t("dialog.parent")} <code>{parentPath}</code>
          </p>
          {mode === "application" && (
            <p className="hint">{t("dialog.deployAppHint")}</p>
          )}
          {mode === "operator-app" && (
            <p className="hint">{t("dialog.operatorAppHint")}</p>
          )}
          {(mode === "alert-rule" || mode === "correlator") && (
            <p className="hint">{t("dialog.alertCorrelatorHint")}</p>
          )}
          {mode === "report" && (
            <>
              <p className="hint">{t("dialog.reportHint", { path: parentPath })}</p>
              <label>
                Data source *
                <select
                  value={reportDataSourcePath}
                  onChange={(e) => setReportDataSourcePath(e.target.value)}
                  required
                >
                  <option value="">{t("platform:sqlBinding.selectPlaceholder")}</option>
                  {(dataSourcesQuery.data ?? []).map((source) => (
                    <option key={source.path} value={source.path}>
                      {source.displayName} ({source.path})
                    </option>
                  ))}
                </select>
              </label>
            </>
          )}
          {mode === "data-source" && (
            <p className="hint">{t("dialog.dataSourceHint", { path: parentPath })}</p>
          )}
          {mode === "migration" && (
            <p className="hint">{t("dialog.migrationHint", { path: parentPath })}</p>
          )}
          {mode === "sql-binding" && (
            <p className="hint">{t("dialog.sqlBindingHint")}</p>
          )}
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              mutation.mutate();
            }}
          >
            <label>
              {mode === "object" ? t("dialog.namePathSegment") : t("dialog.nameOrId")}
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                pattern="[a-zA-Z0-9_-]+"
              />
            </label>
            <label>
              {t("common:field.displayName")}
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>

            {mode === "data-source" && (
              <label className="full">
                PostgreSQL schema *
                <input
                  value={schemaName}
                  onChange={(e) => setSchemaName(e.target.value)}
                  required
                  placeholder="app_myapp"
                />
              </label>
            )}

            {mode === "migration" && (
              <>
                <label>
                  Script ID
                  <input
                    value={scriptId}
                    onChange={(e) => setScriptId(e.target.value)}
                    placeholder={t("dialog.scriptIdPlaceholder")}
                  />
                </label>
                <label>
                  Version
                  <input value={migrationVersion} onChange={(e) => setMigrationVersion(e.target.value)} />
                </label>
                <label className="full">
                  Data source path
                  <input
                    value={migrationDataSourcePath}
                    onChange={(e) => setMigrationDataSourcePath(e.target.value)}
                    placeholder="root.platform.data-sources.myapp"
                  />
                </label>
                <label className="full">
                  {t("dialog.sqlOptional")}
                  <textarea
                    className="mono"
                    rows={4}
                    value={migrationSql}
                    onChange={(e) => setMigrationSql(e.target.value)}
                    spellCheck={false}
                  />
                </label>
              </>
            )}

            {mode === "sql-binding" && (
              <>
                <label className="full">
                  Target object path *
                  <input
                    value={bindingTargetPath}
                    onChange={(e) => setBindingTargetPath(e.target.value)}
                    placeholder="root.platform.devices.demo-sensor-01"
                    required
                  />
                </label>
                <label>
                  Variable
                  <input value={bindingVariable} onChange={(e) => setBindingVariable(e.target.value)} />
                </label>
                <label className="full">
                  Data source path *
                  <input
                    value={bindingDataSourcePath}
                    onChange={(e) => setBindingDataSourcePath(e.target.value)}
                    placeholder="root.platform.data-sources.myapp"
                    required
                  />
                </label>
                <label className="full">
                  SQL query
                  <textarea
                    className="mono"
                    rows={3}
                    value={bindingQuery}
                    onChange={(e) => setBindingQuery(e.target.value)}
                    spellCheck={false}
                  />
                </label>
              </>
            )}

            {mode === "object" && (
              <label>
                {t("dialog.type")}
                <select
                  value={typeSelection}
                  onChange={(e) => {
                    const next = e.target.value;
                    setTypeSelection(next);
                    if (!next.startsWith(INSTANCE_TYPE_PREFIX)) {
                      setType(next as ObjectType);
                    } else {
                      const model = instanceTypesQuery.data?.find(
                        (item) => item.id === next.slice(INSTANCE_TYPE_PREFIX.length)
                      );
                      if (model?.targetObjectType) {
                        setType(model.targetObjectType);
                      }
                    }
                  }}
                >
                  <optgroup label="Platform types">
                    {OBJECT_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </optgroup>
                  {(instanceTypesQuery.data?.length ?? 0) > 0 && (
                    <optgroup label={t("dialog.instanceTypes")}>
                      {(instanceTypesQuery.data ?? []).map((model: ModelDto) => (
                        <option key={model.id} value={`${INSTANCE_TYPE_PREFIX}${model.id}`}>
                          {model.name}
                          {model.targetObjectType ? ` (${model.targetObjectType})` : ""}
                        </option>
                      ))}
                    </optgroup>
                  )}
                </select>
              </label>
            )}

            {mode === "object" && type === "DEVICE" && !selectedInstanceModel && (
              <>
                <label>
                  {t("dialog.driver")}
                  <span className="inline-badge-wrap">
                    <select
                      value={driverId}
                      onChange={(e) => handleDriverChange(e.target.value)}
                      disabled={driversQuery.isLoading}
                    >
                      {(driversQuery.data ?? []).map((driver) => (
                        <option key={driver.id} value={driver.id}>
                          {formatDriverOptionLabel(driver.id, driver.name, driver.maturity)}
                        </option>
                      ))}
                    </select>
                    {selectedDriver && <DriverMaturityBadge maturity={selectedDriver.maturity} />}
                  </span>
                </label>
                <label>
                  {t("dialog.pollIntervalMs")}
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
                  {t("dialog.defaultDriverConfig")}
                  <textarea
                    className="mono readonly"
                    rows={4}
                    value={configPreview}
                    readOnly
                    spellCheck={false}
                  />
                </label>
                <p className="hint full">{t("dialog.deviceDriverHint")}</p>
              </>
            )}

            {(mode === "object" || mode === "data-source" || mode === "migration" || mode === "sql-binding") && (
              <label className="full">
                {t("common:field.description")}
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
                {t("common:action.cancel")}
              </button>
              <button
                type="submit"
                className="btn primary"
                disabled={mutation.isPending || !name || (mode === "data-source" && !schemaName.trim())}
              >
                {t("common:action.create")}
              </button>
            </footer>
          </form>
        </div>
      </div>
    </div>
  );
}
