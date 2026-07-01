package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Soft pace reminders as a turn grows — nudge toward finish without hard-stopping early.
 */
final class AgentTurnPaceHints {

    private AgentTurnPaceHints() {
    }

    static String gentlePaceSuffix(List<Map<String, Object>> steps, int maxStepsTotal) {
        int stepCount = steps != null ? steps.size() : 0;
        int remaining = Math.max(0, maxStepsTotal - stepCount);
        if (stepCount >= 60) {
            return " Шагов уже " + stepCount + " — если цель достигнута, лучше finish; иначе только нужный следующий инструмент.";
        }
        if (stepCount >= 40) {
            return " Шагов " + stepCount + " — проверьте, хватает ли данных для ответа или завершения плана.";
        }
        if (stepCount >= 20) {
            return " Старайтесь не повторять одни и те же catalog-инструменты; двигайтесь к finish.";
        }
        if (remaining <= 12 && remaining > 0) {
            return " Осталось ~" + remaining + " шагов до лимита turn (" + maxStepsTotal + ") — завершите finish, если цель достигнута.";
        }
        return "";
    }
}
