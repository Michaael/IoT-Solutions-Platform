import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  fetchAiAgentTools,
  fetchAiContextPack,
  fetchAiModels,
  fetchAiProviderStatus,
} from "../api/ai";

export default function AiStudioStatusTab() {
  const { t } = useTranslation(["ai", "common"]);
  const [toolQuery, setToolQuery] = useState("");

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
  const modelsQuery = useQuery({
    queryKey: ["ai-models"],
    queryFn: fetchAiModels,
  });

  const provider = providerQuery.data;
  const contextPack = contextPackQuery.data;
  const tools = toolsQuery.data?.tools ?? [];

  const filteredTools = useMemo(() => {
    const needle = toolQuery.trim().toLowerCase();
    if (!needle) {
      return tools;
    }
    return tools.filter(
      (tool) =>
        tool.name.toLowerCase().includes(needle) ||
        tool.description.toLowerCase().includes(needle)
    );
  }, [toolQuery, tools]);

  const loading = providerQuery.isLoading || contextPackQuery.isLoading || toolsQuery.isLoading;
  const ready = Boolean(provider?.available && tools.length > 0);

  return (
    <div className="ai-studio-status">
      <header className="ai-studio-status-hero">
        <div className="ai-studio-status-hero-main">
          <span
            className={`ai-studio-status-dot${ready ? " ok" : " warn"}`}
            aria-hidden
          />
          <div>
            <h4 className="ai-studio-status-hero-title">
              {ready ? t("status.overallReady") : t("status.overallNotReady")}
            </h4>
            <p className="ai-studio-status-hero-sub">{t("status.subtitle")}</p>
          </div>
        </div>
        {!loading && (
          <div className="ai-studio-status-hero-stats">
            <span className="ai-studio-stat-pill">
              {t("status.toolsCount", { count: tools.length })}
            </span>
            {contextPack?.contextPackVersion && (
              <span className="ai-studio-stat-pill muted">
                {contextPack.contextPackVersion}
              </span>
            )}
          </div>
        )}
      </header>

      {loading && <p className="op-muted ai-studio-status-loading">{t("common:action.loading")}</p>}

      <div className="ai-studio-metric-grid">
        <section className="ai-studio-metric-card">
          <div className="ai-studio-metric-card-head">
            <span className="ai-studio-metric-icon" aria-hidden>
              ◆
            </span>
            <h5>{t("settings.llmProvider")}</h5>
            {provider && (
              <span className={`ai-studio-badge ${provider.available ? "ok" : "warn"}`}>
                {provider.available ? t("settings.ready") : t("settings.notConfigured")}
              </span>
            )}
          </div>
          {provider && (
            <dl className="ai-studio-kv">
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
              {provider.capabilities && (
                <div>
                  <dt>{t("status.vision")}</dt>
                  <dd>
                    <span
                      className={`ai-studio-capability-badge${provider.capabilities.vision ? " ok" : " muted"}`}
                    >
                      {provider.capabilities.vision ? t("status.visionYes") : t("status.visionNo")}
                    </span>
                  </dd>
                </div>
              )}
              <div>
                <dt>{t("settings.enabledOnServer")}</dt>
                <dd>{provider.enabled ? t("common:action.yes") : t("common:action.no")}</dd>
              </div>
              {modelsQuery.data?.models && modelsQuery.data.models.length > 0 && (
                <div className="ai-studio-kv-wide">
                  <dt>{t("settings.availableModels")}</dt>
                  <dd>
                    <ul className="ai-studio-chip-list">
                      {modelsQuery.data.models.map((model) => (
                        <li key={model.id}>
                          <code>{model.id}</code>
                          {model.label ? <span className="op-muted"> — {model.label}</span> : null}
                        </li>
                      ))}
                    </ul>
                  </dd>
                </div>
              )}
            </dl>
          )}
          {!provider?.available && provider && (
            <p className="ai-studio-metric-hint hint">
              {provider.reason === "missing-api-key" ? (
                <>
                  {t("settings.missingApiKeyBefore")}{" "}
                  <code>ISPF_AI_API_KEY</code> {t("settings.missingApiKeyAfter")}{" "}
                  <code>scripts/run-local-with-ai.ps1</code>.
                </>
              ) : (
                <>
                  {t("settings.missingProviderBefore")}{" "}
                  <code>ispf.ai.provider</code> {t("settings.missingProviderMid")}{" "}
                  <code>base-url</code> {t("settings.missingProviderAfter")}{" "}
                  <code>ispf-server</code>.
                </>
              )}
            </p>
          )}
        </section>

        <section className="ai-studio-metric-card">
          <div className="ai-studio-metric-card-head">
            <span className="ai-studio-metric-icon context" aria-hidden>
              ▣
            </span>
            <h5>{t("settings.contextPack")}</h5>
          </div>
          {contextPack && (
            <dl className="ai-studio-kv">
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
      </div>

      <section className="ai-studio-tools-panel">
        <div className="ai-studio-tools-panel-head">
          <div>
            <h5>{t("settings.agentTools")}</h5>
            <p className="op-muted">{t("status.toolsIntro")}</p>
          </div>
          <input
            type="search"
            className="ai-studio-tools-search"
            value={toolQuery}
            onChange={(e) => setToolQuery(e.target.value)}
            placeholder={t("status.searchTools")}
            aria-label={t("status.searchTools")}
          />
        </div>
        {filteredTools.length === 0 && !toolsQuery.isLoading && (
          <p className="op-muted">{t("status.noToolsMatch")}</p>
        )}
        <ul className="ai-studio-tool-grid">
          {filteredTools.map((tool) => (
            <li key={tool.name} className="ai-studio-tool-card">
              <code className="ai-studio-tool-name">{tool.name}</code>
              <p className="ai-studio-tool-desc">{tool.description}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
