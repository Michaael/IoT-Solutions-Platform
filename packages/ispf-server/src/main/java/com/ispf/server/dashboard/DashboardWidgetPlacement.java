package com.ispf.server.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fine-grid placement for dashboard widgets (84 columns × 8px row height).
 * Migrates legacy 12-column agent layouts and auto-stacks widgets missing coordinates.
 */
public final class DashboardWidgetPlacement {

    public static final int FINE_GRID_SCALE = 7;
    public static final int DEFAULT_COLUMNS = 12 * FINE_GRID_SCALE;
    public static final int DEFAULT_ROW_HEIGHT = 8;

    private static final int LEGACY_COLUMNS = 12;
    private static final int LEGACY_ROW_HEIGHT = 72;
    private static final int LEGACY_MAX_UNIT = 12;

    private DashboardWidgetPlacement() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeLayoutMap(Map<String, Object> layout) {
        Map<String, Object> copy = new LinkedHashMap<>(layout);
        int columns = intField(copy.get("columns"), DEFAULT_COLUMNS);
        int rowHeight = intField(copy.get("rowHeight"), DEFAULT_ROW_HEIGHT);

        if (columns == LEGACY_COLUMNS && rowHeight == LEGACY_ROW_HEIGHT) {
            columns = DEFAULT_COLUMNS;
            rowHeight = DEFAULT_ROW_HEIGHT;
        }
        copy.put("columns", columns);
        copy.put("rowHeight", rowHeight);

        Object widgetsRaw = copy.get("widgets");
        if (!(widgetsRaw instanceof List<?> list)) {
            copy.put("widgets", List.of());
            return copy;
        }

        List<Map<String, Object>> widgets = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                widgets.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        scaleLegacyWidgets(widgets, columns);
        reflowOverlappingWidgets(widgets);
        copy.put("widgets", widgets);
        return copy;
    }

    public static Map<String, Object> prepareNewWidget(
            Map<String, Object> widget,
            List<Map<String, Object>> existingWidgets,
            int columns,
            int rowHeight
    ) {
        Map<String, Object> prepared = new LinkedHashMap<>(widget);
        scaleLegacyWidget(prepared, columns);
        applyDefaults(prepared, existingWidgets, columns);
        return prepared;
    }

    private static void scaleLegacyWidgets(List<Map<String, Object>> widgets, int columns) {
        if (columns < DEFAULT_COLUMNS || widgets.isEmpty()) {
            return;
        }
        boolean allLegacySized = widgets.stream().allMatch(DashboardWidgetPlacement::looksLegacySized);
        if (!allLegacySized) {
            return;
        }
        for (Map<String, Object> widget : widgets) {
            scaleLegacyWidget(widget, columns);
        }
    }

    private static void scaleLegacyWidget(Map<String, Object> widget, int columns) {
        if (columns < DEFAULT_COLUMNS || !looksLegacySized(widget)) {
            return;
        }
        putScaled(widget, "x");
        putScaled(widget, "y");
        putScaled(widget, "w");
        putScaled(widget, "h");
    }

    private static boolean looksLegacySized(Map<String, Object> widget) {
        int w = intField(widget.get("w"), 0);
        int h = intField(widget.get("h"), 0);
        if (w <= 0 || h <= 0) {
            return true;
        }
        return w <= LEGACY_MAX_UNIT
                && h <= LEGACY_MAX_UNIT
                && !isFineGridUnit(w)
                && !isFineGridUnit(h);
    }

    private static boolean isFineGridUnit(int value) {
        return value >= FINE_GRID_SCALE && value % FINE_GRID_SCALE == 0;
    }

    private static void putScaled(Map<String, Object> widget, String key) {
        int value = intField(widget.get(key), 0);
        if (value > 0) {
            widget.put(key, value * FINE_GRID_SCALE);
        }
    }

    private static void applyDefaults(
            Map<String, Object> widget,
            List<Map<String, Object>> existingWidgets,
            int columns
    ) {
        String type = String.valueOf(widget.getOrDefault("type", ""));
        int defaultW = "scada-mimic".equals(type) ? columns : 4 * FINE_GRID_SCALE;
        int defaultH = "scada-mimic".equals(type) ? 9 * FINE_GRID_SCALE : 2 * FINE_GRID_SCALE;

        int w = intField(widget.get("w"), 0);
        int h = intField(widget.get("h"), 0);
        if (w <= 0) {
            w = defaultW;
        }
        if (h <= 0) {
            h = defaultH;
        }
        w = Math.min(w, columns);

        int x = intField(widget.get("x"), -1);
        int y = intField(widget.get("y"), -1);
        if (x < 0 || y < 0) {
            int[] slot = nextVerticalSlot(existingWidgets, h);
            x = slot[0];
            y = slot[1];
        }

        widget.put("x", Math.max(0, x));
        widget.put("y", Math.max(0, y));
        widget.put("w", w);
        widget.put("h", h);
    }

    private static int[] nextVerticalSlot(List<Map<String, Object>> existingWidgets, int height) {
        int maxBottom = 0;
        for (Map<String, Object> existing : existingWidgets) {
            int y = intField(existing.get("y"), 0);
            int h = intField(existing.get("h"), 0);
            if (h > 0) {
                maxBottom = Math.max(maxBottom, y + h);
            }
        }
        return new int[] {0, maxBottom};
    }

    private static void reflowOverlappingWidgets(List<Map<String, Object>> widgets) {
        if (widgets.size() < 2) {
            return;
        }
        List<Map<String, Object>> placed = new ArrayList<>();
        for (Map<String, Object> widget : widgets) {
            Map<String, Object> next = new LinkedHashMap<>(widget);
            if (placed.isEmpty()) {
                placed.add(next);
                continue;
            }
            if (overlapsAny(next, placed)) {
                int[] slot = nextVerticalSlot(placed, intField(next.get("h"), 2 * FINE_GRID_SCALE));
                next.put("x", slot[0]);
                next.put("y", slot[1]);
            }
            placed.add(next);
        }
        widgets.clear();
        widgets.addAll(placed);
    }

    private static boolean overlapsAny(Map<String, Object> candidate, List<Map<String, Object>> placed) {
        int x = intField(candidate.get("x"), 0);
        int y = intField(candidate.get("y"), 0);
        int w = intField(candidate.get("w"), 1);
        int h = intField(candidate.get("h"), 1);
        for (Map<String, Object> other : placed) {
            int ox = intField(other.get("x"), 0);
            int oy = intField(other.get("y"), 0);
            int ow = intField(other.get("w"), 1);
            int oh = intField(other.get("h"), 1);
            if (x < ox + ow && x + w > ox && y < oy + oh && y + h > oy) {
                return true;
            }
        }
        return false;
    }

    private static int intField(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
