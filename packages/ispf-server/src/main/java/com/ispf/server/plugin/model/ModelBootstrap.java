package com.ispf.server.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelBindingDefinition;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.server.dashboard.DashboardLayouts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds built-in models for the platform.
 */
@Component
public class ModelBootstrap {

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema THRESHOLD_SCHEMA = DataSchema.builder("threshold")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema FUNCTION_RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema VOID_INPUT_SCHEMA = DataSchema.builder("voidInput").build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final String VIRTUAL_DRIVER_CONFIG =
            "{\"baseTemperature\":\"22.0\",\"amplitude\":\"15.0\",\"periodSec\":\"60\"}";

    public static final String SNMP_AGENT_MODEL = "snmp-agent-v1";
    public static final String DEVICE_DRIVER_MODEL = "device-driver-v1";
    public static final String SNMP_LOCALHOST_PATH = "root.platform.devices.snmp-localhost";

    public static final String SNMP_DRIVER_CONFIG =
            "{\"host\":\"127.0.0.1\",\"port\":\"161\",\"community\":\"public\",\"version\":\"2c\",\"timeoutMs\":\"3000\",\"retries\":\"1\"}";

    /** OID mappings for {@link #SNMP_AGENT_MODEL} — must match dashboard {@code snmp-host-monitoring}. */
    public static final String SNMP_POINT_MAPPINGS =
            "{\"sysName\":\"1.3.6.1.2.1.1.5.0:STRING\",\"sysDescr\":\"1.3.6.1.2.1.1.1.0:STRING\","
                    + "\"sysUpTime\":\"1.3.6.1.2.1.1.3.0\",\"sysLocation\":\"1.3.6.1.2.1.1.6.0:STRING\","
                    + "\"sysContact\":\"1.3.6.1.2.1.1.4.0:STRING\","
                    + "\"hrMemorySize\":\"1.3.6.1.2.1.25.2.2.0:INTEGER\","
                    + "\"hrSystemProcesses\":\"1.3.6.1.2.1.25.1.6.0:INTEGER\","
                    + "\"hrSystemNumUsers\":\"1.3.6.1.2.1.25.1.5.0:INTEGER\","
                    + "\"ifNumber\":\"1.3.6.1.2.1.2.1.0:INTEGER\","
                    + "\"ifInOctets\":\"1.3.6.1.2.1.2.2.1.10.2:INTEGER\","
                    + "\"ifOutOctets\":\"1.3.6.1.2.1.2.2.1.16.2:INTEGER\","
                    + "\"hrProcessorLoad\":\"1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional\"}";

    private static final DataSchema SNMP_NUMERIC_SCHEMA = DataSchema.builder("snmpNumeric")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DataSchema SNMP_STRING_SCHEMA = DataSchema.builder("snmpString")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public ModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    /**
     * Registers built-in models on every startup (in-memory registry is empty after restart).
     */
    public void ensureBuiltInModels() {
        seedModels();
        ensureDeviceDriverModel();
        ensureAutomationModels();
        ensureModelInheritanceDemo();
    }

    private void ensureModelInheritanceDemo() {
        if (modelRegistry.findByName("base-sensor-v1").isEmpty()) {
            modelEngine.createModel(buildBaseSensorModel());
        }
        if (modelRegistry.findByName("vendor-sensor-ext-v1").isEmpty()) {
            String baseId = modelRegistry.findByName("base-sensor-v1")
                    .orElseThrow()
                    .id();
            modelEngine.createModel(buildVendorSensorExtensionModel(baseId));
        }
    }

