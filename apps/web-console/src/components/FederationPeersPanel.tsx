import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  connectOutboundAgent,
  configureFederationSecretsKey,
  createFederationPeer,
  createInboundRegistration,
  createOutboundAgent,
  deleteFederationPeer,
  deleteInboundRegistration,
  deleteOutboundAgent,
  fetchFederationPeers,
  fetchInboundRegistrations,
  fetchOutboundAgents,
  fetchFederationSecretsKeyStatus,
  fetchRemoteFederationToken,
  fetchTunnelSessions,
  probeFederationObject,
  refreshPeerToken,
  type FederationPeerPayload,
} from "../api/federation";
import { fetchSecurityUsers, issueFederationToken } from "../api/securityUsers";
import { fetchPlatformInfo } from "../api";
import FederationPeersTab from "./federation/FederationPeersTab";
import FederationCatalogSyncDialog from "./federation/FederationCatalogSyncDialog";
import FederationProbeTab from "./federation/FederationProbeTab";
import FederationTokensTab from "./federation/FederationTokensTab";
import FederationTunnelTab from "./federation/FederationTunnelTab";
import {
  defaultFederationBaseUrl,
  FEDERATION_TAB_KEYS,
  type FederationTab,
} from "./federation/federationShared";

interface FederationPeersPanelProps {
  canManage: boolean;
}

