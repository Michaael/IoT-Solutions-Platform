package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in SCADA symbol reference for the platform agent (subset of web-console registry).
 */
final class MimicSymbolCatalog {

    private MimicSymbolCatalog() {
    }

    static List<Map<String, Object>> symbols(String categoryFilter) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SymbolRef symbol : SYMBOLS) {
            if (!"all".equals(categoryFilter) && !symbol.category.equals(categoryFilter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", symbol.id);
            row.put("category", symbol.category);
            row.put("bindings", symbol.bindings);
            row.put("defaultWidth", symbol.defaultWidth);
            row.put("defaultHeight", symbol.defaultHeight);
            out.add(row);
        }
        return out;
    }

    static Map<String, Object> exampleElement() {
        Map<String, Object> binding = Map.of(
                "objectPath", "root.platform.devices.demo-sensor",
                "variableName", "level",
                "valueField", "value",
                "transform", "number"
        );
        return Map.of(
                "id", "tank-demo",
                "symbolId", "tank.vertical",
                "layerId", "layer-default",
                "x", 100,
                "y", 80,
                "bindings", Map.of("fillLevel", binding)
        );
    }

    private record SymbolRef(
            String id,
            String category,
            List<String> bindings,
            int defaultWidth,
            int defaultHeight
    ) {
    }

    private static final List<SymbolRef> SYMBOLS = List.of(
            sym("tank.vertical", "process", List.of("fillLevel", "maxLevel", "rate"), 80, 120),
            sym("tank.horizontal", "process", List.of("fillLevel", "maxLevel"), 120, 80),
            sym("tank.spherical", "process", List.of("fillLevel"), 80, 80),
            sym("valve.butterfly", "process", List.of("open", "intermediate"), 40, 60),
            sym("valve.gate", "process", List.of("open"), 36, 56),
            sym("valve.check", "process", List.of("open"), 36, 36),
            sym("valve.ball", "process", List.of("open"), 36, 56),
            sym("valve.globe", "process", List.of("open"), 36, 56),
            sym("pump.centrifugal", "process", List.of("running", "fault"), 56, 56),
            sym("pump.positive", "process", List.of("running"), 56, 56),
            sym("pump.submersible", "process", List.of("running"), 56, 56),
            sym("compressor", "process", List.of("running"), 56, 56),
            sym("heat-exchanger", "process", List.of(), 80, 60),
            sym("filter", "process", List.of(), 60, 60),
            sym("separator", "process", List.of(), 60, 80),
            sym("mixer", "process", List.of("running"), 56, 56),
            sym("motor", "process", List.of("running"), 48, 48),
            sym("pipe.segment", "pipe", List.of(), 80, 12),
            sym("pipe.junction", "pipe", List.of(), 24, 24),
            sym("pipe.tee", "pipe", List.of(), 40, 40),
            sym("pipeline-track", "pipe", List.of(), 200, 24),
            sym("sensor.indicator", "sensor", List.of("value"), 32, 32),
            sym("sensor.gauge-inline", "sensor", List.of("value"), 48, 32),
            sym("data-block", "sensor", List.of("value"), 80, 40),
            sym("meter", "sensor", List.of("value"), 48, 48),
            sym("label", "annotation", List.of("text"), 120, 24),
            sym("value-badge", "annotation", List.of("value"), 80, 28),
            sym("status-arrow", "annotation", List.of("active"), 32, 32),
            sym("rect", "annotation", List.of(), 80, 40),
            sym("ellipse", "annotation", List.of(), 60, 40),
            sym("table.embedded", "annotation", List.of(), 200, 120),
            sym("gen.block", "electrical", List.of("running"), 56, 56),
            sym("breaker", "electrical", List.of("closed"), 40, 56),
            sym("disconnector", "electrical", List.of("closed"), 40, 56),
            sym("busbar.horizontal", "electrical", List.of(), 120, 8),
            sym("busbar.vertical", "electrical", List.of(), 8, 120),
            sym("transformer.two-winding", "electrical", List.of(), 56, 72),
            sym("load.block", "electrical", List.of("power"), 56, 56),
            sym("line.feeder", "electrical", List.of("energized"), 80, 12)
    );

    private static SymbolRef sym(
            String id,
            String category,
            List<String> bindings,
            int defaultWidth,
            int defaultHeight
    ) {
        return new SymbolRef(id, category, bindings, defaultWidth, defaultHeight);
    }
}
