import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { SecurityUserSummary } from "../../api/securityUsers";
import { copyToClipboard } from "./federationShared";

interface FederationTokensTabProps {
  usersQuery: UseQueryResult<SecurityUserSummary[], Error>;
  tokenUser: string;
  setTokenUser: (value: string) => void;
  tokenTtlHours: string;
  setTokenTtlHours: (value: string) => void;
  issuedToken: string | null;
  issuedTokenMeta: string | null;
  tokenCopyFeedback: string | null;
  setTokenCopyFeedback: (value: string | null) => void;
  tokenPanelError: string | null;
  tokenApiMissing: boolean;
  issueTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTokensTab({
  usersQuery,
  tokenUser,
  setTokenUser,
  tokenTtlHours,
  setTokenTtlHours,
  issuedToken,
  issuedTokenMeta,
  tokenCopyFeedback,
  setTokenCopyFeedback,
  tokenPanelError,
  tokenApiMissing,
  issueTokenMutation,
}: FederationTokensTabProps) {
  const { t } = useTranslation(["federation", "common"]);
  const userOptions = usersQuery.data ?? [];

  return (
    <div className="panel-card driver-config-form">
      <h4>{t("tokens.title")}</h4>
      <p className="op-muted">{t("tokens.subtitle")}</p>
      <div className="form-grid">
        <label>
          {t("tokens.field.user")}
          <select
            value={tokenUser}
            onChange={(e) => setTokenUser(e.target.value)}
            disabled={userOptions.length === 0}
          >
            {userOptions.length === 0 && <option value={tokenUser}>{tokenUser}</option>}
            {userOptions.map((user) => (
              <option key={user.username} value={user.username}>
                {user.username} ({user.roles.join(", ")})
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("tokens.field.ttlHours")}
          <input
            type="number"
            min={1}
            max={168}
            value={tokenTtlHours}
            onChange={(e) => setTokenTtlHours(e.target.value)}
          />
        </label>
      </div>
      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={issueTokenMutation.isPending || tokenApiMissing}
          onClick={() => issueTokenMutation.mutate()}
        >
          {issueTokenMutation.isPending ? t("tokens.issuing") : t("tokens.issue")}
        </button>
        {issuedToken && (
          <button
            type="button"
            className="btn compact"
            onClick={() => {
              copyToClipboard(issuedToken)
                .then(() => setTokenCopyFeedback(t("tokens.copied")))
                .catch(() => setTokenCopyFeedback(t("tokens.copyFailed")));
            }}
          >
            {t("tokens.copy")}
          </button>
        )}
      </div>
      {issuedTokenMeta && <p className="hint">{issuedTokenMeta}</p>}
      {tokenCopyFeedback && <p className="hint success">{tokenCopyFeedback}</p>}
      {tokenPanelError && <p className="hint error">{tokenPanelError}</p>}
      {issuedToken && (
        <pre className="mono federation-code-block">{issuedToken}</pre>
      )}
    </div>
  );
}
