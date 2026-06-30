import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

export type FederationAuthMode = "STATIC_TOKEN" | "SERVICE_ACCOUNT";
export type FederationAuthStatus = "OK" | "EXPIRING" | "FAILED";
export type FederationConnectionMode = "HTTP_PULL" | "TUNNEL_INBOUND";

export interface FederationPeer {
  id: string;
  name: string;
  baseUrl: string;
  pathPrefix: string;
  enabled: boolean;
  description: string | null;
  hasAuthToken: boolean;
  connectionMode?: FederationConnectionMode;
  authMode?: FederationAuthMode;
  authStatus?: FederationAuthStatus;
  tokenExpiresAt?: string | null;
  tunnelConnected?: boolean;
}

export interface FederationPeerPayload {
  name: string;
  baseUrl: string;
  authToken?: string;
  pathPrefix?: string;
  enabled?: boolean;
  description?: string;
  authMode?: FederationAuthMode;
  authUsername?: string;
  authPassword?: string;
}

export interface FederationPeerAuthStatus {
  peerId: string;
  authMode: FederationAuthMode;
  authStatus: FederationAuthStatus;
  tokenExpiresAt: string | null;
  lastAuthAt: string | null;
  lastAuthError: string | null;
  serviceAccountConfigured: boolean;
}

export interface InboundRegistration {
  id: string;
  name: string;
  pathPrefix: string;
  expiresAt: string;
  consumedAt: string | null;
}

export interface CreatedInboundRegistration {
  registration: InboundRegistration;
  registrationCode: string;
}

export interface TunnelSession {
  sessionId: string;
  peerId: string;
  registrationId: string | null;
  connectedAt: string;
  lastPingAt: string | null;
}

export interface OutboundAgent {
  id: string;
  name: string;
  hubBaseUrl: string;
  pathPrefix: string;
  enabled: boolean;
  tunnelStatus: string;
  linkedPeerId: string | null;
  lastError: string | null;
  lastConnectedAt: string | null;
}

export interface CreateOutboundAgentPayload {
  name: string;
  hubBaseUrl: string;
  registrationCode: string;
  pathPrefix?: string;
}

export interface FederationSecretsKeyStatus {
  configured: boolean;
  source: "NONE" | "YAML" | "DATABASE";
  uiConfigurable: boolean;
}

export function fetchFederationSecretsKeyStatus(): Promise<FederationSecretsKeyStatus> {
  return fetch("/api/v1/federation/secrets-key/status", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Secrets key status failed: ${response.status}`));
    }
    return response.json();
  });
}

export function configureFederationSecretsKey(secretsKey: string): Promise<FederationSecretsKeyStatus> {
  return fetch("/api/v1/federation/secrets-key", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ secretsKey }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Configure secrets key failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchFederationPeers(): Promise<FederationPeer[]> {
  return fetch("/api/v1/federation/peers", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Federation peers failed: ${response.status}`));
    }
    return response.json();
  });
}

export function createFederationPeer(payload: FederationPeerPayload): Promise<FederationPeer> {
  return fetch("/api/v1/federation/peers", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Create peer failed: ${response.status}`));
    }
    return response.json();
  });
}

export function deleteFederationPeer(id: string): Promise<void> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Delete peer failed: ${response.status}`));
    }
  });
}

export function fetchPeerAuthStatus(peerId: string): Promise<FederationPeerAuthStatus> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(peerId)}/auth-status`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Auth status failed: ${response.status}`));
    }
    return response.json();
  });
}

export function refreshPeerToken(peerId: string): Promise<FederationPeerAuthStatus> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(peerId)}/refresh-token`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Refresh token failed: ${response.status}`));
    }
    return response.json();
  });
}

export type CatalogSyncConflictType = "LOCAL_NATIVE" | "PROXY_MISMATCH";
export type CatalogSyncResolutionAction = "SKIP" | "BIND";

export interface CatalogSyncConflict {
  localPath: string;
  remotePath: string;
  type: CatalogSyncConflictType;
  existingPeerId: string | null;
  existingRemotePath: string | null;
  localDisplayName: string;
  localType: string;
}

export interface CatalogSyncPreview {
  localRoot: string;
  remoteCount: number;
  createCount: number;
  updateCount: number;
  conflicts: CatalogSyncConflict[];
}

export interface CatalogSyncResolution {
  localPath: string;
  action: CatalogSyncResolutionAction;
}

export function previewFederationCatalogSync(peerId: string): Promise<CatalogSyncPreview> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(peerId)}/catalog-sync-preview`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Catalog preview failed: ${response.status}`));
    }
    return response.json();
  });
}

export function syncFederationCatalog(
  peerId: string,
  resolutions?: CatalogSyncResolution[]
): Promise<{
  localRoot: string;
  created: number;
  updated: number;
  remoteCount: number;
  skipped: number;
}> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(peerId)}/sync-catalog`, {
    method: "POST",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(resolutions?.length ? { resolutions } : {}),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Sync catalog failed: ${response.status}`));
    }
    return response.json();
  });
}

export function probeFederationObject(peerId: string, path: string): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({ peerId, path });
  return fetch(`/api/v1/federation/proxy/objects/by-path?${params.toString()}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Proxy failed: ${response.status}`));
    }
    return response.json();
  });
}

export interface RemoteFederationTokenPayload {
  baseUrl: string;
  username: string;
  password: string;
}

export interface FederationTokenResponse {
  token: string;
  expiresAt?: string;
  username?: string;
  roles?: string[];
}

