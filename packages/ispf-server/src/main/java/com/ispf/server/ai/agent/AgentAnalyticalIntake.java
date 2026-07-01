package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Analytical TZ intake — explicit requirements from implicit user phrases (SIF / business analysis best practices).
 */
public final class AgentAnalyticalIntake {

    /** Core blueprint sections required before approval (correlation_layer may be N/A). */
    public static final List<String> REQUIRED_SECTION_IDS = List.of(
            "ground_truth",
            "intent_scope",
            "model_strategy",
            "source_layer",
            "aggregation_layer",
            "alert_layer",
            "operator_layer",
            "validation_layer"
    );

    private static final Set<String> INTAKE_PLAN_KEYS = Set.of(
            "specBrief",
            "gapMatrix",
            "objectTypesCoverage",
            "assumptions",
            "constraints",
            "executiveSummary",
            "handoffFrame",
            "conformance",
            "deliveryPhases"
    );

    private static final int MIN_SECTION_SUMMARY_CHARS = 80;
    private static final int MIN_IMPLEMENTATION_STEPS = 2;
    private static final int MIN_FUNCTIONAL_REQUIREMENTS = 3;

    private AgentAnalyticalIntake() {
    }

    public static String guide() {
        return """
                
                ## Analytical TZ intake (best practice — implicit → explicit)
                
                Treat every user phrase as raw material. Before implementation sections, produce ANALYSIS artifacts:
                
                1. **Decompose intent** — title, business goal, actors (operator/engineer), success criteria.
                2. **Extract entities** — equipment, zones, signals; never invent slugs — derive from user text or ask.
                3. **Functional requirements (FR-*)** — each row:
                   {id, title, sourcePhrase, layer, acceptanceCriteria}
                   sourcePhrase = quote or paraphrase of user/TZ line that triggered this FR.
                4. **Assumptions & constraints** — what you inferred; mark TBD with a question id.
                5. **Gap matrix** — FR → platform capability (full / out_of_scope) + gapId.
                6. **Section plan** — each section ties to FR ids in summary; steps name concrete tools + paths.
                
                Compact specBrief (BOOTSTRAP turn — allowed early):
                {"title":"…","businessGoal":"…","entities":[{"id":"MNA","label":"…","kind":"pump"}],
                 "functionalRequirements":[{"id":"FR-1","title":"…","sourcePhrase":"…","layer":"source_layer"}],
                 "assumptions":["…"],"constraints":["…"]}
                
                Each plan.sections[] entry MUST include:
                - summary: 2–4 sentences binding section to FR ids and naming policy
                - steps: concrete tool calls (not «создать 6 устройств»)
                - deliverables: paths or object ids expected after the section
                - relatedFrIds: ["FR-1","FR-2"] when applicable
                
                Executive summary: set plan.executiveSummary (3–5 sentences) — TZ binding for the user.
                Do NOT approve until analysis + all required sections are substantive.
                """;
    }

    public static void mergeFinishIntakeIntoPlan(Map<String, Object> plan, Map<String, Object> finishResult) {
        if (plan == null || finishResult == null) {
            return;
        }
        for (String key : INTAKE_PLAN_KEYS) {
            Object value = finishResult.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> map && map.isEmpty()) {
                continue;
            }
            if (value instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            plan.put(key, deepCopyValue(value));
        }
        Object assignmentType = finishResult.get("assignmentType");
        if (assignmentType != null && !String.valueOf(assignmentType).isBlank()) {
            plan.put("assignmentType", String.valueOf(assignmentType));
        }
        Object pitfalls = finishResult.get("pitfalls");
        if (pitfalls instanceof List<?> list && !list.isEmpty()) {
            plan.put("pitfalls", deepCopyValue(pitfalls));
        }
    }

    public static void enrichFinishFromPlan(Map<String, Object> finishResult, Map<String, Object> plan) {
        if (finishResult == null || plan == null || plan.isEmpty()) {
            return;
        }
        for (String key : INTAKE_PLAN_KEYS) {
            if (plan.containsKey(key) && plan.get(key) != null) {
                finishResult.put(key, deepCopyValue(plan.get(key)));
            }
        }
        if (plan.get("pitfalls") != null) {
            finishResult.put("pitfalls", deepCopyValue(plan.get("pitfalls")));
        }
        if (plan.get("assignmentType") != null) {
            finishResult.put("assignmentType", plan.get("assignmentType"));
        }
    }

