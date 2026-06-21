package com.ispf.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.expression.BindingEvaluator;
import com.ispf.expression.ExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelEngineTest {

    private ObjectTree objectTree;
    private ModelEngine engine;

    @BeforeEach
    void setUp() {
        objectTree = new ObjectTree();
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform",
                ObjectType.CUSTOM,
                "Platform",
                null,
                null
        ));
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices",
                ObjectType.CUSTOM,
                "Devices",
                null,
                null
        ));
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.models",
                ObjectType.MODEL,
                "Models",
                null,
                null
        ));

        engine = new ModelEngine(
                new ModelRegistry(),
                objectTree,
                new ExpressionEngine(),
                new BindingEvaluator()
        );
    }

    @Test
    void createsAndAppliesRelativeModel() {
        DataSchema temperatureSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();

        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                "mqtt-sensor-v1",
                "MQTT temperature sensor",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(ModelVariableDefinition.withHistory(
                        "temperature",
                        "Current temperature",
                        "telemetry",
                        temperatureSchema,
                        true,
                        true,
                        null,
                        DataRecord.single(temperatureSchema, Map.of("value", 0.0, "unit", "C"))
                )),
                List.of(new EventDescriptor(
                        "thresholdExceeded",
                        "Threshold exceeded",
                        temperatureSchema,
                        EventLevel.WARNING
                )),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createModel(model);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.sensor-1",
                ObjectType.DEVICE,
                "sensor-1",
                null,
                null
        );
        objectTree.register(device);

        ModelAttachment attachment = engine.applyModel(model.id(), device.path());

        assertThat(attachment.modelName()).isEqualTo("mqtt-sensor-v1");
        assertThat(device.getVariable("temperature")).isPresent();
        assertThat(device.events()).containsKey("thresholdExceeded");
    }

    @Test
    void instantiatesInstanceModel() {
        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                "pump-controller",
                "Pump controller",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createModel(model);

        PlatformObject instance = engine.instantiateModel(
                model.id(),
                "root.platform.devices",
                "pump-01",
                Map.of()
        );

        assertThat(instance.path()).isEqualTo("root.platform.devices.pump-01");
        assertThat(instance.templateId()).contains(model.id());
    }

    @Test
    void rejectsRelativeInstantiation() {
        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                "relative-only",
                "",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createModel(model);

        assertThatThrownBy(() -> engine.instantiateModel(
                model.id(),
                "root.platform.devices",
                "x",
                Map.of()
        )).isInstanceOf(ModelException.class);
    }

    @Test
    void bindingForResolvesSeparateBindingsTableAndDefaultBinding() {
        DataSchema alarmSchema = DataSchema.builder("alarmActive")
                .field("value", FieldType.BOOLEAN)
                .build();
        DataSchema thresholdSchema = DataSchema.builder("threshold")
                .field("value", FieldType.DOUBLE)
                .build();

        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                "sensor",
                "",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "alarmActive",
                                "",
                                "status",
                                alarmSchema,
                                true,
                                false,
                                null,
                                DataRecord.single(alarmSchema, Map.of("value", false))
                        ),
                        ModelVariableDefinition.of(
                                "temperaturePercent",
                                "",
                                "telemetry",
                                thresholdSchema,
                                true,
                                false,
                                "scale(temperature, 0, 100, 0, 1)",
                                DataRecord.single(thresholdSchema, Map.of("value", 0.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(new ModelBindingDefinition(
                        "alarmActive",
                        "self.temperature.value > self.threshold.value"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        assertThat(model.bindingFor("alarmActive"))
                .isEqualTo("self.temperature.value > self.threshold.value");
        assertThat(model.bindingFor("temperaturePercent"))
                .isEqualTo("scale(temperature, 0, 100, 0, 1)");
        assertThat(model.bindingFor("missing")).isNull();
    }

    @Test
    void appliesModelInheritanceChain() {
        DataSchema baseSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .build();
        ModelDefinition base = new ModelDefinition(
                UUID.randomUUID().toString(),
                "sensor-base-v1",
                "Base sensor",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(ModelVariableDefinition.of(
                        "temperature",
                        "Temperature",
                        "telemetry",
                        baseSchema,
                        true,
                        true,
                        null,
                        DataRecord.single(baseSchema, Map.of("value", 0.0))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        ModelDefinition extension = new ModelDefinition(
                UUID.randomUUID().toString(),
                "sensor-vendor-v1",
                "Vendor extension",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(ModelVariableDefinition.of(
                        "vendorId",
                        "Vendor id",
                        "meta",
                        DataSchema.builder("vendorId").field("value", FieldType.STRING).build(),
                        true,
                        true,
                        null,
                        DataRecord.single(
                                DataSchema.builder("vendorId").field("value", FieldType.STRING).build(),
                                Map.of("value", "ACME")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of("extendsModelId", base.id()),
                Instant.now(),
                Instant.now()
        );
        engine.createModel(base);
        engine.createModel(extension);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.vendor-sensor",
                ObjectType.DEVICE,
                "vendor-sensor",
                null,
                null
        );
        objectTree.register(device);

        engine.applyModel(extension.id(), device.path());

        assertThat(device.getVariable("temperature")).isPresent();
        assertThat(device.getVariable("vendorId")).isPresent();
    }
}
