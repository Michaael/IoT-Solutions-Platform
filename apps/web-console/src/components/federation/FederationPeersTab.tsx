import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { FederationPeer, FederationPeerPayload } from "../../api/federation";
import { formatTokenExpiry } from "../../api/federation";
import { getStoredSession } from "../../auth/session";

interface FederationPeersTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  form: FederationPeerPayload;
  setForm: React.Dispatch<React.SetStateAction<FederationPeerPayload>>;
  useServiceAccount: boolean;
  setUseServiceAccount: (value: boolean) => void;
  serviceAccountUsername: string;
  setServiceAccountUsername: (value: string) => void;
  serviceAccountPassword: string;
  setServiceAccountPassword: (value: string) => void;
  remoteLoginUsername: string;
  setRemoteLoginUsername: (value: string) => void;
  remoteLoginPassword: string;
  setRemoteLoginPassword: (value: string) => void;
  formError: string | null;
  tokenApiMissing: boolean;
  createMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteMutation: UseMutationResult<unknown, Error, string, unknown>;
  onSyncCatalog: (peer: FederationPeer) => void;
  refreshTokenMutation: UseMutationResult<unknown, Error, string, unknown>;
  remoteTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
  setFormError: (value: string | null) => void;
  setSyncFeedback: (value: string | null) => void;
}

