import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type {
  InboundRegistration,
  OutboundAgent,
  TunnelSession,
} from "../../api/federation";
import { copyToClipboard } from "./federationShared";

interface FederationTunnelTabProps {
  inboundQuery: UseQueryResult<InboundRegistration[], Error>;
  outboundQuery: UseQueryResult<OutboundAgent[], Error>;
  tunnelsQuery: UseQueryResult<TunnelSession[], Error>;
  inboundName: string;
  setInboundName: (value: string) => void;
  inboundPathPrefix: string;
  setInboundPathPrefix: (value: string) => void;
  issuedRegistrationCode: string | null;
  outboundForm: {
    name: string;
    hubBaseUrl: string;
    registrationCode: string;
    pathPrefix: string;
  };
  setOutboundForm: React.Dispatch<
    React.SetStateAction<{
      name: string;
      hubBaseUrl: string;
      registrationCode: string;
      pathPrefix: string;
    }>
  >;
  secretsKeyInput: string;
  setSecretsKeyInput: (value: string) => void;
  secretsKeyError: string | null;
  secretsKeyFeedback: string | null;
  secretsKeyConfigured: boolean;
  secretsKeySource: string;
  secretsKeyUiConfigurable: boolean;
  formError: string | null;
  setSyncFeedback: (value: string | null) => void;
  createInboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteInboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  createOutboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  connectOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  deleteOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  configureSecretsKeyMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTunnelTab({
  inboundQuery,
  outboundQuery,
  tunnelsQuery,
  inboundName,
  setInboundName,
  inboundPathPrefix,
  setInboundPathPrefix,
  issuedRegistrationCode,
  outboundForm,
  setOutboundForm,
  secretsKeyInput,
  setSecretsKeyInput,
  secretsKeyError,
  secretsKeyFeedback,
  secretsKeyConfigured,
  secretsKeySource,
  secretsKeyUiConfigurable,
  formError,
  setSyncFeedback,
  createInboundMutation,
  deleteInboundMutation,
  createOutboundMutation,
  connectOutboundMutation,
  deleteOutboundMutation,
  configureSecretsKeyMutation,
}: FederationTunnelTabProps) {
  const { t } = useTranslation(["federation", "common"]);

  return (
    <>
      {formError && <div className="op-alert op-alert-error">{formError}</div>}

      <section className="panel-card driver-config-form">
        <h4>{t("tunnel.inboundTitle")}</h4>
        <p className="op-muted">{t("tunnel.inboundHint")}</p>
        <div className="form-grid">
          <label>
            {t("tunnel.field.name")} *
            <input value={inboundName} onChange={(e) => setInboundName(e.target.value)} />
          </label>
          <label>
            {t("tunnel.field.prefix")}
            <input value={inboundPathPrefix} onChange={(e) => setInboundPathPrefix(e.target.value)} />
          </label>
        </div>
        <div className="form-actions">
          <button
            type="button"
            className="btn primary"
            disabled={createInboundMutation.isPending}
            onClick={() => createInboundMutation.mutate()}
          >
            {t("tunnel.createRegistrationCode")}
          </button>
        </div>
        {issuedRegistrationCode && (
          <>
            <p className="hint success">{t("tunnel.registrationCodeHint")}</p>
            <pre className="mono federation-code-block">{issuedRegistrationCode}</pre>
            <button
              type="button"
              className="btn compact"
              onClick={() =>
                copyToClipboard(issuedRegistrationCode).then(() =>
                  setSyncFeedback(t("tunnel.codeCopied"))
                )
              }
            >
              {t("tunnel.copyCode")}
            </button>
          </>
        )}
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>{t("tunnel.column.name")}</th>
              <th>{t("tunnel.column.prefix")}</th>
              <th>{t("tunnel.column.expires")}</th>
              <th>{t("tunnel.column.status")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(inboundQuery.data ?? []).map((reg) => (
              <tr key={reg.id}>
                <td><code>{reg.name}</code></td>
                <td><code>{reg.pathPrefix}</code></td>
                <td>{new Date(reg.expiresAt).toLocaleString()}</td>
                <td>{reg.consumedAt ? t("tunnel.registrationUsed") : t("tunnel.registrationPending")}</td>
                <td>
                  <button
                    type="button"
                    className="btn danger compact"
                    disabled={deleteInboundMutation.isPending}
                    onClick={() => deleteInboundMutation.mutate(reg.id)}
                  >
                    {t("common:action.delete")}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="panel-card driver-config-form">
        <h4>{t("tunnel.outboundTitle")}</h4>
        <p className="op-muted">{t("tunnel.outboundHint")}</p>

        <section className="panel-card federation-secrets-key">
          <h5>{t("tunnel.secretsKeyTitle")}</h5>
          {secretsKeyConfigured ? (
            <div className="op-alert op-alert-success">
              {t("tunnel.secretsKeyConfigured")}
              {secretsKeySource === "YAML" && t("tunnel.secretsKeyYaml")}
              {secretsKeySource === "DATABASE" && t("tunnel.secretsKeyDatabase")}
              .
            </div>
          ) : (
            <div className="op-alert op-alert-error">
              {t("tunnel.secretsKeyMissing")}
            </div>
          )}
          {secretsKeyFeedback && <div className="op-alert op-alert-success">{secretsKeyFeedback}</div>}
          {secretsKeyError && <div className="op-alert op-alert-error">{secretsKeyError}</div>}
          {secretsKeyUiConfigurable && !secretsKeyConfigured && (
            <>
              <div className="form-grid">
                <label>
                  {t("tunnel.secretsKeyField")} *
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder={t("tunnel.secretsKeyPlaceholder")}
                    value={secretsKeyInput}
                    onChange={(e) => setSecretsKeyInput(e.target.value)}
                  />
                </label>
              </div>
              <p className="hint">{t("tunnel.secretsKeySaveHint")}</p>
              <div className="form-actions">
                <button
                  type="button"
                  className="btn primary"
                  disabled={configureSecretsKeyMutation.isPending}
                  onClick={() => configureSecretsKeyMutation.mutate()}
                >
                  {t("tunnel.secretsKeySave")}
                </button>
              </div>
            </>
          )}
          {secretsKeyUiConfigurable && secretsKeyConfigured && secretsKeySource === "DATABASE" && (
            <p className="hint">{t("tunnel.secretsKeyChangeHint")}</p>
          )}
        </section>

        <div className="form-grid">
          <label>
            {t("tunnel.field.name")} *
            <input
              value={outboundForm.name}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            {t("tunnel.field.hubUrl")} *
            <input
              value={outboundForm.hubBaseUrl}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, hubBaseUrl: e.target.value }))}
            />
          </label>
          <label>
            {t("tunnel.field.registrationCode")} *
            <input
              type="password"
              value={outboundForm.registrationCode}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, registrationCode: e.target.value }))}
            />
          </label>
          <label>
            {t("tunnel.field.prefix")}
            <input
              value={outboundForm.pathPrefix}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
            />
          </label>
        </div>
        <div className="form-actions">
          <button
            type="button"
            className="btn primary"
            disabled={createOutboundMutation.isPending || !secretsKeyConfigured}
            onClick={() => createOutboundMutation.mutate()}
          >
            {t("tunnel.addAgent")}
          </button>
        </div>
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>{t("tunnel.column.name")}</th>
              <th>{t("tunnel.column.hub")}</th>
              <th>{t("tunnel.column.status")}</th>
              <th>{t("tunnel.column.peer")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(outboundQuery.data ?? []).map((agent) => (
              <tr key={agent.id}>
                <td><code>{agent.name}</code></td>
                <td>{agent.hubBaseUrl}</td>
                <td>
                  {agent.tunnelStatus}
                  {agent.lastError && <span className="op-muted"> — {agent.lastError}</span>}
                </td>
                <td>{agent.linkedPeerId ?? t("common:empty.dash")}</td>
                <td>
                  <div className="federation-peer-actions">
                    <button
                      type="button"
                      className="btn compact"
                      disabled={connectOutboundMutation.isPending}
                      onClick={() => connectOutboundMutation.mutate(agent.id)}
                    >
                      {t("tunnel.connect")}
                    </button>
                    <button
                      type="button"
                      className="btn danger compact"
                      disabled={deleteOutboundMutation.isPending}
                      onClick={() => deleteOutboundMutation.mutate(agent.id)}
                    >
                      {t("common:action.delete")}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(tunnelsQuery.data ?? []).length > 0 && (
          <p className="hint">
            {t("tunnel.activeSessions", { count: (tunnelsQuery.data ?? []).length })}
          </p>
        )}
      </section>
    </>
  );
}
