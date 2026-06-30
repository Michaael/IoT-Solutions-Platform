package com.ispf.server.mimic;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MimicDiagramJsonSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizeEmptyDocumentAddsDefaults() {
        String json = MimicDiagramJsonSupport.normalize(Map.of("elements", List.of()), objectMapper);
        assertTrue(json.contains("\"version\":2"));
        assertTrue(json.contains("\"layer-default\""));
        assertEquals(0, MimicDiagramJsonSupport.countElements(json, objectMapper));
    }

    @Test
    void replaceElementsStoresSymbols() {
        String base = MimicLayouts.EMPTY_MIMIC;
        String updated = MimicDiagramJsonSupport.replaceElements(
                base,
                List.of(Map.of(
                        "id", "t1",
                        "symbolId", "tank.vertical",
                        "x", 100,
                        "y", 80
                )),
                List.of(),
                objectMapper
        );
        assertEquals(1, MimicDiagramJsonSupport.countElements(updated, objectMapper));
        assertTrue(updated.contains("tank.vertical"));
    }

    @Test
    void mergeElementsAppendsWithoutRemovingExisting() {
        String withOne = MimicDiagramJsonSupport.replaceElements(
                MimicLayouts.EMPTY_MIMIC,
                List.of(Map.of("id", "t1", "symbolId", "tank.vertical", "x", 10, "y", 10)),
                List.of(),
                objectMapper
        );
        String merged = MimicDiagramJsonSupport.mergeElements(
                withOne,
                List.of(Map.of("id", "v1", "symbolId", "valve.gate", "x", 200, "y", 10)),
                List.of(),
                true,
                objectMapper
        );
        assertEquals(2, MimicDiagramJsonSupport.countElements(merged, objectMapper));
    }
}
