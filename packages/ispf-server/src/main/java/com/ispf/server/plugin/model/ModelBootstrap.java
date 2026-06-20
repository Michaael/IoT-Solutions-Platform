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

    private static final String SNMP_DRIVER_CONFIG =
            "{\"host\":\"127.0.0.1\",\"port\":\"161\",\"community\":\"public\",\"version\":\"2c\",\"timeoutMs\":\"3000\",\"retries\":\"1\"}";

    private static final String SNMP_POINT_MAPPINGS =
            "{\"sysName\":\"1.3.6.1.2.1.1.5.0:STRING\",\"sysDescr\":\"1.3.6.1.2.1.1.1.0:STRING\","
                    + "\"sysUpTime\":\"1.3.6.1.2.1.1.3.0\",\"sysLocation\":\"1.3.6.1.2.1.1.6.0:STRING\","
                    + "\"sysContact\":\"1.3.6.1.2.1.1.4.0:STRING\","
                    + "\"hrMemorySize\":\"1.3.6.1.2.1.25.2.2.0:INTEGER\","
                    + "\"hrSystemProcesses\":\"1.3.6.1.2.1.25.1.6.0:INTEGER\","
                    + "\"hrSystemNumUsers\":\"1.3.6.1.2.1.25.1.5.0:INTEGER\","
                    + "\"ifNumber\":\"1.3.6.1.2.1.2.1.0:INTEGER\"}";

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
                        new ModelVariableDefinition(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", true, "lastSeen", "init"))
                        ),
                        new ModelVariableDefinition(
                                "temperature",
                                "Current temperature reading",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 22.5, "unit", "C"))
                        ),
                        new ModelVariableDefinition(
                                "threshold",
                                "Alarm threshold in Celsius",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
                        ),
                        new ModelVariableDefinition(
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
                        new ModelVariableDefinition(
                                "alarmAcknowledged",
                                "Operator acknowledged the active alarm",
                                "status",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        new ModelVariableDefinition(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "virtual"))
                        ),
                        new ModelVariableDefinition(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        new ModelVariableDefinition(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 2000))
                        ),
                        new ModelVariableDefinition(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", VIRTUAL_DRIVER_CONFIG))
                        ),
                        new ModelVariableDefinition(
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
                        new ModelVariableDefinition(
                                "title",
                                "Dashboard title shown in the header",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Dashboard"))
                        ),
                        new ModelVariableDefinition(
                                "refreshIntervalMs",
                                "Widget polling interval in milliseconds",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        new ModelVariableDefinition(
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
                        new ModelVariableDefinition(
                                "title",
                                "Workflow title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Workflow"))
                        ),
                        new ModelVariableDefinition(
                                "status",
                                "Lifecycle status: DRAFT, ACTIVE, STOPPED",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "DRAFT"))
                        ),
                        new ModelVariableDefinition(
                                "bpmnXml",
                                "BPMN 2.0 process definition",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        new ModelVariableDefinition(
                                "triggerJson",
                                "Variable trigger configuration JSON",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        new ModelVariableDefinition(
                                "instanceState",
                                "Last workflow instance state JSON",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        new ModelVariableDefinition(
                                "lastRunAt",
                                "Timestamp of last workflow run",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        new ModelVariableDefinition(
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
                        new ModelVariableDefinition(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        new ModelVariableDefinition(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "virtual"))
                        ),
                        new ModelVariableDefinition(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        new ModelVariableDefinition(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        new ModelVariableDefinition(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        new ModelVariableDefinition(
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
                        new ModelVariableDefinition(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        new ModelVariableDefinition(
                                "sysName",
                                "SNMP sysName (1.3.6.1.2.1.1.5.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "sysDescr",
                                "SNMP sysDescr (1.3.6.1.2.1.1.1.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "sysUpTime",
                                "SNMP sysUpTime (1.3.6.1.2.1.1.3.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "sysLocation",
                                "SNMP sysLocation (1.3.6.1.2.1.1.6.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "sysContact",
                                "SNMP sysContact (1.3.6.1.2.1.1.4.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "hrMemorySize",
                                "Physical memory size in KB (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.2.2.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "hrSystemProcesses",
                                "Running processes (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.6.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "hrSystemNumUsers",
                                "Logged-in users (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.5.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "ifNumber",
                                "Network interfaces count (IF-MIB 1.3.6.1.2.1.2.1.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        new ModelVariableDefinition(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "snmp"))
                        ),
                        new ModelVariableDefinition(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        new ModelVariableDefinition(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        new ModelVariableDefinition(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                null,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", SNMP_DRIVER_CONFIG))
                        ),
                        new ModelVariableDefinition(
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
