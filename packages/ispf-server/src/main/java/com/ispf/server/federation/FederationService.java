package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FederationService {

    private final FederationPeerStore peerStore;
    private final ObjectMapper objectMapper;
    private final FederationWebSocketFanoutService webSocketFanout;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FederationService(
            FederationPeerStore peerStore,
            ObjectMapper objectMapper,
            FederationWebSocketFanoutService webSocketFanout
    ) {
        this.peerStore = peerStore;
        this.objectMapper = objectMapper;
        this.webSocketFanout = webSocketFanout;
    }

    public List<FederationPeer> listPeers() {
        return peerStore.listAll();
    }

    public FederationPeer createPeer(FederationPeerDraft draft) {
        validateDraft(draft);
        return peerStore.insert(draft);
    }

    public FederationPeer updatePeer(UUID id, FederationPeerDraft draft) {
        validateDraft(draft);
        requirePeer(id);
        return peerStore.update(id, draft);
    }

    public void deletePeer(UUID id) {
        requirePeer(id);
        peerStore.delete(id);
    }

    public Map<String, Object> fetchRemoteLoginToken(String baseUrl, String username, String password) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl is required");
        }
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
        String url = baseUrl.trim().replaceAll("/+$", "") + "/api/v1/auth/login";
        try {
            String json = objectMapper.writeValueAsString(Map.of("username", username.trim(), "password", password));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Remote login failed: HTTP " + response.statusCode()
                );
            }
            JsonNode node = objectMapper.readTree(response.body());
            String token = node.path("token").asText(null);
            if (token == null || token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote login did not return a token");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            if (node.hasNonNull("expiresAt")) {
                result.put("expiresAt", node.get("expiresAt").asText());
            }
            if (node.hasNonNull("username")) {
                result.put("username", node.get("username").asText());
            }
            if (node.has("roles")) {
                result.put("roles", objectMapper.convertValue(node.get("roles"), List.class));
            }
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote login failed: " + ex.getMessage(), ex);
        }
    }

    public JsonNode proxyObjectByPath(UUID peerId, String objectPath) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        return sendGet(peer, "/api/v1/objects/by-path?path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8));
    }

    public JsonNode proxyObjectList(UUID peerId) {
        FederationPeer peer = requirePeer(peerId);
        return sendGet(peer, "/api/v1/objects");
    }

    public JsonNode proxyVariablesByPath(UUID peerId, String objectPath) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        return sendGet(
                peer,
                "/api/v1/objects/by-path/variables?path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8)
        );
    }

    public JsonNode proxyDashboardByPath(UUID peerId, String objectPath) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        return sendGet(
                peer,
                "/api/v1/dashboards/by-path?path=" + URLEncoder.encode(remotePath, StandardCharsets.UTF_8)
        );
    }

    public JsonNode proxyVariableHistory(
            UUID peerId,
            String objectPath,
            String name,
            String field,
            java.time.Instant from,
            java.time.Instant to,
            int limit
    ) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        StringBuilder query = new StringBuilder("/api/v1/objects/by-path/variables/history?path=")
                .append(URLEncoder.encode(remotePath, StandardCharsets.UTF_8))
                .append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append("&field=").append(URLEncoder.encode(field, StandardCharsets.UTF_8))
                .append("&limit=").append(limit);
        if (from != null) {
            query.append("&from=").append(URLEncoder.encode(from.toString(), StandardCharsets.UTF_8));
        }
        if (to != null) {
            query.append("&to=").append(URLEncoder.encode(to.toString(), StandardCharsets.UTF_8));
        }
        return sendGet(peer, query.toString());
    }

    public JsonNode proxyVariableHistoryAggregate(
            UUID peerId,
            String objectPath,
            String name,
            String field,
            String bucket,
            java.time.Instant from,
            java.time.Instant to,
            int limit
    ) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        StringBuilder query = new StringBuilder("/api/v1/objects/by-path/variables/history/aggregate?path=")
                .append(URLEncoder.encode(remotePath, StandardCharsets.UTF_8))
                .append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append("&field=").append(URLEncoder.encode(field, StandardCharsets.UTF_8))
                .append("&bucket=").append(URLEncoder.encode(bucket, StandardCharsets.UTF_8))
                .append("&limit=").append(limit);
        if (from != null) {
            query.append("&from=").append(URLEncoder.encode(from.toString(), StandardCharsets.UTF_8));
        }
        if (to != null) {
            query.append("&to=").append(URLEncoder.encode(to.toString(), StandardCharsets.UTF_8));
        }
        return sendGet(peer, query.toString());
    }

    public JsonNode proxyVariablePatch(UUID peerId, String objectPath, String variableName, String bodyJson) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        String path = "/api/v1/objects/by-path/variables/value?path="
                + URLEncoder.encode(remotePath, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(variableName, StandardCharsets.UTF_8);
        JsonNode result = sendJson(peer, "PATCH", path, bodyJson);
        webSocketFanout.notifyFederatedPathUpdated(objectPath, variableName);
        return result;
    }

    public JsonNode proxyFunctionInvoke(UUID peerId, String objectPath, String functionName, String bodyJson) {
        FederationPeer peer = requirePeer(peerId);
        String remotePath = resolveRemotePath(peer.pathPrefix(), objectPath);
        String path = "/api/v1/objects/by-path/functions/invoke?path="
                + URLEncoder.encode(remotePath, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(functionName, StandardCharsets.UTF_8);
        JsonNode result = sendJson(peer, "POST", path, bodyJson);
        webSocketFanout.notifyFederatedPathUpdated(objectPath, null);
        return result;
    }

    private JsonNode sendJson(FederationPeer peer, String method, String pathAndQuery, String body) {
        if (!peer.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Peer is disabled: " + peer.name());
        }
        String url = peer.baseUrl() + pathAndQuery;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            applyAuthorization(builder, peer);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Peer " + peer.name() + " returned HTTP " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Peer request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode sendGet(FederationPeer peer, String pathAndQuery) {
        if (!peer.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Peer is disabled: " + peer.name());
        }
        String url = peer.baseUrl() + pathAndQuery;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            applyAuthorization(builder, peer);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Remote resource not found: " + pathAndQuery);
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Peer " + peer.name() + " returned HTTP " + response.statusCode()
                                + ". Configure authToken on the peer or ensure the caller is authorized on the remote instance."
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Peer " + peer.name() + " returned HTTP " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Peer request failed: " + e.getMessage(), e);
        }
    }

    private void applyAuthorization(HttpRequest.Builder builder, FederationPeer peer) {
        if (peer.authToken() != null && !peer.authToken().isBlank()) {
            builder.header("Authorization", "Bearer " + peer.authToken().trim());
            return;
        }
        resolveInboundBearerToken().ifPresent(token -> builder.header("Authorization", "Bearer " + token));
    }

    static Optional<String> resolveInboundBearerToken() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        String header = servletAttributes.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    static String resolveRemotePath(String pathPrefix, String objectPath) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return objectPath;
        }
        String prefix = pathPrefix.trim();
        while (prefix.endsWith(".")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (objectPath.equals(prefix) || objectPath.startsWith(prefix + ".")) {
            return objectPath;
        }
        return prefix + "." + objectPath;
    }

    private FederationPeer requirePeer(UUID id) {
        return peerStore.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer not found: " + id));
    }

    private void validateDraft(FederationPeerDraft draft) {
        if (draft.name() == null || draft.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (draft.baseUrl() == null || draft.baseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl is required");
        }
    }
}
