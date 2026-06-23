import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  fetchAiAgentTools,
  fetchAiContextPack,
  fetchAiProviderStatus,
} from "../api/ai";
import { useAgentChat } from "../context/AgentChatContext";
import {
  loadAiStudioPrefs,
  saveAiStudioPrefs,
  type AiStudioPrefs,
} from "../utils/agentChatStorage";

export default function AiStudioSettingsTab() {
  const { t } = useTranslation(["ai", "common"]);
  const { chatIndex, clearLocalChatIndex, defaultRootPath } = useAgentChat();
  const [prefs, setPrefs] = useState<AiStudioPrefs>(() => loadAiStudioPrefs());
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
  });
  const contextPackQuery = useQuery({
    queryKey: ["ai-context-pack"],
    queryFn: fetchAiContextPack,
  });
  const toolsQuery = useQuery({
    queryKey: ["ai-agent-tools"],
    queryFn: fetchAiAgentTools,
  });

  useEffect(() => {
    if (!savedHint) {
      return;
    }
    const timer = window.setTimeout(() => setSavedHint(null), 2500);
    return () => window.clearTimeout(timer);
  }, [savedHint]);

  const provider = providerQuery.data;
  const contextPack = contextPackQuery.data;

  const updatePrefs = (patch: Partial<AiStudioPrefs>) => {
    const next = { ...prefs, ...patch };
    setPrefs(next);
    saveAiStudioPrefs(next);
    setSavedHint(t("common:action.saved"));
  };

  return (
    <div className="ai-studio-settings">
      <div className="ai-studio-settings-grid">
        <section className="panel-card ai-studio-settings-card">
          <h4>{t("settings.llmProvider")}</h4>
          {providerQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
          {provider && (
            <dl className="ai-studio-dl">
              <div>
                <dt>{t("settings.status")}</dt>
                <dd>
                  <span className={`ai-studio-badge ${provider.available ? "ok" : "warn"}`}>
                    {provider.available ? t("settings.ready") : t("settings.notConfigured")}
                  </span>
                </dd>
              </div>
              <div>
                <dt>{t("settings.providerId")}</dt>
                <dd><code>{provider.providerId}</code></dd>
              </div>
              {provider.model && (
                <div>
                  <dt>{t("settings.model")}</dt>
                  <dd><code>{provider.model}</code></dd>
                </div>
              )}
              <div>
                <dt>{t("settings.enabledOnServer")}</dt>
                <dd>{provider.enabled ? t("common:action.yes") : t("common:action.no")}</dd>
              </div>
            </dl>
          )}
          {!provider?.available && (
            <p className="hint">
              {provider?.reason === "missing-api-key" ? (
                <>
                  {t("settings.missingApiKeyBefore")}{" "}
                  <code>ISPF_AI_API_KEY</code>{" "}
                  {t("settings.missingApiKeyAfter")}{" "}
                  <code>scripts/run-local-with-ai.ps1</code>.
                </>
              ) : (
                <>
                  {t("settings.missingProviderBefore")}{" "}
                  <code>ispf.ai.provider</code>{" "}
                  {t("settings.missingProviderMid")}{" "}
                  <code>base-url</code>{" "}
                  {t("settings.missingProviderAfter")}{" "}
                  <code>ispf-server</code>.
                </>
              )}
            </p>
          )}
        </section>

        <section className="panel-card ai-studio-settings-card">
          <h4>{t("settings.contextPack")}</h4>
          {contextPackQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
          {contextPack && (
            <dl className="ai-studio-dl">
              <div>
                <dt>{t("settings.version")}</dt>
                <dd><code>{contextPack.contextPackVersion}</code></dd>
              </div>
              {contextPack.platformVersion && (
                <div>
                  <dt>{t("settings.platform")}</dt>
                  <dd><code>{contextPack.platformVersion}</code></dd>
                </div>
              )}
              {contextPack.exampleCount != null && (
                <div>
                  <dt>{t("settings.examplesInPack")}</dt>
                  <dd>{contextPack.exampleCount}</dd>
                </div>
              )}
              {contextPack.generatedAt && (
                <div>
                  <dt>{t("settings.generatedAt")}</dt>
                  <dd>{new Date(contextPack.generatedAt).toLocaleString()}</dd>
                </div>
              )}
            </dl>
          )}
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>{t("settings.agentBehavior")}</h4>
          <div className="form-grid">
            <label>
              {t("settings.defaultRootPath")}
              <input
                value={prefs.defaultRootPath}
                onChange={(e) => updatePrefs({ defaultRootPath: e.target.value })}
                placeholder="root"
              />
            </label>
            <label>
              {t("settings.defaultAppId")}
              <input
                value={prefs.defaultAppId}
                onChange={(e) => updatePrefs({ defaultAppId: e.target.value })}
              />
            </label>
            <label className="full ai-studio-checkbox-label">
              <input
                type="checkbox"
                checked={prefs.restoreLastChat}
                onChange={(e) => updatePrefs({ restoreLastChat: e.target.checked })}
              />
              {" "}
              {t("settings.restoreLastChat")}
            </label>
          </div>
          <p className="hint">
            {t("settings.activeRootHint", { path: defaultRootPath })}{" "}
            {t("settings.persistenceHint")}
          </p>
          {savedHint && <p className="hint success">{savedHint}</p>}
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>{t("settings.localSessions")}</h4>
          <p className="op-muted">
            {t("settings.chatCountHint", { count: chatIndex.chats.length })}
          </p>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => clearLocalChatIndex()}>
              {t("settings.clearLocalChats")}
            </button>
          </div>
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>{t("settings.agentTools")}</h4>
          {toolsQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
          {toolsQuery.data && (
            <ul className="ai-studio-tools-list">
              {toolsQuery.data.tools.map((tool) => (
                <li key={tool.name}>
                  <code>{tool.name}</code>
                  <span className="op-muted"> — {tool.description}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
