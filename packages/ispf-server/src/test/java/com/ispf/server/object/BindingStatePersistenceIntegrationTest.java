package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BindingStatePersistenceIntegrationTest {

    private static final String DEVICE = "root.platform.devices.binding-state-persist-test";
    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL_VALUE = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    /** counterRate skips samples closer than 500 ms apart. */
    private static final long COUNTER_RATE_INTERVAL_MS = 800;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectBindingStatePort bindingStatePort;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingDependencyIndex dependencyIndex;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    private String alarmVariable;

    @BeforeEach
    void ensureIsolatedDevice() {
        if (objectManager.tree().findByPath(DEVICE).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "binding-state-persist-test",
                    ObjectType.DEVICE,
                    "Binding state persistence test device",
                    "",
                    null
            );
            objectManager.createVariable(
                    DEVICE,
                    "temperature",
                    TEMPERATURE,
                    true,
                    true,
                    DataRecord.single(TEMPERATURE, Map.of("value", 20.0, "unit", "C")),
                    false,
                    null
            );
        }
        for (BindingRule rule : bindingRulesService.listRules(DEVICE)) {
            bindingRulesService.deleteRule(DEVICE, rule.id());
        }
        dependencyIndex.rebuild(DEVICE);
        resetBindingState(DEVICE);
    }

    private void resetBindingState(String path) {
        bindingStatePort.invalidateCache(path);
        objectManager.upsertSystemVariable(
                path,
                BindingStateVariables.BINDING_STATE,
                STRING_VALUE,
                DataRecord.single(STRING_VALUE, Map.of("value", "{}"))
        );
        bindingStatePort.invalidateCache(path);
    }

    @AfterEach
    void cleanup() {
        if (alarmVariable != null) {
            bindingRulesService.deleteRule(DEVICE, "rule-" + alarmVariable);
            objectManager.deleteVariable(DEVICE, alarmVariable);
            dependencyIndex.rebuild(DEVICE);
            alarmVariable = null;
        }
    }

    @Test
    void hysteresisLatchPersistsInBindingStateVariable() {
        alarmVariable = "bindPersistAlarm" + System.nanoTime();
        objectManager.createVariable(
                DEVICE,
                alarmVariable,
                BOOL_VALUE,
                true,
                false,
                null,
                false,
                null
        );
        BindingRule rule = new BindingRule(
                "rule-" + alarmVariable,
                alarmVariable,
                true,
                0,
                BindingRulesService.defaultActivators(DEVICE, "hysteresis(temperature, 80, 70)"),
                "",
                "hysteresis(temperature, 80, 70)",
                new BindingTarget(alarmVariable, "value")
        );
        bindingRulesService.upsertRule(DEVICE, rule);
        dependencyIndex.rebuild(DEVICE);

        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 85.0, "unit", "C"))
        );
        assertTrue(readBool(alarmVariable));
        objectManager.persistNodeTree(DEVICE);
        bindingStatePort.invalidateCache(DEVICE);

        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 75.0, "unit", "C"))
        );
        bindingRuleEngine.runRulesForObject(DEVICE);
        assertTrue(readBool(alarmVariable));

        assertTrue(objectManager.require(DEVICE).getVariable(BindingStateVariables.BINDING_STATE).isPresent());
    }

    @Test
    void counterRateStatePersistsAcrossCacheReload() throws InterruptedException {
        String counterName = "bindPersistCounter" + System.nanoTime();
        String rateName = "bindPersistRate" + System.nanoTime();
        DataSchema counterSchema = DataSchema.builder("counter32")
                .field("value", FieldType.DOUBLE)
                .build();
        String ruleId = "rule-" + rateName;
        try {
            objectManager.createVariable(
                    DEVICE,
                    counterName,
                    counterSchema,
                    true,
                    true,
                    null,
                    false,
                    null
            );
            objectManager.createVariable(
                    DEVICE,
                    rateName,
                    DataSchema.builder("rate")
                            .field("value", FieldType.DOUBLE)
                            .build(),
                    true,
                    false,
                    null,
                    false,
                    null
            );
            String expression = "counterRate(" + counterName + ")";
            bindingRulesService.upsertRule(DEVICE, new BindingRule(
                    ruleId,
                    rateName,
                    true,
                    0,
                    BindingRulesService.defaultActivators(DEVICE, expression),
                    "",
                    expression,
                    new BindingTarget(rateName, "value")
            ));
            dependencyIndex.rebuild(DEVICE);

            bumpCounter(counterName, counterSchema, 1000.0);
            bumpCounter(counterName, counterSchema, 2000.0);
            awaitPositiveRate(rateName, 10_000);
            bumpCounter(counterName, counterSchema, 3000.0);

            bindingStatePort.invalidateCache(DEVICE);
            objectManager.persistNodeTree(DEVICE);

            bumpCounter(counterName, counterSchema, 4000.0);
            Double rate = awaitPositiveRate(rateName, 10_000);
            assertTrue(rate > 0, "rate should be computed after cache reload");
        } finally {
            bindingRulesService.deleteRule(DEVICE, ruleId);
            objectManager.deleteVariable(DEVICE, rateName);
            objectManager.deleteVariable(DEVICE, counterName);
            dependencyIndex.rebuild(DEVICE);
        }
    }

    private Double awaitPositiveRate(String rateName, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            bindingRuleEngine.runRulesForObject(DEVICE);
            Double rate = readDoubleOrNull(rateName);
            if (rate != null && rate > 0) {
                return rate;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for positive rate on " + rateName);
        return null;
    }

    private void bumpCounter(String counterName, DataSchema counterSchema, double value) throws InterruptedException {
        Thread.sleep(COUNTER_RATE_INTERVAL_MS);
        objectManager.setVariableValue(
                DEVICE,
                counterName,
                DataRecord.single(counterSchema, Map.of("value", value))
        );
        bindingRuleEngine.runRulesForObject(DEVICE);
    }

    private boolean readBool(String variableName) {
        return (Boolean) objectManager.require(DEVICE)
                .getVariable(variableName)
                .flatMap(Variable::value)
                .orElseThrow()
                .firstRow()
                .get("value");
    }

    private Double readDoubleOrNull(String variableName) {
        return objectManager.require(DEVICE)
                .getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> value instanceof Number number ? number.doubleValue() : null)
                .orElse(null);
    }
}
