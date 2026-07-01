package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunCancellationRegistryTest {

    @Test
    void clearsStaleIdleRunOnIsRunning() throws Exception {
        AgentRunCancellationRegistry registry = new AgentRunCancellationRegistry();
        String sessionId = "sess-stale";
        var handle = registry.start(sessionId, "hello", Map.of());
        RunStateAccessor.setLastProgressMs(registry, sessionId, System.currentTimeMillis() - 16 * 60 * 1000L);
        assertThat(registry.isRunning(sessionId)).isFalse();
        handle.close();
    }

    @Test
    void touchRefreshesIdleDeadline() throws Exception {
        AgentRunCancellationRegistry registry = new AgentRunCancellationRegistry();
        String sessionId = "sess-touch";
        var handle = registry.start(sessionId, "hello", Map.of());
        RunStateAccessor.setLastProgressMs(registry, sessionId, System.currentTimeMillis() - 16 * 60 * 1000L);
        assertThat(registry.isRunning(sessionId)).isFalse();
        handle.close();

        var handle2 = registry.start(sessionId, "hello", Map.of());
        RunStateAccessor.setLastProgressMs(registry, sessionId, System.currentTimeMillis() - 16 * 60 * 1000L);
        registry.touch(sessionId);
        assertThat(registry.isRunning(sessionId)).isTrue();
        handle2.close();
    }

    @Test
    void closeRemovesOnlyOwnRunState() {
        AgentRunCancellationRegistry registry = new AgentRunCancellationRegistry();
        String sessionId = "sess-replace";
        var first = registry.start(sessionId, "first", Map.of());
        first.close();
        var second = registry.start(sessionId, "second", Map.of());
        first.close();
        assertThat(registry.isRunning(sessionId)).isTrue();
        second.close();
        assertThat(registry.isRunning(sessionId)).isFalse();
    }

    /** Test helper — package-private field access via reflection for idle timestamp. */
    private static final class RunStateAccessor {
        static void setLastProgressMs(AgentRunCancellationRegistry registry, String sessionId, long ms) {
            try {
                var field = AgentRunCancellationRegistry.class.getDeclaredField("runsBySession");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var map = (java.util.concurrent.ConcurrentHashMap<String, Object>) field.get(registry);
                Object runState = map.get(sessionId);
                var progressField = runState.getClass().getDeclaredField("lastProgressAtMs");
                progressField.setAccessible(true);
                progressField.setLong(runState, ms);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }
    }
}
