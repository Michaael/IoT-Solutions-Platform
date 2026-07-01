package com.ispf.server.ai.agent;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentSessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String sessionsTable;
    private final String turnsTable;

    public AgentSessionRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sessionsTable = platformSqlCatalog.table("agent_sessions");
        this.turnsTable = platformSqlCatalog.table("agent_turns");
    }

    public void insert(AgentSession session) {
        jdbcTemplate.update("""
                INSERT INTO %s (session_id, actor, root_path, title, run_state_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.formatted(sessionsTable),
                session.sessionId(),
                session.actor(),
                session.rootPath(),
                session.title(),
                writeRunState(session.runState()),
                Timestamp.from(session.createdAt()),
                Timestamp.from(session.updatedAt())
        );
    }

    public void update(AgentSession session) {
        jdbcTemplate.update("""
                UPDATE %s
                SET root_path = ?, title = ?, run_state_json = ?, updated_at = ?
                WHERE session_id = ? AND actor = ?
                """.formatted(sessionsTable),
                session.rootPath(),
                session.title(),
                writeRunState(session.runState()),
                Timestamp.from(session.updatedAt()),
                session.sessionId(),
                session.actor()
        );
    }

    public void insertTurn(String sessionId, AgentTurn turn, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    turn_id, session_id, user_message, assistant_summary, status,
                    steps_json, result_json, created_at, sort_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(turnsTable),
                turn.turnId(),
                sessionId,
                turn.userMessage(),
                turn.assistantSummary(),
                turn.status(),
                writeJson(turn.steps()),
                writeJson(turnResultPayload(turn)),
                Timestamp.from(turn.createdAt()),
                sortOrder
        );
    }

    public Optional<AgentSession> find(String sessionId, String actor) {
        List<AgentSession> sessions = jdbcTemplate.query("""
                SELECT session_id, actor, root_path, title, run_state_json, created_at, updated_at
                FROM %s
                WHERE session_id = ? AND actor = ?
                """.formatted(sessionsTable),
                (rs, rowNum) -> mapSessionHeader(rs),
                sessionId,
                actor
        );
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        AgentSession header = sessions.getFirst();
        List<AgentTurn> turns = jdbcTemplate.query("""
                SELECT turn_id, user_message, assistant_summary, status, steps_json, result_json, created_at
                FROM %s
                WHERE session_id = ?
                ORDER BY sort_order ASC
                """.formatted(turnsTable),
                (rs, rowNum) -> mapTurn(rs),
                sessionId
        );
        return Optional.of(AgentSession.restore(
                header.sessionId(),
                header.actor(),
                header.rootPath(),
                header.title(),
                header.createdAt(),
                header.updatedAt(),
                header.runState(),
                turns
        ));
    }

    public boolean delete(String sessionId, String actor) {
        int deleted = jdbcTemplate.update("""
                DELETE FROM %s WHERE session_id = ? AND actor = ?
                """.formatted(sessionsTable),
                sessionId,
                actor
        );
        return deleted > 0;
    }

    public int deleteExpired(Instant cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM %s WHERE updated_at < ?
                """.formatted(sessionsTable),
                Timestamp.from(cutoff)
        );
    }

    public void saveTurn(AgentSession session, AgentTurn turn) {
        update(session);
        int sortOrder = session.turns().size() - 1;
        insertTurn(session.sessionId(), turn, Math.max(0, sortOrder));
    }

    private AgentSession mapSessionHeader(ResultSet rs) throws SQLException {
        return AgentSession.restore(
                rs.getString("session_id"),
                rs.getString("actor"),
                rs.getString("root_path"),
                rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                readRunState(rs.getString("run_state_json")),
                List.of()
        );
    }

    private AgentTurn mapTurn(ResultSet rs) throws SQLException {
        Map<String, Object> resultPayload = readStringMap(rs.getString("result_json"));
        List<Map<String, Object>> attachments = readAttachments(resultPayload);
        String interactionMode = readInteractionMode(resultPayload);
        Map<String, Object> result = stripTurnMetadata(resultPayload);
        return new AgentTurn(
                rs.getString("turn_id"),
                rs.getString("user_message"),
                rs.getString("assistant_summary"),
                rs.getString("status"),
                readListMap(rs.getString("steps_json")),
                result,
                attachments,
                interactionMode,
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static String readInteractionMode(Map<String, Object> resultPayload) {
        Object raw = resultPayload.get("_interactionMode");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        return String.valueOf(raw);
    }

    private static Map<String, Object> turnResultPayload(AgentTurn turn) {
        Map<String, Object> payload = new LinkedHashMap<>(turn.result());
        if (!turn.attachments().isEmpty()) {
            payload.put("_attachments", turn.attachments());
        }
        if (turn.interactionMode() != null && !turn.interactionMode().isBlank()) {
            payload.put("_interactionMode", turn.interactionMode());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAttachments(Map<String, Object> resultPayload) {
        Object raw = resultPayload.get("_attachments");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                attachments.add((Map<String, Object>) map);
            }
        }
        return List.copyOf(attachments);
    }

    private static Map<String, Object> stripTurnMetadata(Map<String, Object> resultPayload) {
        if (resultPayload == null || resultPayload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(resultPayload);
        result.remove("_attachments");
        result.remove("_interactionMode");
        return result;
    }

    private String writeRunState(AgentRunState runState) {
        return writeJson(runState.snapshot(objectMapper));
    }

    private AgentRunState readRunState(String json) {
        AgentRunState state = new AgentRunState();
        if (json == null || json.isBlank()) {
            return state;
        }
        try {
            Map<String, Object> restored = objectMapper.readValue(json, new TypeReference<>() {});
            state.restore(objectMapper, restored);
            return state;
        } catch (Exception ignored) {
            // legacy: validated bundle map only
        }
        try {
            Map<String, Boolean> legacy = objectMapper.readValue(json, new TypeReference<>() {});
            state.restore(legacy);
        } catch (Exception ignored) {
            // keep empty state
        }
        return state;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<Map<String, Object>> readListMap(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
