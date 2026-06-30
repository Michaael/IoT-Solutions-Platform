package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.event.EventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BindingActivatorsRuntimeTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "thresholdExceeded";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingDependencyIndex dependencyIndex;

    @Autowired
    private EventService eventService;

    @Autowired
    private BindingPeriodicScheduler periodicScheduler;

    private String targetVariable;
    private String ruleId;

    @AfterEach
    void cleanup() {
        if (ruleId != null) {
            bindingRulesService.deleteRule(DEVICE, ruleId);
            dependencyIndex.rebuild(DEVICE);
            ruleId = null;
        }
        if (targetVariable != null) {
            objectManager.deleteVariable(DEVICE, targetVariable);
            targetVariable = null;
        }
    }

    @Test
    void onEventRuleRunsWhenMatchingEventFired() {
        targetVariable = "bindEventTarget" + System.nanoTime();
        ruleId = "rule-" + targetVariable;
        ensureDoubleVariable(targetVariable, 0.0);

        bindingRulesService.upsertRule(DEVICE, new BindingRule(
                ruleId,
                targetVariable,
                true,
                0,
                new BindingActivators(false, List.of(), EVENT_NAME, 0),
                "",
                "42.0",
                new BindingTarget(targetVariable, "value")
        ));
        dependencyIndex.rebuild(DEVICE);

        eventService.fire(DEVICE, EVENT_NAME, DataRecord.empty(
                DataSchema.builder("payload").build()
        ));

        assertThat(readDouble(targetVariable)).isEqualTo(42.0);
    }

    @Test
    void periodicRuleRunsOnSchedule() {
        targetVariable = "bindPeriodicTarget" + System.nanoTime();
        ruleId = "rule-" + targetVariable;
        ensureDoubleVariable(targetVariable, 0.0);

        bindingRulesService.upsertRule(DEVICE, new BindingRule(
                ruleId,
                targetVariable,
                true,
                0,
                new BindingActivators(false, List.of(), null, 100),
                "",
                "7.5",
                new BindingTarget(targetVariable, "value")
        ));
        dependencyIndex.rebuild(DEVICE);

        periodicScheduler.tick();

        assertThat(readDouble(targetVariable)).isEqualTo(7.5);
    }

    @Test
    void normalizeRulesPreservesOnEventOnlyActivator() {
        targetVariable = "bindEventNorm" + System.nanoTime();
        ruleId = "rule-" + targetVariable;
        ensureDoubleVariable(targetVariable, 0.0);

        BindingRule saved = bindingRulesService.upsertRule(DEVICE, new BindingRule(
                ruleId,
                targetVariable,
                true,
                0,
                new BindingActivators(false, List.of(), "  " + EVENT_NAME + "  ", 0),
                "",
                "1.0",
                new BindingTarget(targetVariable, "value")
        ));
        dependencyIndex.rebuild(DEVICE);

        assertThat(saved.activators().onEvent()).isEqualTo(EVENT_NAME);
        assertThat(saved.activators().onVariableChange()).isEmpty();
    }

    private void ensureDoubleVariable(String name, double initial) {
        if (objectManager.require(DEVICE).getVariable(name).isEmpty()) {
            DataSchema schema = DataSchema.builder(name).field("value", FieldType.DOUBLE).build();
            objectManager.createVariable(
                    DEVICE,
                    name,
                    schema,
                    true,
                    false,
                    DataRecord.single(schema, Map.of("value", initial)),
                    false,
                    null
            );
        }
    }

    private double readDouble(String name) {
        return objectManager.require(DEVICE).getVariable(name)
                .flatMap(v -> v.value())
                .map(record -> ((Number) record.firstRow().get("value")).doubleValue())
                .orElseThrow();
    }
}