export default function FederationPeersPanel({ canManage }: FederationPeersPanelProps) {
  const { t } = useTranslation("federation");
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<FederationTab>("peers");
  const [form, setForm] = useState<FederationPeerPayload>({
    name: "",
    baseUrl: defaultFederationBaseUrl(),
    pathPrefix: "root.platform",
    enabled: true,
    description: "",
  });
  const [probePath, setProbePath] = useState("devices.demo-sensor-01");
  const [probePeerId, setProbePeerId] = useState<string>("");
  const [probeResult, setProbeResult] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [tokenPanelError, setTokenPanelError] = useState<string | null>(null);
  const [syncFeedback, setSyncFeedback] = useState<string | null>(null);
  const [catalogSyncPeer, setCatalogSyncPeer] = useState<{ id: string; name: string } | null>(null);

  const [tokenUser, setTokenUser] = useState("admin");
  const [tokenTtlHours, setTokenTtlHours] = useState("12");
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [issuedTokenMeta, setIssuedTokenMeta] = useState<string | null>(null);
  const [tokenCopyFeedback, setTokenCopyFeedback] = useState<string | null>(null);

  const [remoteLoginUsername, setRemoteLoginUsername] = useState("admin");
  const [remoteLoginPassword, setRemoteLoginPassword] = useState("");
  const [useServiceAccount, setUseServiceAccount] = useState(false);
  const [serviceAccountUsername, setServiceAccountUsername] = useState("admin");
  const [serviceAccountPassword, setServiceAccountPassword] = useState("");

  const [inboundName, setInboundName] = useState("");
  const [inboundPathPrefix, setInboundPathPrefix] = useState("root.platform");
  const [issuedRegistrationCode, setIssuedRegistrationCode] = useState<string | null>(null);

  const [outboundForm, setOutboundForm] = useState({
    name: "",
    hubBaseUrl: defaultFederationBaseUrl(),
    registrationCode: "",
    pathPrefix: "root.platform",
  });
  const [secretsKeyInput, setSecretsKeyInput] = useState("");
  const [secretsKeyError, setSecretsKeyError] = useState<string | null>(null);
  const [secretsKeyFeedback, setSecretsKeyFeedback] = useState<string | null>(null);

  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    enabled: canManage,
  });

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
    enabled: canManage,
  });

  const platformInfoQuery = useQuery({
    queryKey: ["platform-info"],
    queryFn: fetchPlatformInfo,
    enabled: canManage,
  });

  const inboundQuery = useQuery({
    queryKey: ["federation-inbound-registrations"],
    queryFn: fetchInboundRegistrations,
    enabled: canManage,
  });

  const tokenApiSupported = platformInfoQuery.data
    ? platformInfoQuery.data.capabilities.includes("federation-issue-token") &&
      platformInfoQuery.data.capabilities.includes("federation-remote-token")
    : true;
  const tokenApiMissing = platformInfoQuery.isSuccess && !tokenApiSupported;
  const tunnelApiSupported = platformInfoQuery.data
    ? platformInfoQuery.data.capabilities.includes("federation-tunnel")
    : true;

  const outboundQuery = useQuery({
    queryKey: ["federation-outbound-agents"],
    queryFn: fetchOutboundAgents,
    enabled: canManage,
    refetchInterval: 5000,
  });

  const secretsKeyQuery = useQuery({
    queryKey: ["federation-secrets-key"],
    queryFn: fetchFederationSecretsKeyStatus,
    enabled: canManage && tunnelApiSupported,
  });

  const tunnelsQuery = useQuery({
    queryKey: ["federation-tunnels"],
    queryFn: fetchTunnelSessions,
    enabled: canManage,
    refetchInterval: 5000,
  });

  const createMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      if (!form.name.trim() || !form.baseUrl.trim()) {
        throw new Error(t("peers.errorNameUrl"));
      }
      return createFederationPeer({
        ...form,
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        authMode: useServiceAccount ? "SERVICE_ACCOUNT" : "STATIC_TOKEN",
        authUsername: useServiceAccount ? serviceAccountUsername.trim() : undefined,
        authPassword: useServiceAccount ? serviceAccountPassword : undefined,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setForm((prev) => ({ ...prev, name: "", description: "" }));
      setSyncFeedback(t("peers.peerAdded"));
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteFederationPeer(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-peers"] }),
  });

  const handleCatalogSynced = (message: string) => {
    setSyncFeedback(message);
    setFormError(null);
    queryClient.invalidateQueries({ queryKey: ["objects"] });
    queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
  };

  const probeMutation = useMutation({
    mutationFn: () => {
      if (!probePeerId) {
        throw new Error(t("peers.errorSelectPeer"));
      }
      return probeFederationObject(probePeerId, probePath.trim());
    },
    onSuccess: (data) => setProbeResult(JSON.stringify(data, null, 2)),
    onError: (error: Error) => setProbeResult(error.message),
  });

  const issueTokenMutation = useMutation({
    mutationFn: () => {
      setTokenPanelError(null);
      const ttl = Number.parseInt(tokenTtlHours, 10);
      return issueFederationToken(tokenUser, Number.isFinite(ttl) && ttl > 0 ? ttl : undefined);
    },
    onSuccess: (data) => {
      setIssuedToken(data.token);
      const parts = [
        data.username ? t("tokens.metaUser", { username: data.username }) : null,
        data.expiresAt ? t("tokens.metaExpires", { expiresAt: data.expiresAt }) : null,
        data.roles?.length ? t("tokens.metaRoles", { roles: data.roles.join(", ") }) : null,
      ].filter(Boolean);
      setIssuedTokenMeta(parts.join(" · "));
      setTokenCopyFeedback(null);
      setTokenPanelError(null);
    },
    onError: (error: Error) => {
      setIssuedToken(null);
      setIssuedTokenMeta(null);
      setTokenPanelError(error.message);
    },
  });

  const refreshTokenMutation = useMutation({
    mutationFn: (peerId: string) => refreshPeerToken(peerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setSyncFeedback(t("peers.peerTokenRefreshed"));
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const createInboundMutation = useMutation({
    mutationFn: () => {
      if (!inboundName.trim()) {
        throw new Error(t("peers.errorInboundName"));
      }
      return createInboundRegistration({
        name: inboundName.trim(),
        pathPrefix: inboundPathPrefix.trim() || "root.platform",
      });
    },
    onSuccess: (data) => {
      setIssuedRegistrationCode(data.registrationCode);
      setInboundName("");
      queryClient.invalidateQueries({ queryKey: ["federation-inbound-registrations"] });
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteInboundMutation = useMutation({
    mutationFn: (id: string) => deleteInboundRegistration(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-inbound-registrations"] }),
  });

  const createOutboundMutation = useMutation({
    mutationFn: () => {
      if (!outboundForm.name.trim() || !outboundForm.hubBaseUrl.trim() || !outboundForm.registrationCode.trim()) {
        throw new Error(t("peers.errorOutboundFields"));
      }
      return createOutboundAgent({
        name: outboundForm.name.trim(),
        hubBaseUrl: outboundForm.hubBaseUrl.trim(),
        registrationCode: outboundForm.registrationCode.trim(),
        pathPrefix: outboundForm.pathPrefix.trim() || "root.platform",
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] });
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setOutboundForm((prev) => ({ ...prev, name: "", registrationCode: "" }));
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const connectOutboundMutation = useMutation({
    mutationFn: (id: string) => connectOutboundAgent(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] }),
  });

  const deleteOutboundMutation = useMutation({
    mutationFn: (id: string) => deleteOutboundAgent(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] }),
  });

  const configureSecretsKeyMutation = useMutation({
    mutationFn: () => {
      setSecretsKeyError(null);
      setSecretsKeyFeedback(null);
      if (!secretsKeyInput.trim()) {
        throw new Error(t("peers.errorSecretsKey"));
      }
      if (secretsKeyInput.trim().length < 16) {
        throw new Error(t("peers.errorSecretsKeyLength"));
      }
      return configureFederationSecretsKey(secretsKeyInput.trim());
    },
    onSuccess: (status) => {
      setSecretsKeyInput("");
      setSecretsKeyFeedback(
        status.source === "YAML"
          ? t("tunnel.secretsKeySavedYaml")
          : t("tunnel.secretsKeySavedEdge"),
      );
      queryClient.invalidateQueries({ queryKey: ["federation-secrets-key"] });
      queryClient.invalidateQueries({ queryKey: ["platform-info"] });
    },
    onError: (error: Error) => setSecretsKeyError(error.message),
  });

  const remoteTokenMutation = useMutation({
    mutationFn: () => {
      setTokenPanelError(null);
      if (!form.baseUrl.trim()) {
        throw new Error(t("peers.errorPeerUrl"));
      }
      if (!remoteLoginPassword) {
        throw new Error(t("peers.errorRemotePassword"));
      }
      return fetchRemoteFederationToken({
        baseUrl: form.baseUrl.trim(),
        username: remoteLoginUsername.trim(),
        password: remoteLoginPassword,
      });
    },
    onSuccess: (data) => {
      setForm((prev) => ({ ...prev, authToken: data.token }));
      setRemoteLoginPassword("");
      setFormError(null);
      setTokenPanelError(null);
      setSyncFeedback(
        data.expiresAt
          ? t("peers.remoteTokenReceivedExpiry", {
              username: data.username ?? remoteLoginUsername,
              expiresAt: data.expiresAt,
            })
          : t("peers.remoteTokenReceived", {
              username: data.username ?? remoteLoginUsername,
            }),
      );
    },
    onError: (error: Error) => setTokenPanelError(error.message),
  });

  if (!canManage) {
    return <p className="op-muted">{t("panel.adminOnly")}</p>;
  }

  const secretsKeyConfigured = secretsKeyQuery.data?.configured ?? platformInfoQuery.data?.federationSecretsKeyConfigured ?? false;
  const secretsKeySource = secretsKeyQuery.data?.source ?? platformInfoQuery.data?.federationSecretsKeySource ?? "NONE";
  const secretsKeyUiConfigurable = secretsKeyQuery.data?.uiConfigurable ?? true;

  const tabs: FederationTab[] = tunnelApiSupported
    ? ["peers", "tokens", "tunnel", "probe"]
    : ["peers", "tokens", "probe"];

  return (
    <section className="federation-peers-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("panel.title")}</h3>
          <p className="op-muted">{t("panel.subtitle")}</p>
        </div>
      </header>

      {peersQuery.error && <div className="op-alert op-alert-error">{String(peersQuery.error)}</div>}

      {tokenApiMissing && (
        <div className="op-alert op-alert-error">
          {t("panel.tokenApiMissing")}
        </div>
      )}

      {syncFeedback && <div className="op-alert op-alert-success">{syncFeedback}</div>}

      <nav className="tabs" aria-label={t("panel.tabsAria")}>
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            className={activeTab === tab ? "active" : ""}
            onClick={() => {
              setActiveTab(tab);
              setFormError(null);
              setTokenPanelError(null);
            }}
          >
            {t(FEDERATION_TAB_KEYS[tab])}
          </button>
        ))}
      </nav>

      <div className={`federation-tab-panel ${activeTab === "peers" ? "active" : ""}`}>
        <FederationPeersTab
          peersQuery={peersQuery}
          form={form}
          setForm={setForm}
          useServiceAccount={useServiceAccount}
          setUseServiceAccount={setUseServiceAccount}
          serviceAccountUsername={serviceAccountUsername}
          setServiceAccountUsername={setServiceAccountUsername}
          serviceAccountPassword={serviceAccountPassword}
          setServiceAccountPassword={setServiceAccountPassword}
          remoteLoginUsername={remoteLoginUsername}
          setRemoteLoginUsername={setRemoteLoginUsername}
          remoteLoginPassword={remoteLoginPassword}
          setRemoteLoginPassword={setRemoteLoginPassword}
          formError={formError}
          tokenApiMissing={tokenApiMissing}
          createMutation={createMutation}
          deleteMutation={deleteMutation}
          onSyncCatalog={(peer) => setCatalogSyncPeer({ id: peer.id, name: peer.name })}
          refreshTokenMutation={refreshTokenMutation}
          remoteTokenMutation={remoteTokenMutation}
          setFormError={setFormError}
          setSyncFeedback={setSyncFeedback}
        />
      </div>

      <div className={`federation-tab-panel ${activeTab === "tokens" ? "active" : ""}`}>
        <FederationTokensTab
          usersQuery={usersQuery}
          tokenUser={tokenUser}
          setTokenUser={setTokenUser}
          tokenTtlHours={tokenTtlHours}
          setTokenTtlHours={setTokenTtlHours}
          issuedToken={issuedToken}
          issuedTokenMeta={issuedTokenMeta}
          tokenCopyFeedback={tokenCopyFeedback}
          setTokenCopyFeedback={setTokenCopyFeedback}
          tokenPanelError={tokenPanelError}
          tokenApiMissing={tokenApiMissing}
          issueTokenMutation={issueTokenMutation}
        />
      </div>

      {tunnelApiSupported && (
        <div className={`federation-tab-panel ${activeTab === "tunnel" ? "active" : ""}`}>
          <FederationTunnelTab
            inboundQuery={inboundQuery}
            outboundQuery={outboundQuery}
            tunnelsQuery={tunnelsQuery}
            inboundName={inboundName}
            setInboundName={setInboundName}
            inboundPathPrefix={inboundPathPrefix}
            setInboundPathPrefix={setInboundPathPrefix}
            issuedRegistrationCode={issuedRegistrationCode}
            outboundForm={outboundForm}
            setOutboundForm={setOutboundForm}
            secretsKeyInput={secretsKeyInput}
            setSecretsKeyInput={setSecretsKeyInput}
            secretsKeyError={secretsKeyError}
            secretsKeyFeedback={secretsKeyFeedback}
            secretsKeyConfigured={secretsKeyConfigured}
            secretsKeySource={secretsKeySource}
            secretsKeyUiConfigurable={secretsKeyUiConfigurable}
            formError={formError}
            setSyncFeedback={setSyncFeedback}
            createInboundMutation={createInboundMutation}
            deleteInboundMutation={deleteInboundMutation}
            createOutboundMutation={createOutboundMutation}
            connectOutboundMutation={connectOutboundMutation}
            deleteOutboundMutation={deleteOutboundMutation}
            configureSecretsKeyMutation={configureSecretsKeyMutation}
          />
        </div>
      )}

      <div className={`federation-tab-panel ${activeTab === "probe" ? "active" : ""}`}>
        <FederationProbeTab
          peersQuery={peersQuery}
          probePeerId={probePeerId}
          setProbePeerId={setProbePeerId}
          probePath={probePath}
          setProbePath={setProbePath}
          probeResult={probeResult}
          probeMutation={probeMutation}
        />
      </div>
      {catalogSyncPeer && (
        <FederationCatalogSyncDialog
          peerId={catalogSyncPeer.id}
          peerName={catalogSyncPeer.name}
          onClose={() => setCatalogSyncPeer(null)}
          onSynced={handleCatalogSynced}
          onError={(message) => {
            setSyncFeedback(null);
            setFormError(message);
            setCatalogSyncPeer(null);
          }}
        />
      )}
    </section>
  );
}
