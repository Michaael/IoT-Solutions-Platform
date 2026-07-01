package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Sectional plan layout for complex TZ — detailed breakdown by platform layers / ObjectTypes.
 */
public final class AgentPlanSections {

    private AgentPlanSections() {
    }

    public static String guide() {
        return """
                
                ## Sectional plan (mandatory for complex TZ)
                
                Put the FULL implementation plan in result.plan.sections[] — NOT a short flat steps list.
                plan.executiveSummary: 3–5 sentences — TZ binding for the user (shown in plan panel).
                Chat summary: 1–3 sentences only. All detail lives in sections + specBrief.
                
                Each section object:
                {
                  "id": "source_layer",
                  "title": "4. Источники данных (DEVICE)",
                  "summary": "2–4 предложения: FR-2, FR-3; что создаём; naming; откуда сигналы.",
                  "relatedFrIds": ["FR-2","FR-3"],
                  "objectTypes": ["DEVICE"],
                  "tools": ["list_objects", "create_virtual_device", "list_variables"],
                  "steps": [
                    "create_object CUSTOM parent=devices name=nps — папка по specBrief.entities",
                    "create_virtual_device profile=lab name=nps-mna-01 parent=root.platform.devices.nps",
                    "list_variables path=root.platform.devices.nps.nps-mna-01"
                  ],
                  "deliverables": ["root.platform.devices.nps.nps-mna-01", "telemetry OK"]
                }
                
                Required sections (include N/A summary if TZ does not need layer):
                1. ground_truth — discovery, paths, recipes, get_automation_schema
                2. intent_scope — цель, FR mapping, naming/path policy
                3. model_strategy — INSTANCE vs RELATIVE vs ABSOLUTE per entity
                4. source_layer — DEVICE, drivers, virtual profiles, list_variables
                5. aggregation_layer — CUSTOM hub, create_variable, create_binding_rule
                6. alert_layer — configure_alert, thresholds, events
                7. correlation_layer — configure_correlator, WORKFLOW triggers (N/A if not in TZ)
                8. operator_layer — MIMIC, DASHBOARD, REPORT, configure_operator_ui
                9. validation_layer — smoke: get_mimic_diagram, get_dashboard_layout, list_automation
                
                Rules:
                - All 8 core sections before approval; correlation_layer explicit N/A if unused.
                - ≤2 NEW sections per turn; SYNTHESIS enriches existing sections only.
                - Each section: summary ≥80 chars, ≥2 concrete steps, non-empty deliverables[].
                """;
    }

    public static boolean hasSections(Map<String, Object> plan) {
        return !readSections(plan).isEmpty();
    }

    public static int totalStepCount(Map<String, Object> plan) {
        if (plan == null) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> section : readSections(plan)) {
            count += toStringList(section.get("steps")).size();
        }
        if (count == 0) {
            count = toStringList(plan.get("steps")).size();
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readSections(Map<String, Object> plan) {
        if (plan == null) {
            return List.of();
        }
        Object raw = plan.get("sections");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                sections.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return sections;
    }

    public static List<String> flattenSteps(Map<String, Object> plan) {
        List<String> flat = new ArrayList<>();
        for (Map<String, Object> section : readSections(plan)) {
            String prefix = sectionTitlePrefix(section);
            for (String step : toStringList(section.get("steps"))) {
                flat.add(prefix.isBlank() ? step : prefix + step);
            }
        }
        if (flat.isEmpty() && plan != null) {
            flat.addAll(toStringList(plan.get("steps")));
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mergeSections(Object existingRaw, Object incomingRaw) {
        List<Map<String, Object>> existing = toSectionList(existingRaw);
        List<Map<String, Object>> incoming = toSectionList(incomingRaw);
        if (incoming.isEmpty()) {
            return existing;
        }
        if (existing.isEmpty()) {
            return incoming;
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> section : existing) {
            byId.put(sectionKey(section), new LinkedHashMap<>(section));
        }
        for (Map<String, Object> incomingSection : incoming) {
            String key = sectionKey(incomingSection);
            Map<String, Object> prior = byId.get(key);
            if (prior == null) {
                byId.put(key, new LinkedHashMap<>(incomingSection));
                continue;
            }
            byId.put(key, mergeSection(prior, incomingSection));
        }
        return new ArrayList<>(byId.values());
    }

    private static Map<String, Object> mergeSection(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        mergeStringField(merged, incoming, "title");
        mergeStringField(merged, incoming, "summary");
        merged.put("objectTypes", mergeStringLists(existing.get("objectTypes"), incoming.get("objectTypes")));
        merged.put("tools", mergeStringLists(existing.get("tools"), incoming.get("tools")));
        merged.put("deliverables", mergeStringLists(existing.get("deliverables"), incoming.get("deliverables")));
        merged.put("steps", AgentPlanGuard.mergePlanSteps(existing.get("steps"), incoming.get("steps")));
        return merged;
    }

    private static void mergeStringField(Map<String, Object> target, Map<String, Object> incoming, String field) {
        String existingVal = stringValue(target.get(field));
        String incomingVal = stringValue(incoming.get(field));
        if (!incomingVal.isBlank() && (existingVal.isBlank() || incomingVal.length() >= existingVal.length())) {
            target.put(field, incomingVal);
        }
    }

    private static String sectionKey(Map<String, Object> section) {
        String id = stringValue(section.get("id"));
        if (!id.isBlank()) {
            return id.toLowerCase(Locale.ROOT);
        }
        return stringValue(section.get("title")).toLowerCase(Locale.ROOT);
    }

    private static String sectionTitlePrefix(Map<String, Object> section) {
        String title = stringValue(section.get("title"));
        if (title.isBlank()) {
            return "";
        }
        return "[" + title + "] ";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toSectionList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                sections.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return sections;
    }

    private static List<String> mergeStringLists(Object left, Object right) {
        List<String> merged = new ArrayList<>();
        for (String item : toStringList(left)) {
            if (!merged.contains(item)) {
                merged.add(item);
            }
        }
        for (String item : toStringList(right)) {
            if (!merged.contains(item)) {
                merged.add(item);
            }
        }
        return merged;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = coerceStepText(item);
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private static String coerceStepText(Object item) {
        if (item instanceof String s) {
            return s.trim();
        }
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("text", "step", "description", "label", "action", "title")) {
                Object value = map.get(key);
                if (value instanceof String s && !s.isBlank()) {
                    return s.trim();
                }
            }
        }
        return Objects.toString(item, "").trim();
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
