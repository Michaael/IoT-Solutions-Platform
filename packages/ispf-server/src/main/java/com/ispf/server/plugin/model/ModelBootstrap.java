package com.ispf.server.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.plugin.model.SystemIntrinsicModels;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.dashboard.DashboardContextSupport;
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
        ensureAutomationModels();
    }

    private void ensureAutomationModels() {
        if (modelRegistry.findByName("alert-rule-v1").isEmpty()) {
            modelEngine.createModel(buildAlertRuleModel().withSystemIntrinsicFlag());
        }
        if (modelRegistry.findByName("correlator-v1").isEmpty()) {
            modelEngine.createModel(buildCorrelatorModel().withSystemIntrinsicFlag());
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
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "watchVariable",
                                "Variable name that triggers evaluation",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "conditionExpr",
                                "CEL expression evaluated on the target object",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "eventName",
                                "Event to publish when condition is met",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "payloadVariable",
                                "Optional variable for event payload",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "enabled",
                                "Whether the rule is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "edgeTrigger",
                                "Fire only on false→true transition",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "delaySeconds",
                                "Seconds condition must stay true before firing (with sustainWhileTrue)",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        ModelVariableDefinition.of(
                                "sustainWhileTrue",
                                "Require condition to remain true for delaySeconds before firing",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        ModelVariableDefinition.of(
                                "rateLimitSeconds",
                                "Minimum seconds between event fires (0 = no limit)",
                                "config",
                                com.ispf.core.model.DataSchema.builder("rateLimitSeconds")
                                        .field("value", com.ispf.core.model.FieldType.INTEGER)
                                        .build(),
                                true,
                                true, DataRecord.single(
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
                                false, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        ),
                        ModelVariableDefinition.of(
                                "lastFiredAt",
                                "Runtime: last event fire timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "conditionTrueSince",
                                "Runtime: when condition first became true (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
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
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "patternType",
                                "COUNT, SEQUENCE or EVENT_CHAIN",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "COUNT"))
                        ),
                        ModelVariableDefinition.of(
                                "eventName",
                                "Primary event name",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "secondEventName",
                                "Second event for SEQUENCE pattern",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "windowSeconds",
                                "Sliding window for COUNT pattern",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        ModelVariableDefinition.of(
                                "minOccurrences",
                                "Minimum occurrences in window",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 1))
                        ),
                        ModelVariableDefinition.of(
                                "cooldownSeconds",
                                "Cooldown after trigger",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 120))
                        ),
                        ModelVariableDefinition.of(
                                "sequenceGapSeconds",
                                "Max seconds between consecutive events (SEQUENCE / EVENT_CHAIN; 0 = no limit)",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 0))
                        ),
                        ModelVariableDefinition.of(
                                "actionType",
                                "Action on match (RUN_WORKFLOW)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "RUN_WORKFLOW"))
                        ),
                        ModelVariableDefinition.of(
                                "actionTarget",
                                "Workflow path, event name, variable=value, or report path",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "payloadFilterExpr",
                                "Optional CEL filter on latest event payload (payload map context)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "enabled",
                                "Whether the correlator is active",
                                "config",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        ModelVariableDefinition.of(
                                "lastTriggeredAt",
                                "Runtime: last trigger timestamp (ISO-8601)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    /** @deprecated use {@link #ensureBuiltInModels()} */
    @Deprecated
    public void seedModels() {
        if (!modelRegistry.findByName("dashboard-v1").isEmpty()) {
            return;
        }

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
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Dashboard"))
                        ),
                        ModelVariableDefinition.of(
                                "refreshIntervalMs",
                                "Widget polling interval in milliseconds",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "layout",
                                "Dashboard layout JSON (grid + widgets)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DashboardLayouts.EMPTY_DASHBOARD))
                        ),
                        ModelVariableDefinition.of(
                                com.ispf.core.dashboard.DashboardContextConstants.VARIABLE,
                                "Operator session context (selection, params, widgets)",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DashboardContextSupport.EMPTY_JSON))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(dashboard.withSystemIntrinsicFlag());

        ModelDefinition report = new ModelDefinition(
                UUID.randomUUID().toString(),
                "report-v1",
                "SQL report definition stored on object tree (REQ-PF-12)",
                ModelType.RELATIVE,
                ObjectType.REPORT,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "title",
                                "Report title",
                                "info",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Report"))
                        ),
                        ModelVariableDefinition.of(
                                "dataSourcePath",
                                "Data source object path for SQL schema",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "query",
                                "SELECT/WITH SQL query (? placeholders)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "parameters",
                                "JSON array of SQL parameter names",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "[]"))
                        ),
                        ModelVariableDefinition.of(
                                "columns",
                                "JSON array of {field, label}",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "[]"))
                        ),
                        ModelVariableDefinition.of(
                                "defaultParameters",
                                "JSON object of default parameter values for preview",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "maxRows",
                                "Maximum rows returned",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 1000))
                        ),
                        ModelVariableDefinition.of(
                                "refreshIntervalMs",
                                "Auto-refresh interval in view mode",
                                "config",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 30000))
                        ),
                        ModelVariableDefinition.of(
                                "templateFormat",
                                "YARG template format: xlsx, docx, html (empty = no template)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "layout",
                                "Report layout JSON (web designer)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(report.withSystemIntrinsicFlag());

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
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "Workflow"))
                        ),
                        ModelVariableDefinition.of(
                                "status",
                                "Lifecycle status: DRAFT, ACTIVE, STOPPED",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "DRAFT"))
                        ),
                        ModelVariableDefinition.of(
                                "bpmnXml",
                                "BPMN 2.0 process definition",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "triggerJson",
                                "Trigger configuration JSON (variable or event)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "operatorAppId",
                                "Operator App that receives user tasks from this workflow",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "instanceState",
                                "Last workflow instance state JSON",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "lastRunAt",
                                "Timestamp of last workflow run",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "lastAction",
                                "Last action recorded by workflow",
                                "runtime",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );

        modelEngine.createModel(workflow.withSystemIntrinsicFlag());
    }

    public ModelEngine modelEngine() {
        return modelEngine;
    }
}
