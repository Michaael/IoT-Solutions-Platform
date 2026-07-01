import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AiAgentChat from "./AiAgentChat";
import AiStudioBundleTab, { defaultBundleManifest } from "./AiStudioBundleTab";
import AiStudioPrefsTab from "./AiStudioPrefsTab";
import AiStudioStatusTab from "./AiStudioStatusTab";
import { useAgentChat } from "../context/AgentChatContext";
import { loadAiStudioPrefs, saveAiStudioPrefs } from "../utils/agentChatStorage";

export type StudioMode = "agent" | "bundle" | "status" | "prefs";

export default function AiStudioPanel() {
  const { t } = useTranslation("ai");
  const { isPending } = useAgentChat();
  const [mode, setMode] = useState<StudioMode>(() => loadAiStudioPrefs().lastTab);
  const [appId, setAppId] = useState(() => loadAiStudioPrefs().defaultAppId);
  const [prompt, setPrompt] = useState(
    "Create a minimal warehouse-style bundle with one migration table, one script function, and operatorUi with a single dashboard."
  );
  const [manifestText, setManifestText] = useState(defaultBundleManifest);
  const [validationText, setValidationText] = useState<string | null>(null);
  const [dryRunText, setDryRunText] = useState<string | null>(null);

  const backgroundBusy = isPending;

  useEffect(() => {
    const prefs = loadAiStudioPrefs();
    saveAiStudioPrefs({ ...prefs, lastTab: mode });
  }, [mode]);

  const tabs: StudioMode[] = ["agent", "bundle", "status", "prefs"];

  return (
    <div className="ai-studio-panel">
      <header className="ai-studio-head">
        <h3>{t("studio.title")}</h3>
        {backgroundBusy && mode !== "agent" && (
          <div className="ai-studio-background-hint" role="status">
            <span className="ai-agent-status-bar-pulse" aria-hidden />
            {t("studio.backgroundBusy")}
          </div>
        )}
      </header>

      <nav className="tabs ai-studio-tabs" aria-label={t("studio.sectionsAria")}>
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            className={mode === tab ? "active" : ""}
            onClick={() => setMode(tab)}
          >
            {t(`studio.tab.${tab}`)}
            {tab === "agent" && backgroundBusy && (
              <span className="tab-pending-dot" title={t("agent.pendingTitle")} />
            )}
          </button>
        ))}
      </nav>

      <div className="ai-studio-body">
        <div className={`ai-studio-tab-layer ${mode === "agent" ? "active" : "dormant"}`}>
          <AiAgentChat />
        </div>

        <div className={`ai-studio-tab-layer ${mode === "bundle" ? "active" : "dormant"}`}>
          <AiStudioBundleTab
            appId={appId}
            setAppId={setAppId}
            prompt={prompt}
            setPrompt={setPrompt}
            manifestText={manifestText}
            setManifestText={setManifestText}
            validationText={validationText}
            setValidationText={setValidationText}
            dryRunText={dryRunText}
            setDryRunText={setDryRunText}
          />
        </div>

        <div className={`ai-studio-tab-layer ${mode === "status" ? "active" : "dormant"}`}>
          <AiStudioStatusTab />
        </div>

        <div className={`ai-studio-tab-layer ${mode === "prefs" ? "active" : "dormant"}`}>
          <AiStudioPrefsTab />
        </div>
      </div>
    </div>
  );
}