    public static boolean readyForApproval(Map<String, Object> plan, Map<String, Object> finishResult) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        return completenessGaps(plan, finishResult).isEmpty();
    }

    public static List<String> completenessGaps(Map<String, Object> plan, Map<String, Object> finishResult) {
        return completenessGaps(plan, finishResult, null);
    }

    public static List<String> completenessGaps(
            Map<String, Object> plan,
            Map<String, Object> finishResult,
            String userMessage
    ) {
        boolean russian = preferRussianLocale(plan, finishResult, userMessage);
        List<String> gaps = new ArrayList<>();
        if (stringValue(plan.get("goal")).isBlank() && stringValue(plan.get("executiveSummary")).isBlank()) {
            gaps.add(russian
                    ? "Не указаны plan.goal и plan.executiveSummary"
                    : "plan.goal or plan.executiveSummary missing");
        }
        Map<String, Object> specBrief = resolveSpecBrief(plan, finishResult);
        if (!specBriefMeetsMinimum(specBrief)) {
            gaps.add(russian
                    ? "specBrief неполный — нужны title, entities[], ≥" + MIN_FUNCTIONAL_REQUIREMENTS
                    + " functionalRequirements с sourcePhrase"
                    : "specBrief incomplete — need title, entities[], ≥" + MIN_FUNCTIONAL_REQUIREMENTS
                    + " functionalRequirements with sourcePhrase");
        }
        List<Map<String, Object>> sections = AgentPlanSections.readSections(plan);
        if (sections.size() < REQUIRED_SECTION_IDS.size()) {
            gaps.add(russian
                    ? "Секций plan.sections: " + sections.size() + " — нужны все " + REQUIRED_SECTION_IDS.size()
                    + " базовых слоёв"
                    : "plan.sections count " + sections.size() + " — need all " + REQUIRED_SECTION_IDS.size()
                    + " core sections");
        }
        Set<String> presentIds = sectionIds(sections);
        for (String requiredId : REQUIRED_SECTION_IDS) {
            if (!presentIds.contains(requiredId)) {
                gaps.add(russian
                        ? "Нет секции: " + sectionLabel(requiredId, true)
                        : "missing section id: " + requiredId);
            }
        }
        for (Map<String, Object> section : sections) {
            String id = sectionId(section);
            if (!sectionMeetsQuality(section, id)) {
                String label = sectionLabel(id, russian);
                gaps.add(russian
                        ? "Секция " + label + " слишком краткая — summary ≥" + MIN_SECTION_SUMMARY_CHARS
                        + " символов, ≥" + MIN_IMPLEMENTATION_STEPS + " конкретных шагов, deliverables[]"
                        : "section " + id + " too thin — need summary ≥" + MIN_SECTION_SUMMARY_CHARS
                        + " chars, ≥" + MIN_IMPLEMENTATION_STEPS + " concrete steps, deliverables[]");
            }
        }
        Object coverage = plan.get("objectTypesCoverage");
        if (!(coverage instanceof List<?> cov) || cov.isEmpty()) {
            gaps.add(russian
                    ? "Нет objectTypesCoverage[] — перечислите типы объектов платформы"
                    : "objectTypesCoverage[] missing");
        }
        Object gapMatrix = resolveGapMatrix(plan, finishResult);
        if (!(gapMatrix instanceof List<?> rows) || rows.isEmpty()) {
            gaps.add(russian
                    ? "Нет gapMatrix — сопоставьте каждый FR с capability платформы"
                    : "gapMatrix missing — map each FR to capability");
        }
        Object handoff = plan.get("handoffFrame");
        if (handoff == null && finishResult != null) {
            handoff = finishResult.get("handoffFrame");
        }
        if (!(handoff instanceof Map<?, ?>)) {
            gaps.add(russian
                    ? "Нет handoffFrame — нужен для утверждения"
                    : "handoffFrame missing for approval");
        }
        return gaps;
    }

    public static String completenessHint(Map<String, Object> plan, Map<String, Object> finishResult) {
        return completenessHint(plan, finishResult, null);
    }

    public static String completenessHint(
            Map<String, Object> plan,
            Map<String, Object> finishResult,
            String userMessage
    ) {
        List<String> gaps = completenessGaps(plan, finishResult, userMessage);
        if (gaps.isEmpty()) {
            return "";
        }
        boolean russian = preferRussianLocale(plan, finishResult, userMessage);
        StringBuilder sb = new StringBuilder(russian ? "План не готов к утверждению:\n" : "Plan not ready for approval:\n");
        gaps.forEach(g -> sb.append("- ").append(g).append("\n"));
        sb.append(russian
                ? "Дополните аналитику (specBrief, FR mapping) и секции перед утверждением."
                : "Enrich analysis (specBrief, FR mapping) and sections before primary suggestion.");
        return sb.toString().trim();
    }

    static boolean preferRussianLocale(
            Map<String, Object> plan,
            Map<String, Object> finishResult,
            String userMessage
    ) {
        if (containsCyrillic(userMessage)) {
            return true;
        }
        if (plan != null) {
            if (containsCyrillic(stringValue(plan.get("goal")))) {
                return true;
            }
            if (containsCyrillic(stringValue(plan.get("executiveSummary")))) {
                return true;
            }
        }
        Map<String, Object> specBrief = resolveSpecBrief(plan, finishResult);
        if (containsCyrillic(stringValue(specBrief.get("title")))) {
            return true;
        }
        if (containsCyrillic(stringValue(specBrief.get("businessGoal")))) {
            return true;
        }
        Object frRaw = specBrief.get("functionalRequirements");
        if (frRaw instanceof List<?> frList) {
            for (Object item : frList) {
                if (item instanceof Map<?, ?> fr) {
                    if (containsCyrillic(stringValue(fr.get("title")))
                            || containsCyrillic(stringValue(fr.get("sourcePhrase")))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String sectionLabel(String id, boolean russian) {
        if (!russian || id == null || id.isBlank()) {
            return id;
        }
        return switch (id) {
            case "ground_truth" -> "исходные данные (ground_truth)";
            case "intent_scope" -> "цели и границы (intent_scope)";
            case "model_strategy" -> "стратегия моделей (model_strategy)";
            case "source_layer" -> "слой источников (source_layer)";
            case "aggregation_layer" -> "слой агрегации (aggregation_layer)";
            case "alert_layer" -> "слой алертов (alert_layer)";
            case "operator_layer" -> "операторский слой (operator_layer)";
            case "validation_layer" -> "валидация (validation_layer)";
            default -> id;
        };
    }

    private static boolean containsCyrillic(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeBlock.of(text.charAt(i)) == Character.UnicodeBlock.CYRILLIC) {
                return true;
            }
        }
        return false;
    }

    public static boolean sectionMeetsQuality(Map<String, Object> section, String id) {
        if (section == null || section.isEmpty()) {
            return false;
        }
        String summary = stringValue(section.get("summary"));
        if ("correlation_layer".equals(id) && summary.toLowerCase(Locale.ROOT).contains("n/a")) {
            return true;
        }
        if (summary.length() < MIN_SECTION_SUMMARY_CHARS) {
            return false;
        }
        int steps = toStringList(section.get("steps")).size();
        if (steps < MIN_IMPLEMENTATION_STEPS) {
            return false;
        }
        return !toStringList(section.get("deliverables")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveSpecBrief(Map<String, Object> plan, Map<String, Object> finishResult) {
        Object raw = plan.get("specBrief");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (finishResult != null && finishResult.get("specBrief") instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static Object resolveGapMatrix(Map<String, Object> plan, Map<String, Object> finishResult) {
        Object raw = plan.get("gapMatrix");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return raw;
        }
        if (finishResult != null && finishResult.get("gapMatrix") instanceof List<?> list && !list.isEmpty()) {
            return list;
        }
        return null;
    }

    private static boolean specBriefMeetsMinimum(Map<String, Object> specBrief) {
        if (specBrief == null || specBrief.isEmpty()) {
            return false;
        }
        if (stringValue(specBrief.get("title")).isBlank()) {
            return false;
        }
        Object entities = specBrief.get("entities");
        if (!(entities instanceof List<?> entityList) || entityList.isEmpty()) {
            return false;
        }
        Object fr = specBrief.get("functionalRequirements");
        if (!(fr instanceof List<?> frList) || frList.size() < MIN_FUNCTIONAL_REQUIREMENTS) {
            return false;
        }
        int withSource = 0;
        for (Object item : frList) {
            if (item instanceof Map<?, ?> row && !stringValue(row.get("sourcePhrase")).isBlank()) {
                withSource++;
            }
        }
        return withSource >= MIN_FUNCTIONAL_REQUIREMENTS;
    }

    private static Set<String> sectionIds(List<Map<String, Object>> sections) {
        return sections.stream()
                .map(AgentAnalyticalIntake::sectionId)
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String sectionId(Map<String, Object> section) {
        String id = stringValue(section.get("id"));
        if (!id.isBlank()) {
            return id.toLowerCase(Locale.ROOT);
        }
        return stringValue(section.get("title")).toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                out.add(String.valueOf(item).trim());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }
}
