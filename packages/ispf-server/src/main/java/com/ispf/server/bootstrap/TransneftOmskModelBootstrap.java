package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INSTANCE models for the Transneft Omsk RDP tank farm SCADA demo.
 */
@Component
public class TransneftOmskModelBootstrap {

    public static final String TANK_MODEL = "transneft-tank-v1";
    public static final String RDP_HUB_MODEL = "transneft-rdp-hub-v1";

    public static String tankDriverConfig(int tankIndex, double initialLevelMm, double rateBiasMmPerHour) {
        return String.format(
                "{\"profile\":\"transneft-tank\",\"tankIndex\":\"%d\",\"initialLevelMm\":\"%.0f\",\"rateBiasMmPerHour\":\"%.0f\",\"maxLevelMm\":\"10000\"}",
                tankIndex,
                initialLevelMm,
                rateBiasMmPerHour
        );
    }

    public static final String RDP_HUB_DRIVER_CONFIG = "{\"profile\":\"transneft-rdp\"}";

    private static final DataSchema MEAS = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema STR = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public TransneftOmskModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureTransneftModels() {
        ensure(buildTankModel());
        ensure(buildHubModel());
    }

    private void ensure(ModelDefinition desired) {
        var existing = modelRegistry.findByName(desired.name());
        if (existing.isEmpty()) {
            modelEngine.createModel(desired);
            return;
        }
        ModelDefinition current = existing.get();
        if (current.variables().size() >= desired.variables().size()) {
            return;
        }
        modelEngine.updateModel(new ModelDefinition(
                current.id(),
                desired.name(),
                desired.description(),
                desired.type(),
                desired.targetObjectType(),
                desired.suitabilityExpression(),
                desired.variables(),
                desired.events(),
                desired.functions(),
                desired.bindingRules(),
                desired.parameters(),
                current.createdAt(),
                Instant.now()
        ));
    }

    private ModelDefinition buildTankModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("fillLevelMm", "Fill level", "telemetry", "mm", 5000));
        vars.add(meas("rateMmPerHour", "Level change rate", "telemetry", "mm/h", 0));
        vars.add(meas("maxLevelMm", "Max level", "config", "mm", 10000));
        vars.add(boolVar("valveOpen", "Outlet valve open", "status", false, false));
        vars.addAll(driverVars("{\"profile\":\"transneft-tank\"}"));
        return model(TANK_MODEL, "Transneft storage tank", ObjectType.DEVICE, vars, List.of());
    }

    private ModelDefinition buildHubModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("linePressureMpa", "Line pressure", "telemetry", "MPa", 0.82));
        vars.add(meas("lineFlowM3h", "Line flow", "telemetry", "m³/h", 1240));
        vars.add(meas("lineTempC", "Line temperature", "telemetry", "°C", 12));
        vars.add(meas("deltaPressureMpa", "Differential pressure", "telemetry", "MPa", 0.04));
        vars.add(meas("totalVolumeM3", "Total stored volume", "telemetry", "m³", 81000));
        vars.addAll(driverVars(RDP_HUB_DRIVER_CONFIG));
        return model(RDP_HUB_MODEL, "Transneft RDP manifold hub", ObjectType.CUSTOM, vars, List.of());
    }

    private static List<ModelVariableDefinition> driverVars(String configJson) {
        return List.of(
                ModelVariableDefinition.of("driverId", "Driver id", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", "virtual"))),
                ModelVariableDefinition.of("driverConfigJson", "Driver config", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", configJson))),
                ModelVariableDefinition.of("driverPointMappingsJson", "Point mappings", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", MiniTecModelBootstrap.TEC_POINT_MAPPINGS)))
        );
    }

    private static ModelVariableDefinition meas(
            String name, String desc, String group, String unit, double def
    ) {
        return ModelVariableDefinition.withHistory(
                name, desc, group, MEAS, true, false,
                DataRecord.single(MEAS, Map.of("value", def, "unit", unit))
        );
    }

    private static ModelVariableDefinition boolVar(
            String name, String desc, String group, boolean def, boolean writable
    ) {
        return ModelVariableDefinition.of(name, desc, group, BOOL, true, writable,
                DataRecord.single(BOOL, Map.of("value", def)));
    }

    private static ModelDefinition model(
            String name,
            String description,
            ObjectType type,
            List<ModelVariableDefinition> variables,
            List<ModelBindingRule> bindings
    ) {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                name,
                description,
                ModelType.INSTANCE,
                type,
                "",
                variables,
                List.of(),
                List.of(),
                bindings,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
