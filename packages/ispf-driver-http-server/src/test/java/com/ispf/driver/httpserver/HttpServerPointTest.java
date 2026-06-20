package com.ispf.driver.httpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpServerPointTest {

    @Test
    void parsesRequestsMetric() {
        HttpServerPoint point = HttpServerPoint.parse("requests");
        assertEquals(HttpServerPoint.HttpServerMetric.REQUESTS, point.metric());
    }

    @Test
    void parsesLastPathCaseInsensitive() {
        HttpServerPoint point = HttpServerPoint.parse("LastPath");
        assertEquals(HttpServerPoint.HttpServerMetric.LAST_PATH, point.metric());
    }

    @Test
    void parsesLastBody() {
        HttpServerPoint point = HttpServerPoint.parse("lastBody");
        assertEquals(HttpServerPoint.HttpServerMetric.LAST_BODY, point.metric());
    }

    @Test
    void rejectsUnknownMetric() {
        assertThrows(IllegalArgumentException.class, () -> HttpServerPoint.parse("latency"));
    }
}
