import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  fetchPlatformRuntimeSettings,
  patchPlatformRuntimeSettings,
  type PlatformRuntimeSetting,
  type PlatformRuntimeSettingsSection,
} from "../api/platformRuntimeSettings";

const QUICK_BOOLEAN_IDS = [
  "redis.enabled",
  "nats.enabled",
  "ai.enabled",
  "mcp.enabled",
] as const;

const QUICK_SELECT_SETTINGS: Record<string, readonly string[]> = {
  "event-journal.store": ["jdbc", "clickhouse"],
  "variable-history.store": ["jdbc", "jpa", "clickhouse"],
  "ai.provider": ["noop", "openai-compatible", "ollama", "custom-url"],
};

function findSetting(
  sections: PlatformRuntimeSettingsSection[],
  id: string,
): PlatformRuntimeSetting | undefined {
  for (const section of sections) {
    const match = section.settings.find((setting) => setting.id === id);
    if (match) {
      return match;
    }
  }
  return undefined;
}

function IntegrationQuickToggles({
  sections,
  drafts,
  onDraftChange,
}: {
  sections: PlatformRuntimeSettingsSection[];
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
}) {
  const { t } = useTranslation(["system", "common"]);

  const quickSettings = useMemo(() => {
    const ids = [...QUICK_BOOLEAN_IDS, ...Object.keys(QUICK_SELECT_SETTINGS)];
    return ids
      .map((id) => findSetting(sections, id))
      .filter((setting): setting is PlatformRuntimeSetting => setting != null);
  }, [sections]);

  if (quickSettings.length === 0) {
    return null;
  }

  return (
    <section className="system-metrics-card system-settings-quick">
      <h3>{t("settings.quickToggles.title")}</h3>
      <p className="hint system-settings-quick-hint">{t("settings.quickToggles.hint")}</p>
      <div className="system-settings-quick-grid">
        {quickSettings.map((setting) => {
          const draftValue = drafts[setting.id] ?? setting.value;
          const isBoolean = setting.type === "boolean";
          const selectOptions = QUICK_SELECT_SETTINGS[setting.id];

          return (
            <label
              key={setting.id}
              className={`system-settings-quick-item${setting.editable ? "" : " system-settings-quick-item-locked"}`}
            >
              <span className="system-settings-quick-label">
                {t(`settings.keys.${setting.id}`, setting.id)}
              </span>
              {isBoolean ? (
                <input
                  type="checkbox"
                  className="system-settings-quick-checkbox"
                  checked={draftValue === "true"}
                  disabled={!setting.editable}
                  onChange={(event) => onDraftChange(setting.id, event.target.checked ? "true" : "false")}
                />
              ) : (
                <select
                  className="system-settings-input"
                  value={draftValue}
                  disabled={!setting.editable}
                  onChange={(event) => onDraftChange(setting.id, event.target.value)}
                >
                  {(selectOptions ?? [draftValue]).map((option) => (
                    <option key={option} value={option}>
                      {t(`settings.options.${setting.id}.${option}`, option)}
                    </option>
                  ))}
                </select>
              )}
              <span className="hint system-settings-quick-meta">
                <code>{setting.envVar}</code>
                {setting.overridesEnvironment && setting.environmentValue != null && (
                  <> · {t("settings.envOverrideHint", { value: setting.environmentValue })}</>
                )}
                {setting.restartRequired && <> · {t("settings.quickToggles.restartRequired")}</>}
              </span>
            </label>
          );
        })}
      </div>
    </section>
  );
}