export default function FederationPeersTab({
  peersQuery,
  form,
  setForm,
  useServiceAccount,
  setUseServiceAccount,
  serviceAccountUsername,
  setServiceAccountUsername,
  serviceAccountPassword,
  setServiceAccountPassword,
  remoteLoginUsername,
  setRemoteLoginUsername,
  remoteLoginPassword,
  setRemoteLoginPassword,
  formError,
  tokenApiMissing,
  createMutation,
  deleteMutation,
  onSyncCatalog,
  refreshTokenMutation,
  remoteTokenMutation,
  setFormError,
  setSyncFeedback,
}: FederationPeersTabProps) {
  const { t } = useTranslation(["federation", "common"]);
  const peers = peersQuery.data ?? [];

  const authBadge = (peer: FederationPeer): React.ReactNode => {
    if (peer.authMode === "SERVICE_ACCOUNT") {
      return (
        <>
          <span className="badge">{t("peers.authAuto")}</span>
          {" "}
          <span className="op-muted">{formatTokenExpiry(peer.tokenExpiresAt)}</span>
          {peer.authStatus === "FAILED" ? (
            <span className="badge danger" title={t("peers.authError")}>{t("peers.authError")}</span>
          ) : (
            <span className="badge ok">OK</span>
          )}
        </>
      );
    }
    return peer.hasAuthToken ? (
      <span className="badge ok">OK</span>
    ) : (
      <span className="badge">{t("peers.noToken")}</span>
    );
  };

  return (
    <>
      {formError && <div className="op-alert op-alert-error">{formError}</div>}

      <div className="panel-card">
        <h4>{t("peers.registered")}</h4>
        {peers.length === 0 ? (
          <p className="federation-empty-state">{t("peers.empty")}</p>
        ) : (
          <table className="op-table security-users-table security-users-table-compact">
            <thead>
              <tr>
                <th>{t("peers.column.name")}</th>
                <th>{t("peers.column.url")}</th>
                <th>{t("peers.column.prefix")}</th>
                <th>{t("peers.column.auth")}</th>
                <th>{t("peers.column.token")}</th>
                <th>{t("peers.column.enabled")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {peers.map((peer) => (
                <tr key={peer.id}>
                  <td>
                    <code>{peer.name}</code>
                    {peer.connectionMode === "TUNNEL_INBOUND" && (
                      <span className="badge">
                        {t("peers.tunnelBadge")}{peer.tunnelConnected ? "" : t("peers.tunnelOffline")}
                      </span>
                    )}
                  </td>
                  <td>{peer.baseUrl}</td>
                  <td><code>{peer.pathPrefix || t("common:empty.dash")}</code></td>
                  <td>{authBadge(peer)}</td>
                  <td>{peer.hasAuthToken ? t("common:action.yes") : t("common:action.no")}</td>
                  <td>{peer.enabled ? t("common:action.yes") : t("common:action.no")}</td>
                  <td>
                    <div className="federation-peer-actions">
                      {peer.authMode === "SERVICE_ACCOUNT" && (
                        <button
                          type="button"
                          className="btn compact"
                          disabled={refreshTokenMutation.isPending}
                          onClick={() => refreshTokenMutation.mutate(peer.id)}
                        >
                          {t("peers.refreshToken")}
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn compact"
                        onClick={() => onSyncCatalog(peer)}
                      >
                        {t("peers.syncCatalog")}
                      </button>
                      <button
                        type="button"
                        className="btn danger compact"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(peer.id)}
                      >
                        {t("common:action.delete")}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <form
        className="panel-card driver-config-form"
        onSubmit={(e) => {
          e.preventDefault();
          createMutation.mutate();
        }}
      >
        <h4>{t("peers.newPeer")}</h4>
        <p className="op-muted">{t("peers.newPeerHint")}</p>
        <div className="form-grid">
          <label>
            {t("peers.field.name")} *
            <input
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            {t("peers.field.url")} *
            <input
              value={form.baseUrl}
              onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
            />
          </label>
          <label>
            {t("peers.field.prefix")}
            <input
              value={form.pathPrefix ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
            />
          </label>
          <label>
            {t("peers.field.authToken")}
            <input
              type="password"
              value={form.authToken ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, authToken: e.target.value }))}
            />
          </label>
          <label className="full">
            {t("peers.field.description")}
            <input
              value={form.description ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            />
          </label>
          <label className="full">
            <input
              type="checkbox"
              checked={useServiceAccount}
              onChange={(e) => setUseServiceAccount(e.target.checked)}
            />
            {" "}
            {t("peers.autoRefreshToken")}
          </label>
          {useServiceAccount && (
            <>
              <label>
                {t("peers.field.username")}
                <input
                  value={serviceAccountUsername}
                  onChange={(e) => setServiceAccountUsername(e.target.value)}
                />
              </label>
              <label>
                {t("peers.field.password")}
                <input
                  type="password"
                  value={serviceAccountPassword}
                  onChange={(e) => setServiceAccountPassword(e.target.value)}
                />
              </label>
            </>
          )}
        </div>

        <section className="federation-remote-token panel-card">
          <h5>{t("peers.getAuthToken")}</h5>
          <p className="op-muted">{t("peers.getAuthTokenHint")}</p>
          <div className="form-actions">
            <button
              type="button"
              className="btn compact"
              onClick={() => {
                const session = getStoredSession();
                if (!session?.token) {
                  setFormError(t("peers.noSession"));
                  return;
                }
                setForm((prev) => ({ ...prev, authToken: session.token }));
                setFormError(null);
                setSyncFeedback(t("peers.sessionTokenApplied"));
              }}
            >
              {t("peers.useSessionToken")}
            </button>
          </div>
          <div className="form-grid">
            <label>
              {t("peers.remoteUsername")}
              <input
                value={remoteLoginUsername}
                onChange={(e) => setRemoteLoginUsername(e.target.value)}
              />
            </label>
            <label>
              {t("peers.remotePassword")}
              <input
                type="password"
                value={remoteLoginPassword}
                onChange={(e) => setRemoteLoginPassword(e.target.value)}
              />
            </label>
          </div>
          <div className="form-actions">
            <button
              type="button"
              className="btn"
              disabled={remoteTokenMutation.isPending || tokenApiMissing}
              onClick={() => remoteTokenMutation.mutate()}
            >
              {remoteTokenMutation.isPending ? t("peers.gettingToken") : t("peers.getRemoteToken")}
            </button>
          </div>
        </section>

        <div className="form-actions">
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            {createMutation.isPending ? t("peers.adding") : t("peers.add")}
          </button>
        </div>
      </form>
    </>
  );
}
