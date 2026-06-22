import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dryRunAiDeploy,
  fetchAiContextPack,
  fetchAiProviderStatus,
  generateAiBundle,
  validateAiBundle,
} from "../api/ai";
import { fetchDeployHistory, rollbackDeploy } from "../api/applications";
import { importPackage } from "../api/packages";

const DEFAULT_MANIFEST = `{
  "version": "1.0.0",
  "displayName": "AI generated app",
  "schemaName": "app_ai_generated",
  "migrations": []
}`;

export default function AiStudioPanel() {
  const queryClient = useQueryClient();
  const [appId, setAppId] = useState("ai-generated");
  const [prompt, setPrompt] = useState(
    "Create a minimal warehouse-style bundle with one migration table, one script function, and operatorUi with a single dashboard."
  );
  const [manifestText, setManifestText] = useState(DEFAULT_MANIFEST);
  const [validationText, setValidationText] = useState<string | null>(null);
  const [dryRunText, setDryRunText] = useState<string | null>(null);

  const contextPackQuery = useQuery({
    queryKey: ["ai-context-pack"],
    queryFn: fetchAiContextPack,
  });

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
  });

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
      setManifestText(JSON.stringify(data.artifact, null, 2));
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

  const provider = providerQuery.data;
  const previewUrl = appId.trim()
    ? `/?mode=operator&app=${encodeURIComponent(appId.trim())}`
    : "/?mode=operator";

  return (
    <div className="ai-studio-panel">
      <h3>AI Studio</h3>
      <p className="op-muted">
        Platform copilot for declarative bundle generation. Publish uses the same package import API as manual deploy.
      </p>

      <div className="ai-studio-meta">
        <div>
          <strong>ContextPack:</strong>{" "}
          {contextPackQuery.data?.contextPackVersion ?? "loading..."}
        </div>
        <div>
          <strong>Provider:</strong>{" "}
          {provider ? `${provider.providerId} (${provider.available ? "ready" : "not configured"})` : "loading..."}
        </div>
      </div>

      <label>
        appId / packageId
        <input value={appId} onChange={(e) => setAppId(e.target.value)} />
      </label>

      <label className="full">
        prompt
        <textarea
          className="mono"
          rows={4}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          spellCheck={false}
        />
      </label>

      <div className="form-actions">
        <button
          type="button"
          className="btn"
          disabled={generateMutation.isPending || !provider?.available}
          onClick={() => generateMutation.mutate()}
        >
          Generate bundle
        </button>
        <button
          type="button"
          className="btn"
          disabled={validateMutation.isPending}
          onClick={() => validateMutation.mutate()}
        >
          Validate
        </button>
        <button
          type="button"
          className="btn"
          disabled={dryRunMutation.isPending}
          onClick={() => dryRunMutation.mutate()}
        >
          Dry-run deploy
        </button>
        <button
          type="button"
          className="btn primary"
          disabled={publishMutation.isPending || !appId.trim()}
          onClick={() => publishMutation.mutate()}
        >
          Publish
        </button>
        <a className="btn" href={previewUrl} target="_blank" rel="noreferrer">
          Preview operator
        </a>
      </div>

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
        <h4>Deploy history / rollback</h4>
        {historyQuery.isLoading && <p className="op-muted">Loading history…</p>}
        {historyQuery.data && historyQuery.data.length === 0 && (
          <p className="op-muted">No deploy history for this appId yet.</p>
        )}
        {historyQuery.data && historyQuery.data.length > 0 && (
          <ul className="deploy-history-list">
            {historyQuery.data.map((entry) => (
              <li key={entry.version}>
                <span>
                  v{entry.version}
                  {entry.active ? " (active)" : ""}
                  {entry.deployedAt ? ` — ${entry.deployedAt}` : ""}
                </span>
                <button
                  type="button"
                  className="btn small"
                  disabled={rollbackMutation.isPending || entry.active}
                  onClick={() => rollbackMutation.mutate(entry.version)}
                >
                  Rollback
                </button>
              </li>
            ))}
          </ul>
        )}
        {rollbackMutation.error && (
          <div className="op-alert op-alert-error">{String(rollbackMutation.error)}</div>
        )}
      </section>

      <label className="full">
        bundle manifest JSON
        <textarea
          className="mono"
          rows={16}
          value={manifestText}
          onChange={(e) => setManifestText(e.target.value)}
          spellCheck={false}
        />
      </label>

      {validationText && (
        <label className="full">
          validation
          <textarea className="mono" rows={8} readOnly value={validationText} />
        </label>
      )}

      {dryRunText && (
        <label className="full">
          dry-run
          <textarea className="mono" rows={8} readOnly value={dryRunText} />
        </label>
      )}
    </div>
  );
}
