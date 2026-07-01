package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPhasedPlanIntakeTest {

    @Test
    void resolvesSynthesisWhenSectionsPresentButIncomplete() {
        List<Map<String, Object>> sections = List.of(
                Map.of("id", "ground_truth"),
                Map.of("id", "intent_scope"),
                Map.of("id", "model_strategy"),
                Map.of("id", "source_layer"),
                Map.of("id", "aggregation_layer"),
                Map.of("id", "alert_layer"),
                Map.of("id", "operator_layer"),
                Map.of("id", "validation_layer")
        );
        Map<String, Object> plan = Map.of("goal", "NPS", "sections", sections);
        assertThat(AgentPhasedPlanIntake.resolveStageAfterMerge(plan))
                .isEqualTo(AgentPhasedPlanIntake.Stage.SYNTHESIS);
    }

    @Test
    void stripsPrimarySuggestionWhenAnalyticalGateFails() {
        Map<String, Object> finish = new java.util.LinkedHashMap<>();
        finish.put("phase", "plan");
        finish.put("suggestions", List.of(
                Map.of("label", "Утвердить", "message", "go", "primary", true)
        ));
        finish.put("plan", Map.of(
                "sections", List.of(Map.of("id", "ground_truth"))
        ));
        AgentRunState state = new AgentRunState();

        AgentPlanFinishNormalizer.applyPhasedPolicy(finish, state);

        assertThat(finish.containsKey("suggestions")).isFalse();
    }

    @Test
    void bootstrapAllowsTwoSections() {
        assertThat(AgentPhasedPlanIntake.maxSectionsThisTurn(AgentPhasedPlanIntake.Stage.BOOTSTRAP)).isEqualTo(2);
        assertThat(AgentPhasedPlanIntake.maxSectionsThisTurn(AgentPhasedPlanIntake.Stage.SYNTHESIS)).isZero();
    }
}
