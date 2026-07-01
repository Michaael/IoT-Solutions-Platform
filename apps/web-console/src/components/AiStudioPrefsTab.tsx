import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAgentChat } from "../context/AgentChatContext";
import {
  loadAiStudioPrefs,
  saveAiStudioPrefs,
  type AiStudioPrefs,
} from "../utils/agentChatStorage";

export default function AiStudioPrefsTab() {
  const { t } = useTranslation(["ai", "common"]);
  const { chatIndex, clearLocalChatIndex, defaultRootPath } = useAgentChat();
  const [prefs, setPrefs] = useState<AiStudioPrefs>(() => loadAiStudioPrefs());
  const [savedHint, setSavedHint] = useState<string | null>(null);

  useEffect(() => {
    if (!savedHint) {
      return;
    }
    const timer = window.setTimeout(() => setSavedHint(null), 2500);
    return () => window.clearTimeout(timer);
  }, [savedHint]);

  const updatePrefs = (patch: Partial<AiStudioPrefs>) => {
    const next = { ...prefs, ...patch };
    setPrefs(next);
    saveAiStudioPrefs(next);
    setSavedHint(t("common:action.saved"));
  };

  return (
    <div className="ai-studio-prefs">
      <header className="ai-studio-prefs-intro">
        <h4>{t("prefs.title")}</h4>
        <p className="op-muted">{t("prefs.subtitle")}</p>
      </header>

      <section className="ai-studio-prefs-card">
        <h5>{t("settings.agentBehavior")}</h5>
        <div className="ai-studio-prefs-fields">
          <label className="ai-studio-prefs-field">
            <span>{t("settings.defaultRootPath")}</span>
            <input
              value={prefs.defaultRootPath}
              onChange={(e) => updatePrefs({ defaultRootPath: e.target.value })}
              placeholder="root"
            />
          </label>
          <label className="ai-studio-prefs-field">
            <span>{t("settings.defaultAppId")}</span>
            <input
              value={prefs.defaultAppId}
              onChange={(e) => updatePrefs({ defaultAppId: e.target.value })}
            />
          </label>
          <label className="ai-studio-prefs-toggle">
            <input
              type="checkbox"
              checked={prefs.restoreLastChat}
              onChange={(e) => updatePrefs({ restoreLastChat: e.target.checked })}
            />
            <span>{t("settings.restoreLastChat")}</span>
          </label>
        </div>
        <p className="hint ai-studio-prefs-hint">
          {t("settings.activeRootHint", { path: defaultRootPath })}{" "}
          {t("settings.persistenceHint")}
        </p>
        {savedHint && <p className="hint success">{savedHint}</p>}
      </section>

      <section className="ai-studio-prefs-card ai-studio-prefs-card--danger-zone">
        <div className="ai-studio-prefs-card-head">
          <div>
            <h5>{t("settings.localSessions")}</h5>
            <p className="op-muted">{t("settings.chatCountHint", { count: chatIndex.chats.length })}</p>
          </div>
          <span className="ai-studio-session-count">{chatIndex.chats.length}</span>
        </div>
        <button type="button" className="btn ai-studio-clear-btn" onClick={() => clearLocalChatIndex()}>
          {t("settings.clearLocalChats")}
        </button>
      </section>
    </div>
  );
}
