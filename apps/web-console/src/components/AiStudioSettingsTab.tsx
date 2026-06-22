import { useEffect, useState } from "react";
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
    setSavedHint("Сохранено");
  };

  return (
    <div className="ai-studio-settings">
      <div className="ai-studio-settings-grid">
        <section className="panel-card ai-studio-settings-card">
          <h4>Провайдер LLM</h4>
          {providerQuery.isLoading && <p className="op-muted">Загрузка…</p>}
          {provider && (
            <dl className="ai-studio-dl">
              <div>
                <dt>Статус</dt>
                <dd>
                  <span className={`ai-studio-badge ${provider.available ? "ok" : "warn"}`}>
                    {provider.available ? "готов" : "не настроен"}
                  </span>
                </dd>
              </div>
              <div>
                <dt>ID провайдера</dt>
                <dd><code>{provider.providerId}</code></dd>
              </div>
              {provider.model && (
                <div>
                  <dt>Модель</dt>
                  <dd><code>{provider.model}</code></dd>
                </div>
              )}
              <div>
                <dt>Включён на сервере</dt>
                <dd>{provider.enabled ? "да" : "нет"}</dd>
              </div>
            </dl>
          )}
          {!provider?.available && (
            <p className="hint">
              {provider?.reason === "missing-api-key" ? (
                <>
                  Задайте <code>ISPF_AI_API_KEY</code> в файле <code>.env</code> в корне репозитория и
                  запустите сервер через <code>scripts/run-local-with-ai.ps1</code>.
                </>
              ) : (
                <>
                  Задайте <code>ispf.ai.provider</code> и <code>base-url</code> в конфигурации сервера
                  (или переменные окружения) и перезапустите <code>ispf-server</code>.
                </>
              )}
            </p>
          )}
        </section>

        <section className="panel-card ai-studio-settings-card">
          <h4>Context Pack</h4>
          {contextPackQuery.isLoading && <p className="op-muted">Загрузка…</p>}
          {contextPack && (
            <dl className="ai-studio-dl">
              <div>
                <dt>Версия</dt>
                <dd><code>{contextPack.contextPackVersion}</code></dd>
              </div>
              {contextPack.platformVersion && (
                <div>
                  <dt>Платформа</dt>
                  <dd><code>{contextPack.platformVersion}</code></dd>
                </div>
              )}
              {contextPack.exampleCount != null && (
                <div>
                  <dt>Примеров в пакете</dt>
                  <dd>{contextPack.exampleCount}</dd>
                </div>
              )}
              {contextPack.generatedAt && (
                <div>
                  <dt>Сгенерирован</dt>
                  <dd>{new Date(contextPack.generatedAt).toLocaleString()}</dd>
                </div>
              )}
            </dl>
          )}
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>Поведение агента</h4>
          <div className="form-grid">
            <label>
              Корневой путь новых сессий
              <input
                value={prefs.defaultRootPath}
                onChange={(e) => updatePrefs({ defaultRootPath: e.target.value })}
                placeholder="root"
              />
            </label>
            <label>
              AppId по умолчанию (bundle)
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
              Восстанавливать последний чат при открытии консоли
            </label>
          </div>
          <p className="hint">
            Текущий корневой путь активной сессии: <code>{defaultRootPath}</code>.
            Чаты и незавершённые запросы сохраняются в браузере и продолжают выполняться на сервере,
            даже если вы переключили вкладку или закрыли окно.
          </p>
          {savedHint && <p className="hint success">{savedHint}</p>}
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>Локальные сессии</h4>
          <p className="op-muted">
            В списке чатов: {chatIndex.chats.length}. Данные хранятся в localStorage этого браузера;
            серверные сессии не удаляются при очистке списка.
          </p>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => clearLocalChatIndex()}>
              Очистить локальный список чатов
            </button>
          </div>
        </section>

        <section className="panel-card ai-studio-settings-card ai-studio-settings-wide">
          <h4>Инструменты агента</h4>
          {toolsQuery.isLoading && <p className="op-muted">Загрузка…</p>}
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