    private static ModelDefinition buildBaseSensorModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "base-sensor-v1",
                "Base temperature sensor — family blueprint",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "temperature",
                                "Current temperature",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 20.0, "unit", "C"))
                        ),
                        ModelVariableDefinition.of(
                                "threshold",
                                "Alarm threshold",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of("modelVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition buildVendorSensorExtensionModel(String baseModelId) {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "vendor-sensor-ext-v1",
                "Vendor extension — adds humidity without duplicating base sensor",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "humidity",
                                "Relative humidity percent",
                                "telemetry",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 45.0))
                        ),
                        ModelVariableDefinition.of(
                                "threshold",
                                "Vendor-specific threshold override",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 40.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of("extendsModelId", baseModelId, "modelVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    private void ensureAutomationModels() {
        if (modelRegistry.findByName("alert-rule-v1").isEmpty()) {
            modelEngine.createModel(buildAlertRuleModel());
        }
        if (modelRegistry.findByName("correlator-v1").isEmpty()) {
            modelEngine.createModel(buildCorrelatorModel());
        }
    }

    static ModelDefinition buildAlertRuleModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "alert-rule-v1",
                "CEL alert rule — watches a variable and publishes events",
                ModelType.RELATIVE,
                ObjectType.ALERT,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "targetObjectPath",
                                "Object path to watch",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "watchVariable",
                                "Variable name that triggers evaluation",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "conditionExpr",
                                "CEL expression evaluated on the target object",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "eventName",
                                "Event to publish when condition is met",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "payloadVariable",
                                "Optional variable for event payload",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "enabled",
                                "Whether the rule is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "edgeTrigger",
                                "Fire only on false→true transition",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "rateLimitSeconds",
                                "Minimum seconds between event fires (0 = no limit)",
                                "config",
                                com.ispf.core.model.DataSchema.builder("rateLimitSeconds")
                                        .field("value", com.ispf.core.model.FieldType.INTEGER)
                                        .build(),
                                true,
                                true,
                                null,
                                DataRecord.single(
                                        com.ispf.core.model.DataSchema.builder("rateLimitSeconds")
                                                .field("value", com.ispf.core.model.FieldType.INTEGER)
                                                .build(),
                                        Map.of("value", 0)
                                )
                        ),
                        ModelVariableDefinition.of(
                                "lastConditionMet",
                                "Runtime: last evaluated condition result",
                                "runtime",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        ModelVariableDefinition.of(
                                "lastFiredAt",
                                "Runtime: last event fire timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    static ModelDefinition buildCorrelatorModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "correlator-v1",
                "Event correlator — COUNT or SEQUENCE patterns trigger workflows",
                ModelType.RELATIVE,
                ObjectType.CORRELATOR,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "targetObjectPath",
                                "Optional object path filter (empty = any)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "patternType",
                                "COUNT, SEQUENCE or EVENT_CHAIN",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "COUNT"))
                        ),
                        ModelVariableDefinition.of(
                                "eventName",
                                "Primary event name",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "secondEventName",
                                "Second event for SEQUENCE pattern",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "windowSeconds",
                                "Sliding window for COUNT pattern",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        ModelVariableDefinition.of(
                                "minOccurrences",
                                "Minimum occurrences in window",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 1))
                        ),
                        ModelVariableDefinition.of(
                                "cooldownSeconds",
                                "Cooldown after trigger",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 120))
                        ),
                        ModelVariableDefinition.of(
                                "actionType",
                                "Action on match (RUN_WORKFLOW)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "RUN_WORKFLOW"))
                        ),
                        ModelVariableDefinition.of(
                                "actionTarget",
                                "Workflow path for RUN_WORKFLOW",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "enabled",
                                "Whether the correlator is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "lastTriggeredAt",
                                "Runtime: last trigger timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private void ensureDeviceDriverModel() {
        if (!modelRegistry.findByName(DEVICE_DRIVER_MODEL).isEmpty()) {
            return;
        }
        modelEngine.createModel(buildDeviceDriverModel());
    }

    /** @deprecated use {@link #ensureBuiltInModels()} */
    @Deprecated
    public void seedModels() {
        if (!modelRegistry.findByName("mqtt-sensor-v1").isEmpty()) {
            return;
        }
        ModelDefinition mqttSensor = new ModelDefinition(
                UUID.randomUUID().toString(),
                "mqtt-sensor-v1",
                "MQTT temperature sensor with threshold monitoring",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", true, "lastSeen", "init"))
                        ),
                        ModelVariableDefinition.withHistory(
                                "temperature",
                                "Current temperature reading",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 22.5, "unit", "C"))
                        ),
                        ModelVariableDefinition.of(
                                "threshold",
                                "Alarm threshold in Celsius",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
                        ),
                        ModelVariableDefinition.of(
                                "temperaturePercent",
                                "Temperature normalized to 0-100% (-20..50 °C)",
                                "telemetry",
                                THRESHOLD_SCHEMA,
                                true,
                                false,
                                "scale(temperature, -20, 50, 0, 100)",
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 0.0))
                        ),
                        ModelVariableDefinition.of(
                                "alarmActive",
                                "Whether temperature exceeds threshold",
                                "status",
                                DataSchema.builder("alarmActive").field("value", FieldType.BOOLEAN).build(),
                                true,
                                false,
                                null,
                                DataRecord.single(
                                        DataSchema.builder("alarmActive").field("value", FieldType.BOOLEAN).build(),
                                        Map.of("value", false)
                                )
                        ),
                        ModelVariableDefinition.of(
                                "alarmAcknowledged",
                                "Operator acknowledged the active alarm",
                                "status",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "virtual"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 2000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", VIRTUAL_DRIVER_CONFIG))
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{\"temperature\":\"sim\"}"))
                        )
                ),
                List.of(new EventDescriptor(
                        "thresholdExceeded",
                        "Temperature exceeded configured threshold",
                        TEMPERATURE_SCHEMA,
                        EventLevel.WARNING
                )),
                List.of(new FunctionDescriptor(
                        "acknowledgeAlarm",
                        "Acknowledge active temperature alarm",
                        VOID_INPUT_SCHEMA,
                        FUNCTION_RESULT_SCHEMA
                )),
                List.of(new ModelBindingDefinition(
                        "alarmActive",
                        "self.temperature.value > self.threshold.value"
                )),
                Map.of("unit", "C"),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(mqttSensor);

        modelEngine.createModel(buildSnmpAgentModel());

        modelEngine.createModel(buildDeviceDriverModel());

        ModelDefinition dashboard = new ModelDefinition(
                UUID.randomUUID().toString(),
                "dashboard-v1",
                "Low-code HMI dashboard with widget layout stored as JSON",
                ModelType.RELATIVE,
                ObjectType.DASHBOARD,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "title",
                                "Dashboard title shown in the header",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Dashboard"))
                        ),
                        ModelVariableDefinition.of(
                                "refreshIntervalMs",
                                "Widget polling interval in milliseconds",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "layout",
                                "Dashboard layout JSON (grid + widgets)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DashboardLayouts.EMPTY_DASHBOARD))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(dashboard);

        ModelDefinition workflow = new ModelDefinition(
                UUID.randomUUID().toString(),
                "workflow-v1",
                "BPMN workflow with NATS event bridge",
                ModelType.RELATIVE,
                ObjectType.WORKFLOW,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "title",
                                "Workflow title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Workflow"))
                        ),
                        ModelVariableDefinition.of(
                                "status",
                                "Lifecycle status: DRAFT, ACTIVE, STOPPED",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "DRAFT"))
                        ),
                        ModelVariableDefinition.of(
                                "bpmnXml",
                                "BPMN 2.0 process definition",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "triggerJson",
                                "Variable trigger configuration JSON",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "instanceState",
                                "Last workflow instance state JSON",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "lastRunAt",
                                "Timestamp of last workflow run",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "lastAction",
                                "Last action recorded by workflow",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(workflow);
    }

    static ModelDefinition buildDeviceDriverModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                DEVICE_DRIVER_MODEL,
                "Generic device with driver binding (driverId, config, mappings)",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "virtual"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    static ModelDefinition buildSnmpAgentModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                SNMP_AGENT_MODEL,
                "SNMP agent device (MIB-II system group)",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysName",
                                "SNMP sysName (1.3.6.1.2.1.1.5.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysDescr",
                                "SNMP sysDescr (1.3.6.1.2.1.1.1.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "sysUpTime",
                                "SNMP sysUpTime (1.3.6.1.2.1.1.3.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysLocation",
                                "SNMP sysLocation (1.3.6.1.2.1.1.6.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysContact",
                                "SNMP sysContact (1.3.6.1.2.1.1.4.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrMemorySize",
                                "Physical memory size in KB (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.2.2.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrSystemProcesses",
                                "Running processes (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.6.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrSystemNumUsers",
                                "Logged-in users (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.5.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifNumber",
                                "Network interfaces count (IF-MIB 1.3.6.1.2.1.2.1.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifInOctets",
                                "IF-MIB ifInOctets Counter32 — total octets received on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifOutOctets",
                                "IF-MIB ifOutOctets Counter32 — total octets sent on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifInOctetsRate",
                                "Inbound traffic rate B/s derived from ifInOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                "counterRate(ifInOctets)",
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifOutOctetsRate",
                                "Outbound traffic rate B/s derived from ifOutOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                "counterRate(ifOutOctets)",
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrProcessorLoad",
                                "CPU load % (HOST-RESOURCES-MIB hrProcessorLoad, Linux index 196608)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "snmp"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", SNMP_DRIVER_CONFIG))
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", SNMP_POINT_MAPPINGS))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public ModelEngine modelEngine() {
        return modelEngine;
    }
}
