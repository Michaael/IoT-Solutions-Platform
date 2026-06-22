package com.ispf.server.ai.context;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ContextPackBuildTest {

    @Autowired
    private ContextPackService contextPackService;

    @Test
    void contextPackHasStructuredIndices() {
        Map<String, Object> pack = contextPackService.loadPack();

        assertFalse(String.valueOf(pack.get("contextPackVersion")).contains("0.1.0-SNAPSHOT"));
        assertTrue(pack.get("driverCatalog") instanceof List<?> drivers && !drivers.isEmpty());
        assertTrue(pack.get("exampleSummaries") instanceof List<?> examples && !examples.isEmpty());
        assertTrue(pack.get("featureIndex") instanceof List<?> features && !features.isEmpty());
        assertTrue(pack.get("docChunks") instanceof List<?> chunks && !chunks.isEmpty());
    }
}
