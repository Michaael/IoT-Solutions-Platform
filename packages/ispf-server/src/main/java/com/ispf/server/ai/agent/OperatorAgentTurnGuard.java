package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prevents operator copilot from looping on catalog/search tools instead of acting (e.g. run_report).
 */
final class OperatorAgentTurnGuard {

    private static final Pattern REPORT_RUN_INTENT = Pattern.compile(
            "(?ius)(?:запусти|запустить|покажи|выведи|сформируй|run|execute|open|show|сменн|shift|отчёт|отчет|report)"
    );

    private OperatorAgentTurnGuard() {
    }

    record BlockDecision(
            boolean blocked,
            String error,
            String hint,
            String redirectTool,
            Map<String, Object> redirectArgs,
            OperatorAgentClarificationBuilder.ClarificationFinish clarification
    ) {
        static BlockDecision allow() {
            return new BlockDecision(false, null, null, null, null, null);
        }

        static BlockDecision block(String error, String hint) {
            return new BlockDecision(true, error, hint, null, null, null);
        }

        static BlockDecision redirect(String tool, Map<String, Object> args) {
            return new BlockDecision(true, null, null, tool, args, null);
        }

        static BlockDecision clarify(OperatorAgentClarificationBuilder.ClarificationFinish finish) {
            return new BlockDecision(true, null, null, null, null, finish);
        }

        boolean hasRedirect() {
            return redirectTool != null && !redirectTool.isBlank();
        }

        boolean hasClarification() {
            return clarification != null;
        }
    }

    static BlockDecision checkBeforeTool(
            String toolName,
            Map<String, Object> toolArgs,
            List<Map<String, Object>> steps,
            String userMessage,
            OperatorAgentScope scope
    ) {
        if (toolName == null || toolName.isBlank()) {
            return BlockDecision.allow();
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        boolean reportIntent = isReportRunIntent(userMessage);
        long listReportsCount = toolCount(steps, "list_reports");

        if ("list_reports".equals(normalized)) {
            if (listReportsCount >= 1) {
                var clarification = OperatorAgentClarificationBuilder.onRepeatListReports(steps, userMessage);
                if (clarification.isPresent()) {
                    return BlockDecision.clarify(clarification.get());
                }
                return BlockDecision.block(
                        "list_reports already called in this turn",
                        "Do not call list_reports again. Either run_report with the best path, "
                                + "or finish with a clarifying question and result.suggestions."
                );
            }
        }

        if ("run_report".equals(normalized) && scope != null) {
            String path = stringArg(toolArgs, "path");
            if (!path.isBlank() && !scope.isPathAllowed(path)) {
                var clarification = OperatorAgentClarificationBuilder.onInvalidReportPath(
                        steps,
                        userMessage,
                        path
                );
                if (clarification.isPresent()) {
                    return BlockDecision.clarify(clarification.get());
                }
                return BlockDecision.block(
                        "Report path outside operator app scope",
                        "Use run_report only with paths from list_reports for this app."
                );
            }
        }

        if (reportIntent) {
            if (Set.of(
                    "search_app_documents",
                    "list_app_documents",
                    "read_app_document",
                    "list_app_memory",
                    "remember_app_memory",
                    "list_work_queue"
            ).contains(normalized)) {
                if (listReportsCount == 0) {
                    return BlockDecision.block(
                            "User asked to run a report — do not search docs/memory first",
                            "Call list_reports once, then run_report with the best matching path, then finish."
                    );
                }
                return BlockDecision.block(
                        "User asked to run a report — stop searching and run it",
                        buildRunReportHint(steps, userMessage)
                );
            }
        }

        if ("list_app_memory".equals(normalized) && toolCount(steps, "list_app_memory") >= 2) {
            return BlockDecision.block(
                    "list_app_memory limit reached for this turn",
                    "Use run_report, get_variable_history, or finish."
            );
        }

        if ("search_app_documents".equals(normalized) && toolCount(steps, "search_app_documents") >= 1) {
            return BlockDecision.block(
                    "search_app_documents already called",
                    buildRunReportHint(steps, userMessage)
            );
        }

        return BlockDecision.allow();
    }

    static String continuationHint(
            String lastTool,
            List<Map<String, Object>> steps,
            int maxStepsTotal,
            String userMessage
    ) {
        if ("run_report".equals(lastTool)) {
            return """
                    Report data received. Prefer {"type":"finish","summary":"...key numbers...","result":{"links":[...]}} \
                    if this answers the user; otherwise one more targeted read tool is OK.""";
        }
        if (toolCount(steps, "list_reports") >= 1 && toolCount(steps, "run_report") == 0) {
            return buildRunReportHint(steps, userMessage)
                    + " Then finish with interpreted numbers.";
        }
        if (isReportRunIntent(userMessage) && toolCount(steps, "run_report") == 0) {
            return """
                    Operator report request: call list_reports ONCE (if not yet), then run_report with path=..., then finish. \
                    Avoid calling list_reports twice."""
                    + gentlePaceSuffix(steps, maxStepsTotal);
        }
        String base = AgentLoopGuard.continuationHint(lastTool, steps, maxStepsTotal);
        String pace = AgentTurnPaceHints.gentlePaceSuffix(steps, maxStepsTotal);
        return pace.isEmpty() ? base : base + pace;
    }

    /** Soft reminders as the turn grows — no hard step cap for operator. */
    static String gentlePaceSuffix(List<Map<String, Object>> steps, int maxStepsTotal) {
        return AgentTurnPaceHints.gentlePaceSuffix(steps, maxStepsTotal);
    }

    static boolean isReportRunIntent(String userMessage) {
        return userMessage != null && REPORT_RUN_INTENT.matcher(userMessage).find();
    }

    private static long toolCount(List<Map<String, Object>> steps, String toolName) {
        if (steps == null || steps.isEmpty()) {
            return 0;
        }
        return steps.stream()
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> step.get("tool"))
                .filter(java.util.Objects::nonNull)
                .map(tool -> String.valueOf(tool).toLowerCase(Locale.ROOT))
                .filter(tool -> tool.equals(toolName.toLowerCase(Locale.ROOT)))
                .count();
    }

