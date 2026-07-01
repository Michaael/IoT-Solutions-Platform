package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardWidgetPlacementTest {

    @Test
    void scalesLegacyWidgetsOnFineGrid() {
        Map<String, Object> layout = Map.of(
                "columns", 84,
                "rowHeight", 8,
                "widgets", List.of(
                        Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2),
                        Map.of("id", "b", "type", "value", "x", 4, "y", 0, "w", 4, "h", 2)
                )
        );

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(84, normalized.get("columns"));
        assertEquals(28, widgets.get(0).get("w"));
        assertEquals(14, widgets.get(0).get("h"));
        assertEquals(0, widgets.get(0).get("y"));
        assertEquals(28, widgets.get(1).get("x"));
        assertEquals(0, widgets.get(1).get("y"));
    }

    @Test
    void reflowsOverlappingLegacyWidgets() {
        Map<String, Object> layout = Map.of(
                "columns", 84,
                "rowHeight", 8,
                "widgets", List.of(
                        Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2),
                        Map.of("id", "b", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2)
                )
        );

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(0, widgets.get(0).get("y"));
        assertEquals(14, widgets.get(1).get("y"));
    }

    @Test
    void migratesLegacyLayoutColumns() {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("columns", 12);
        layout.put("rowHeight", 72);
        layout.put("widgets", List.of(
                Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 6, "h", 2)
        ));

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);

        assertEquals(84, normalized.get("columns"));
        assertEquals(8, normalized.get("rowHeight"));
    }

    @Test
    void autoPlacesWidgetMissingCoordinates() {
        List<Map<String, Object>> existing = new ArrayList<>();
        existing.add(Map.of("id", "a", "x", 0, "y", 0, "w", 28, "h", 14));

        Map<String, Object> widget = new LinkedHashMap<>(Map.of(
                "id", "b",
                "type", "value",
                "title", "B"
        ));

        Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                widget,
                existing,
                84,
                8
        );

        assertEquals(28, placed.get("w"));
        assertEquals(14, placed.get("h"));
        assertEquals(14, placed.get("y"));
    }

    @Test
    void scadaMimicDefaultsToFullScreen() {
        Map<String, Object> widget = new LinkedHashMap<>(Map.of(
                "id", "mimic",
                "type", "scada-mimic",
                "title", "Mimic",
                "mimicPath", "root.platform.mimics.demo"
        ));

        Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                widget,
                List.of(),
                84,
                8
        );

        assertEquals(84, placed.get("w"));
        assertEquals(63, placed.get("h"));
    }
}