export function fetchRemoteFederationToken(payload: RemoteFederationTokenPayload): Promise<FederationTokenResponse> {
  return fetch("/api/v1/federation/remote-token", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      const message = parseApiError(await response.text(), `Remote token failed: ${response.status}`);
      if (response.status === 403) {
        throw new Error(
          `${message}. Endpoint не найден или доступ запрещён — перезапустите ispf-server с последней сборкой.`
        );
      }
      throw new Error(message);
    }
    return response.json();
  });
}

export function fetchInboundRegistrations(): Promise<InboundRegistration[]> {
  return fetch("/api/v1/federation/inbound/registrations", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Inbound registrations failed: ${response.status}`));
    }
    return response.json();
  });
}

export function createInboundRegistration(payload: {
  name: string;
  pathPrefix?: string;
  ttlHours?: number;
}): Promise<CreatedInboundRegistration> {
  return fetch("/api/v1/federation/inbound/registrations", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Create registration failed: ${response.status}`));
    }
    return response.json();
  });
}

export function deleteInboundRegistration(id: string): Promise<void> {
  return fetch(`/api/v1/federation/inbound/registrations/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Delete registration failed: ${response.status}`));
    }
  });
}

export function fetchTunnelSessions(): Promise<TunnelSession[]> {
  return fetch("/api/v1/federation/tunnels", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Tunnel sessions failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchOutboundAgents(): Promise<OutboundAgent[]> {
  return fetch("/api/v1/federation/outbound/agents", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Outbound agents failed: ${response.status}`));
    }
    return response.json();
  });
}

export function createOutboundAgent(payload: CreateOutboundAgentPayload): Promise<OutboundAgent> {
  return fetch("/api/v1/federation/outbound/agents", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Create outbound agent failed: ${response.status}`));
    }
    return response.json();
  });
}

export function deleteOutboundAgent(id: string): Promise<void> {
  return fetch(`/api/v1/federation/outbound/agents/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Delete outbound agent failed: ${response.status}`));
    }
  });
}

export function connectOutboundAgent(id: string): Promise<OutboundAgent> {
  return fetch(`/api/v1/federation/outbound/agents/${encodeURIComponent(id)}/connect`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Connect outbound agent failed: ${response.status}`));
    }
    return response.json();
  });
}

export interface FederationBind {
  localPath: string;
  peerId: string | null;
  peerName: string | null;
  remotePath: string | null;
  type: string;
  displayName: string;
  bound: boolean;
}

export interface FederationBindProbeResult {
  remotePath: string;
  type: string;
  displayName: string;
  description: string;
}

export function fetchFederationBinds(excludeCatalogMirror = true): Promise<FederationBind[]> {
  const params = new URLSearchParams({ excludeCatalogMirror: String(excludeCatalogMirror) });
  return fetch(`/api/v1/federation/binds?${params.toString()}`, { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `List binds failed: ${response.status}`));
    }
    return response.json();
  });
}

export function createFederationBind(payload: {
  localPath?: string;
  parentPath?: string;
  name?: string;
  peerId: string;
  remotePath: string;
  displayName?: string;
  description?: string;
}): Promise<FederationBind> {
  return fetch("/api/v1/federation/binds", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Create bind failed: ${response.status}`));
    }
    return response.json();
  });
}

export function rebindFederationObject(payload: {
  localPath: string;
  peerId: string;
  remotePath: string;
}): Promise<FederationBind> {
  return fetch("/api/v1/federation/binds", {
    method: "PATCH",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Rebind failed: ${response.status}`));
    }
    return response.json();
  });
}

export function unbindFederationObject(localPath: string): Promise<void> {
  const params = new URLSearchParams({ localPath });
  return fetch(`/api/v1/federation/binds?${params.toString()}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Unbind failed: ${response.status}`));
    }
  });
}

export function probeFederationBind(peerId: string, remotePath: string): Promise<FederationBindProbeResult> {
  return fetch("/api/v1/federation/binds/probe", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ peerId, remotePath }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Probe failed: ${response.status}`));
    }
    return response.json();
  });
}

export function proxyFederationVariablePatch(
  peerId: string,
  objectPath: string,
  variableName: string,
  payload: Record<string, unknown>
): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({
    peerId,
    path: objectPath,
    name: variableName,
  });
  return fetch(`/api/v1/federation/proxy/objects/by-path/variables/value?${params.toString()}`, {
    method: "PATCH",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Proxy variable patch failed: ${response.status}`));
    }
    return response.json();
  });
}

export function proxyFederationFunctionInvoke(
  peerId: string,
  objectPath: string,
  functionName: string,
  input?: Record<string, unknown>
): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({
    peerId,
    path: objectPath,
    name: functionName,
  });
  return fetch(`/api/v1/federation/proxy/objects/by-path/functions/invoke?${params.toString()}`, {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(input ?? {}),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Proxy function invoke failed: ${response.status}`));
    }
    return response.json();
  });
}

export function formatTokenExpiry(expiresAt: string | null | undefined): string {
  if (!expiresAt) {
    return "—";
  }
  const expiry = new Date(expiresAt);
  const hoursLeft = Math.round((expiry.getTime() - Date.now()) / (1000 * 60 * 60));
  if (hoursLeft <= 0) {
    return "истёк";
  }
  if (hoursLeft < 24) {
    return `истекает через ${hoursLeft} ч`;
  }
  const daysLeft = Math.round(hoursLeft / 24);
  return `истекает через ${daysLeft} д`;
}