    @SuppressWarnings("unchecked")
    private static String buildRunReportHint(List<Map<String, Object>> steps, String userMessage) {
        List<String> paths = extractReportPaths(steps);
        StringBuilder sb = new StringBuilder();
        sb.append("Call run_report NOW with path from the catalog");
        if (!paths.isEmpty()) {
            sb.append(" (pick best match for user request): ");
            sb.append(String.join(", ", paths));
        } else {
            sb.append(". Example: {\"type\":\"tool\",\"name\":\"run_report\",\"arguments\":{\"path\":\"root.platform.reports.<id>\"}}");
        }
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append(". User asked: ").append(userMessage.trim());
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> enrichListReportsResult(
            Map<String, Object> toolResult,
            String userMessage
    ) {
        if (!isReportRunIntent(userMessage) || toolResult == null) {
            return toolResult;
        }
        if (!"OK".equals(String.valueOf(toolResult.get("status")))) {
            return toolResult;
        }
        List<OperatorAgentReportResolver.ReportEntry> catalog =
                OperatorAgentReportResolver.parseCatalog(toolResult);
        String path = OperatorAgentReportResolver.resolveBestPath(userMessage, catalog);
        if (path == null) {
            return toolResult;
        }
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(toolResult);
        enriched.put("suggestedReportPath", path);
        enriched.put("nextAction", Map.of(
                "type", "tool",
                "name", "run_report",
                "arguments", Map.of("path", path)
        ));
        enriched.put("hint", "Call run_report next with suggestedReportPath — do not call list_reports again.");
        List<OperatorAgentReportResolver.ReportEntry> catalogForHint = catalog;
        OperatorAgentReportResolver.MatchAnalysis analysis =
                OperatorAgentReportResolver.analyze(userMessage, catalogForHint);
        if (analysis.needsClarification()) {
            enriched.put("needsClarification", true);
            if (analysis.mismatchReason() != null) {
                enriched.put("terminologyNote", analysis.mismatchReason());
            }
            enriched.put("clarifyHint", """
                    Prefer finish with a short question and result.suggestions (label + message per report). \
                    Do not call list_reports again.""");
        }
        return enriched;
    }

    private static String resolveReportPath(List<Map<String, Object>> steps, String userMessage) {
        Map<String, Object> lastList = lastListReportsResult(steps);
        if (lastList == null) {
            return null;
        }
        List<OperatorAgentReportResolver.ReportEntry> catalog =
                OperatorAgentReportResolver.parseCatalog(lastList);
        return OperatorAgentReportResolver.resolveBestPath(userMessage, catalog);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lastListReportsResult(List<Map<String, Object>> steps) {
        if (steps == null) {
            return null;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"list_reports".equals(step.get("tool"))) {
                continue;
            }
            Object result = step.get("result");
            if (result instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractReportPaths(List<Map<String, Object>> steps) {
        Set<String> paths = new LinkedHashSet<>();
        if (steps == null) {
            return List.of();
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"list_reports".equals(step.get("tool"))) {
                continue;
            }
            Object result = step.get("result");
            if (!(result instanceof Map<?, ?> resultMap) || !"OK".equals(String.valueOf(resultMap.get("status")))) {
                continue;
            }
            Object reports = resultMap.get("reports");
            if (!(reports instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && row.get("path") != null) {
                    paths.add(String.valueOf(row.get("path")));
                }
            }
            if (!paths.isEmpty()) {
                break;
            }
        }
        return new ArrayList<>(paths);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
