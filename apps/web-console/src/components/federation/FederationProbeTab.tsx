import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { FederationPeer } from "../../api/federation";

interface FederationProbeTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  probePeerId: string;
  setProbePeerId: (value: string) => void;
  probePath: string;
  setProbePath: (value: string) => void;
  probeResult: string | null;
  probeMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationProbeTab({
  peersQuery,
  probePeerId,
  setProbePeerId,
  probePath,
  setProbePath,
  probeResult,
  probeMutation,
}: FederationProbeTabProps) {
  const { t } = useTranslation("federation");

  return (
    <div className="panel-card federation-probe">
      <h4>{t("probe.title")}</h4>
      <p className="op-muted">{t("probe.subtitle")}</p>
      <div className="form-grid">
        <label>
          {t("probe.field.peer")}
          <select value={probePeerId} onChange={(e) => setProbePeerId(e.target.value)}>
            <option value="">{t("bind.selectPeer")}</option>
            {(peersQuery.data ?? []).map((peer) => (
              <option key={peer.id} value={peer.id}>{peer.name}</option>
            ))}
          </select>
        </label>
        <label>
          {t("probe.field.path")}
          <input value={probePath} onChange={(e) => setProbePath(e.target.value)} />
        </label>
      </div>
      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={probeMutation.isPending || !probePeerId}
          onClick={() => probeMutation.mutate()}
        >
          {probeMutation.isPending ? t("probe.running") : t("probe.run")}
        </button>
      </div>
      {probeResult && (
        <pre className="mono federation-probe-result">{probeResult}</pre>
      )}
    </div>
  );
}
