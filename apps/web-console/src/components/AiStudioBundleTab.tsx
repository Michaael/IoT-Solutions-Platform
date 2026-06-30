import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  dryRunAiDeploy,
  generateAiBundle,
  validateAiBundle,
} from "../api/ai";
import { fetchDeployHistory, rollbackDeploy } from "../api/applications";
import { importPackage } from "../api/packages";
import { useAgentChat } from "../context/AgentChatContext";

const DEFAULT_MANIFEST = `{
  "version": "1.0.0",
  "displayName": "AI generated app",
  "schemaName": "app_ai_generated",
  "migrations": []
}`;

interface AiStudioBundleTabProps {
  appId: string;
  setAppId: (value: string) => void;
  prompt: string;
  setPrompt: (value: string) => void;
  manifestText: string;
  setManifestText: (value: string) => void;
  validationText: string | null;
  setValidationText: (value: string | null) => void;
  dryRunText: string | null;
  setDryRunText: (value: string | null) => void;
}

export function defaultBundleManifest(): string {
  return DEFAULT_MANIFEST;
}

export default function AiStudioBundleTab({
  appId,
  setAppId,
  prompt,
  setPrompt,
  manifestText,
  setManifestText,
  validationText,
  setValidationText,
  dryRunText,
  setDryRunText,
}: AiStudioBundleTabProps) {
  const { t } = useTranslation("ai");
  const { provider } = useAgentChat();
  const queryClient = useQueryClient();

  const historyQuery = useQuery({
    queryKey: ["deploy-history", appId],
    queryFn: () => fetchDeployHistory(appId.trim()),
    enabled: Boolean(appId.trim()),
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: string) => rollbackDeploy(appId.trim(), version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return validateAiBundle(appId.trim(), manifest);
    },
    onSuccess: (data) => setValidationText(JSON.stringify(data, null, 2)),
  });

  const dryRunMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return dryRunAiDeploy(appId.trim(), manifest);
    },
    onSuccess: (data) => setDryRunText(JSON.stringify(data, null, 2)),
  });

  const generateMutation = useMutation({
    mutationFn: () => generateAiBundle(appId.trim(), prompt),
    onSuccess: (data) => {
      const artifact = data.artifact as Record<string, unknown> | undefined;
      if (data.publishable && artifact && Object.keys(artifact).length > 0) {
        setManifestText(JSON.stringify(artifact, null, 2));
      }
      setValidationText(JSON.stringify(data.validation, null, 2));
      setDryRunText(JSON.stringify(data.dryRun, null, 2));
    },
  });

  const publishMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return importPackage(appId.trim(), manifest);
    },
  });

  const previewUrl = appId.trim()
    ? `/?mode=operator&app=${encodeURIComponent(appId.trim())}`
    : "/?mode=operator";

  return (
    <div className="panel-card">
      <div className="form-grid">
        <label>
          {t("bundle.appId")}
          <input value={appId} onChange={(e) => setAppId(e.target.value)} />
        </label>

        <label className="full">
          {t("bundle.prompt")}
          <textarea
            className="code-field mono"
            rows={4}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            spellCheck={false}
          />
        </label>
      </div>

      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={generateMutation.isPending || !provider?.available}
          onClick={() => generateMutation.mutate()}
        >
          {generateMutation.isPending ? t("bundle.generating") : t("bundle.generate")}
        </button>
        <button
          type="button"
          className="btn"
          disabled={validateMutation.isPending}
          onClick={() => validateMutation.mutate()}
        >
          {t("bundle.validate")}
        </button>
        <button
          type="button"
          className="btn"
          disabled={dryRunMutation.isPending}
          onClick={() => dryRunMutation.mutate()}
        >
          {t("bundle.dryRun")}
        </button>
        <button
          type="button"
          className="btn primary"
          disabled={publishMutation.isPending || !appId.trim()}
          onClick={() => publishMutation.mutate()}
        >
          {t("bundle.publish")}
        </button>
        <a className="btn" href={previewUrl} target="_blank" rel="noreferrer">
          {t("bundle.operatorPreview")}
        </a>
      </div>

      {generateMutation.data && !generateMutation.data.publishable && (
        <div className="op-alert op-alert-error">
          {t("bundle.notPublishable")}
        </div>
      )}

      {(generateMutation.error || validateMutation.error || dryRunMutation.error || publishMutation.error) && (
        <div className="op-alert op-alert-error">
          {String(
            generateMutation.error
              || validateMutation.error
              || dryRunMutation.error
              || publishMutation.error
          )}
        </div>
      )}

      {publishMutation.data && (
        <pre className="mono small">{JSON.stringify(publishMutation.data, null, 2)}</pre>
      )}

      <section className="ai-studio-history">
        <h4>{t("bundle.deployHistory")}</h4>
        {historyQuery.isLoading && <p className="op-muted">{t("bundle.loadingHistory")}</p>}
        {historyQuery.data && historyQuery.data.length === 0 && (
          <p className="op-muted">{t("bundle.noDeployHistory")}</p>
        )}
        {historyQuery.data && historyQuery.data.length > 0 && (
          <ul className="deploy-history-list">
            {historyQuery.data.map((entry) => (
              <li key={entry.version}>
                <span>
                  v{entry.version}
                  {entry.active ? t("bundle.active") : ""}
                  {entry.deployedAt ? ` — ${entry.deployedAt}` : ""}
                </span>
                <button
                  type="button"
                  className="btn small"
                  disabled={rollbackMutation.isPending || entry.active}
                  onClick={() => rollbackMutation.mutate(entry.version)}
                >
                  {t("bundle.rollback")}
                </button>
              </li>
            ))}
          </ul>
        )}
        {rollbackMutation.error && (
          <div className="op-alert op-alert-error">{String(rollbackMutation.error)}</div>
        )}
      </section>

      <div className="form-grid">
        <label className="full">
          {t("bundle.manifestJson")}
          <textarea
            className="json-editor"
            rows={16}
            value={manifestText}
            onChange={(e) => setManifestText(e.target.value)}
            spellCheck={false}
          />
        </label>

        {validationText && (
          <label className="full">
            {t("bundle.validationResult")}
            <textarea className="code-field mono" rows={8} readOnly value={validationText} />
          </label>
        )}

        {dryRunText && (
          <label className="full">
            {t("bundle.dryRun")}
            <textarea className="code-field mono" rows={8} readOnly value={dryRunText} />
          </label>
        )}
      </div>
    </div>
  );
}
