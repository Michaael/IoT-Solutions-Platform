package com.ispf.server.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ObjectWebSocketHandler extends TextWebSocketHandler {

    private static final String SUBSCRIBE_ATTR = "subscribedPaths";

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final FederationSubscribePollService federationSubscribePollService;
    private final ObjectPresenceService presenceService;
    private final ObjectManager objectManager;

    public ObjectWebSocketHandler(
            ObjectMapper objectMapper,
            FederationSubscribePollService federationSubscribePollService,
            ObjectPresenceService presenceService,
            ObjectManager objectManager
    ) {
        this.objectMapper = objectMapper;
        this.federationSubscribePollService = federationSubscribePollService;
        this.presenceService = presenceService;
        this.objectManager = objectManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        Object username = session.getAttributes().get("username");
        Object path = session.getAttributes().get("presencePath");
        if (username instanceof String user && path instanceof String presencePath) {
            presenceService.remove(presencePath, user);
        }
        refreshFederationPollSubscriptions();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asString("");
        if ("subscribe".equals(type)) {
            handleSubscribe(session, node);
            return;
        }
        if ("presence".equals(type)) {
            handlePresence(session, node);
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode node) throws IOException {
        Set<String> paths = new HashSet<>();
        JsonNode pathsNode = node.get("paths");
        if (pathsNode != null && pathsNode.isArray()) {
            pathsNode.forEach(entry -> {
                if (entry.isString()) {
                    String path = entry.asString().trim();
                    if (!path.isBlank()) {
                        paths.add(path);
                    }
                }
            });
        }
        session.getAttributes().put(SUBSCRIBE_ATTR, paths);
        refreshFederationPollSubscriptions();
    }

    private void handlePresence(WebSocketSession session, JsonNode node) throws IOException {
        String path = node.path("path").asString(null);
        String username = node.path("username").asString(null);
        String mode = node.path("mode").asString("view");
        if (path == null || username == null) {
            return;
        }
        session.getAttributes().put("username", username);
        session.getAttributes().put("presencePath", path);
        presenceService.heartbeat(path, username, mode);
        Map<String, Object> response = Map.of(
                "type", "presence",
                "path", path,
                "entries", presenceService.listForPath(path).stream()
                        .map(entry -> Map.of(
                                "username", entry.username(),
                                "mode", entry.mode(),
                                "lastSeen", entry.lastSeen().toString()
                        ))
                        .toList()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    public int activeSessionCount() {
        return (int) sessions.stream().filter(WebSocketSession::isOpen).count();
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) throws IOException {
        Long revision = event.revision();
        if (revision == null) {
            try {
                revision = objectManager.require(event.path()).revision();
            } catch (Exception ignored) {
                revision = null;
            }
        }
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("type", event.type().name());
        message.put("path", event.path());
        message.put("variableName", event.variableName() != null ? event.variableName() : "");
        message.put("timestamp", event.timestamp().toString());
        if (revision != null) {
            message.put("revision", revision);
        }
        if (event.changedBy() != null) {
            message.put("changedBy", event.changedBy());
        }
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(message));
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            if (!matchesSubscription(session, event.path())) {
                continue;
            }
            session.sendMessage(payload);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean matchesSubscription(WebSocketSession session, String eventPath) {
        Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
        if (!(raw instanceof Set<?> subscribed) || subscribed.isEmpty()) {
            return true;
        }
        for (Object entry : subscribed) {
            if (entry instanceof String path && matchesPath(eventPath, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPath(String eventPath, String subscribedPath) {
        return eventPath.equals(subscribedPath)
                || eventPath.startsWith(subscribedPath + ".")
                || subscribedPath.startsWith(eventPath + ".");
    }

    @SuppressWarnings("unchecked")
    private void refreshFederationPollSubscriptions() {
        Set<String> federated = new HashSet<>();
        for (WebSocketSession session : sessions) {
            Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
            if (raw instanceof Set<?> subscribed) {
                for (Object entry : subscribed) {
                    if (entry instanceof String path && FederationPaths.isCatalogMirrorPath(path)) {
                        federated.add(path);
                    }
                }
            }
        }
        federationSubscribePollService.replaceSubscriptions(federated);
    }
}