function SettingRow({
  setting,
  draftValue,
  onChange,
}: {
  setting: PlatformRuntimeSetting;
  draftValue: string;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation(["system", "common"]);

  const input = setting.type === "boolean" ? (
    <select
      className="system-settings-input"
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="true">{t("common:action.yes")}</option>
      <option value="false">{t("common:action.no")}</option>
    </select>
  ) : (
    <input
      className="system-settings-input"
      type={setting.type === "integer" ? "number" : "text"}
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    />
  );

  return (
    <tr className={setting.editable ? "" : "system-settings-row-locked"}>
      <th scope="row">
        <div className="system-settings-label">{t(`settings.keys.${setting.id}`, setting.id)}</div>
        <code className="system-settings-env">{setting.envVar}</code>
      </th>
      <td>{input}</td>
      <td>
        <span className={`system-settings-source system-settings-source-${setting.source}`}>
          {t(`settings.source.${setting.source}`)}
        </span>
        {setting.hotReloadable && (
          <span className="hint system-settings-badge">{t("settings.hotReload")}</span>
        )}
        {setting.overridesEnvironment && setting.environmentValue != null && (
          <span className="hint">{t("settings.envOverrideHint", { value: setting.environmentValue })}</span>
        )}
      </td>
      <td className="mono">{setting.defaultValue || t("common:empty.dash")}</td>
    </tr>
  );
}

function SettingsSectionCard({
  section,
  drafts,
  onDraftChange,
}: {
  section: PlatformRuntimeSettingsSection;
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
}) {
  const { t } = useTranslation("system");

  return (
    <section className="system-metrics-card system-settings-card">
      <h3>{t(`settings.sections.${section.id}`, section.title)}</h3>
      <table className="op-table system-settings-table">
        <thead>
          <tr>
            <th>{t("settings.column.setting")}</th>
            <th>{t("settings.column.value")}</th>
            <th>{t("settings.column.source")}</th>
            <th>{t("settings.column.default")}</th>
          </tr>
        </thead>
        <tbody>
          {section.settings.map((setting) => (
            <SettingRow
              key={setting.id}
              setting={setting}
              draftValue={drafts[setting.id] ?? setting.value}
              onChange={(value) => onDraftChange(setting.id, value)}
            />
          ))}
        </tbody>
      </table>
    </section>
  );
}

export default function SystemSettingsView() {
  const { t } = useTranslation(["system", "common"]);
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: ["platform-runtime-settings"],
    queryFn: fetchPlatformRuntimeSettings,
  });
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState<string | null>(null);

  const mergedDrafts = useMemo(() => {
    const base: Record<string, string> = {};
    for (const section of settingsQuery.data?.sections ?? []) {
      for (const setting of section.settings) {
        base[setting.id] = drafts[setting.id] ?? setting.value;
      }
    }
    return base;
  }, [drafts, settingsQuery.data]);

  const patchMutation = useMutation({
    mutationFn: patchPlatformRuntimeSettings,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["platform-runtime-settings"] });
      setDrafts({});
      const parts: string[] = [];
      if (result.appliedLive.length > 0) {
        parts.push(t("settings.feedback.appliedLive", { count: result.appliedLive.length }));
      }
      if (result.restartRequired) {
        parts.push(t("settings.feedback.restartRequired"));
      }
      if (result.skippedEnvLocked.length > 0) {
        parts.push(t("settings.feedback.skippedEnv", { count: result.skippedEnvLocked.length }));
      }
      if (result.errors.length > 0) {
        parts.push(result.errors.join("; "));
      }
      setFeedback(parts.join(" "));
    },
    onError: (error: Error) => setFeedback(error.message),
  });

  const dirtyValues = useMemo(() => {
    const values: Record<string, string> = {};
    for (const section of settingsQuery.data?.sections ?? []) {
      for (const setting of section.settings) {
        if (!setting.editable || setting.sensitive) {
          continue;
        }
        const draft = mergedDrafts[setting.id];
        if (draft != null && draft !== setting.value) {
          values[setting.id] = draft;
        }
      }
    }
    return values;
  }, [mergedDrafts, settingsQuery.data]);

  return (
    <div className="system-settings-view">
      <div className="system-embedded-toolbar">
        <button
          type="button"
          className="btn"
          disabled={settingsQuery.isFetching}
          onClick={() => settingsQuery.refetch()}
        >
          {t("settings.refresh")}
        </button>
        <button
          type="button"
          className="btn primary"
          disabled={patchMutation.isPending || Object.keys(dirtyValues).length === 0}
          onClick={() => patchMutation.mutate(dirtyValues)}
        >
          {t("settings.saveChanges")}
        </button>
      </div>

      <p className="hint system-settings-intro">{t("settings.intro")}</p>
      {settingsQuery.data?.settingsFile && (
        <p className="hint system-settings-file">
          {t("settings.overrideFile")}: <code>{settingsQuery.data.settingsFile}</code>
        </p>
      )}

      {feedback && <div className="op-alert">{feedback}</div>}
      {settingsQuery.error && (
        <div className="op-alert op-alert-error">{String(settingsQuery.error)}</div>
      )}
      {settingsQuery.isLoading && <p className="hint">{t("settings.loading")}</p>}

      {settingsQuery.data && (
        <IntegrationQuickToggles
          sections={settingsQuery.data.sections}
          drafts={mergedDrafts}
          onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
        />
      )}

      <div className="system-settings-sections">
        {settingsQuery.data?.sections.map((section) => (
          <SettingsSectionCard
            key={section.id}
            section={section}
            drafts={mergedDrafts}
            onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
          />
        ))}
      </div>
    </div>
  );
}
