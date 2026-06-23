package com.ispf.server.bootstrap;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.server.function.MiniTecFunctionHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INSTANCE models for the generic mini-TEC digital twin.
 */
@Component
public class MiniTecModelBootstrap {

    public static final String GPU_MODEL = "mini-tec-gpu-v1";
    public static final String GRPB_MODEL = "mini-tec-grpb-v1";
    public static final String RUMB_MODEL = "mini-tec-rumb-v1";
    public static final String DGU_MODEL = "mini-tec-dgu-v1";
    public static final String LOAD_MODEL = "mini-tec-load-module-v1";
    public static final String HUB_MODEL = "mini-tec-station-hub-v1";

    public static final String GPU_DRIVER_CONFIG_TEMPLATE =
            "{\"profile\":\"tec-gpu\",\"ratedPowerKw\":\"%s\",\"unitIndex\":\"%d\"}";
    public static final String GRPB_DRIVER_CONFIG = "{\"profile\":\"tec-grpb\"}";
    public static final String RUMB_DRIVER_CONFIG = "{\"profile\":\"tec-rumb\"}";
    public static final String DGU_DRIVER_CONFIG = "{\"profile\":\"tec-dgu\"}";
    public static final String LOAD_DRIVER_CONFIG = "{\"profile\":\"tec-load\"}";

    public static final String TEC_POINT_MAPPINGS = "{\"status\":\"sim\"}";

    private static final DataSchema MEAS = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema INT = DataSchema.builder("intValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema STR = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public MiniTecModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureMiniTecModels() {
        ensure(buildGpuModel());
        ensure(buildGrpbModel());
        ensure(buildRumbModel());
        ensure(buildDguModel());
        ensure(buildLoadModel());
        ensure(buildHubModel());
    }

    private void ensure(ModelDefinition model) {
        if (modelRegistry.findByName(model.name()).isEmpty()) {
            modelEngine.createModel(model);
        }
    }

    private static ModelVariableDefinition meas(String name, String desc, String group, String unit, double def) {
        return ModelVariableDefinition.withHistory(
                name, desc, group, MEAS, true, false,
                DataRecord.single(MEAS, Map.of("value", def, "unit", unit))
        );
    }

    private static ModelVariableDefinition boolVar(String name, String desc, String group, boolean def, boolean writable) {
        return ModelVariableDefinition.of(
                name, desc, group, BOOL, true, writable,
                DataRecord.single(BOOL, Map.of("value", def))
        );
    }

    private static ModelVariableDefinition intVar(String name, String desc, String group, int def, boolean writable) {
        return ModelVariableDefinition.of(
                name, desc, group, INT, true, writable,
                DataRecord.single(INT, Map.of("value", def))
        );
    }

    private static List<ModelVariableDefinition> driverVars(String configJson) {
        return List.of(
                ModelVariableDefinition.of("driverId", "Driver id", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", "virtual"))),
                ModelVariableDefinition.of("driverConfigJson", "Driver config", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", configJson))),
                ModelVariableDefinition.of("driverPointMappingsJson", "Point mappings", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", TEC_POINT_MAPPINGS))),
                ModelVariableDefinition.of("driverPollIntervalMs", "Poll interval ms", "driver", INT, true, true,
                        DataRecord.single(INT, Map.of("value", 2000))),
                boolVar("driverAutoStart", "Auto-start driver", "driver", true, true)
        );
    }

    private ModelDefinition buildGpuModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("jacketWaterTemp", "Jacket water temperature", "telemetry", "C", 75));
        vars.add(meas("jacketWaterPressure", "Jacket water pressure", "telemetry", "bar", 1.8));
        vars.add(meas("lubeOilTemp", "Lube oil temperature", "telemetry", "C", 70));
        vars.add(meas("lubeOilPressure", "Lube oil pressure", "telemetry", "bar", 4.5));
        vars.add(meas("mixtureTemp", "Mixture temperature", "telemetry", "C", 40));
        vars.add(meas("coolantTemp", "Coolant temperature", "telemetry", "C", 68));
        vars.add(meas("coolantPressure", "Coolant pressure", "telemetry", "bar", 1.2));
        vars.add(meas("exhaustGasTemp", "Exhaust gas temperature", "telemetry", "C", 420));
        vars.add(meas("boostPressure", "Boost pressure", "telemetry", "bar", 0.8));
        vars.add(meas("oilLevelMin", "Oil level min", "telemetry", "%", 45));
        vars.add(meas("oilLevelMax", "Oil level max", "telemetry", "%", 85));
        vars.add(meas("rpm", "Engine speed", "telemetry", "rpm", 0));
        vars.add(meas("activePowerKw", "Active power", "telemetry", "kW", 0));
        vars.add(meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("excitationVoltage", "Excitation voltage", "telemetry", "V", 0));
        vars.add(meas("windingTemp", "Winding temperature", "telemetry", "C", 85));
        vars.add(meas("throttlePosition", "Throttle position", "telemetry", "%", 0));
        vars.add(meas("gasMixerPosition", "Gas mixer position", "telemetry", "%", 40));
        vars.add(meas("gasConcentration", "Gas concentration", "telemetry", "%LEL", 0));
        vars.add(meas("runningHours", "Running hours", "exploitation", "h", 0));
        vars.add(meas("energyKwh", "Active energy", "exploitation", "kWh", 0));
        vars.add(meas("reactiveEnergyKvarh", "Reactive energy", "exploitation", "kVArh", 0));
        vars.add(boolVar("detonation", "Detonation", "status", false, false));
        vars.add(boolVar("running", "Running", "status", false, false));
        vars.add(boolVar("synced", "Synchronized", "status", false, false));
        vars.add(boolVar("manualMode", "Manual mode", "status", false, true));
        vars.add(boolVar("remoteEnabled", "Remote enabled", "status", true, false));
        vars.add(intVar("startCount", "Start count", "exploitation", 0, false));
        vars.add(boolVar("cmdStart", "Start command", "control", false, true));
        vars.add(boolVar("cmdStop", "Stop command", "control", false, true));
        vars.add(boolVar("cmdSync", "Sync command", "control", false, true));
        vars.add(meas("setpointPowerKw", "Power setpoint", "control", "kW", 1000));
        vars.add(boolVar("protOverload", "Overload protection", "protection", false, false));
        vars.add(boolVar("protOvervoltage", "Overvoltage protection", "protection", false, false));
        vars.add(boolVar("protUndervoltage", "Undervoltage protection", "protection", false, false));
        vars.add(boolVar("protAsymmetry", "Asymmetry protection", "protection", false, false));
        vars.add(boolVar("protFrequency", "Frequency protection", "protection", false, false));
        vars.add(boolVar("protExciter", "Exciter protection", "protection", false, false));
        vars.addAll(driverVars(String.format(GPU_DRIVER_CONFIG_TEMPLATE, "1480", 1)));

        List<ModelBindingRule> bindings = List.of(
                ModelBindingRule.of("prot-overload", "protOverload",
                        "self.activePowerKw[\"value\"] > 1550"),
                ModelBindingRule.of("prot-overvoltage", "protOvervoltage",
                        "self.excitationVoltage[\"value\"] > 160"),
                ModelBindingRule.of("prot-undervoltage", "protUndervoltage",
                        "self.running[\"value\"] == true && self.excitationVoltage[\"value\"] < 80"),
                ModelBindingRule.of("prot-frequency", "protFrequency",
                        "self.running[\"value\"] == true && (self.rpm[\"value\"] < 1400 || self.rpm[\"value\"] > 1600)"),
                ModelBindingRule.of("prot-exciter", "protExciter",
                        "self.excitationVoltage[\"value\"] > 180")
        );

        return model(GPU_MODEL, "Mini-TEC gas piston unit", ObjectType.DEVICE, vars, bindings,
                List.of(
                        MiniTecFunctionHandler.gpuStartFn(),
                        MiniTecFunctionHandler.gpuStopFn(),
                        MiniTecFunctionHandler.gpuSyncFn()
                ));
    }

    private ModelDefinition buildGrpbModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("gasOutletPressure", "Gas outlet pressure", "telemetry", "bar", 2.5));
        vars.add(meas("gasFlowRate", "Gas flow rate", "telemetry", "m3/h", 0));
        vars.add(meas("gasVolume", "Gas volume", "telemetry", "m3", 0));
        vars.add(meas("dpMeter", "Meter differential pressure", "telemetry", "bar", 0));
        vars.add(meas("dpFilter", "Filter differential pressure", "telemetry", "bar", 0));
        vars.add(meas("valvePosition", "Valve position", "telemetry", "%", 80));
        vars.add(meas("ambientTemp", "Ambient temperature", "telemetry", "C", 18));
        vars.add(boolVar("pzkTripped", "PZK tripped", "protection", false, false));
        vars.add(boolVar("gasLeak", "Gas leak", "protection", false, false));
        vars.add(boolVar("unauthorizedAccess", "Unauthorized access", "status", false, false));
        vars.add(boolVar("fireAlarm", "Fire alarm", "protection", false, false));
        vars.add(boolVar("cmdValveOpen", "Open valve", "control", false, true));
        vars.add(boolVar("cmdValveClose", "Close valve", "control", false, true));
        vars.add(boolVar("cmdPzkReset", "Reset PZK", "control", false, true));
        vars.add(boolVar("cmdGasTrip", "Emergency gas trip", "control", false, true));
        vars.addAll(driverVars(GRPB_DRIVER_CONFIG));
        return model(GRPB_MODEL, "Gas regulating point block", ObjectType.DEVICE, vars, List.of(),
                List.of(MiniTecFunctionHandler.grpbValveFn(), MiniTecFunctionHandler.grpbPzkResetFn()));
    }

    private ModelDefinition buildRumbModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(boolVar("breakerClosed", "Breaker closed", "status", true, false));
        vars.add(boolVar("breakerPosition", "Breaker position", "status", true, false));
        vars.add(boolVar("cartPosition", "Cart position", "status", true, false));
        vars.add(boolVar("emergencyStop", "Emergency stop", "status", false, false));
        vars.add(boolVar("terminalFault", "Terminal fault", "status", false, false));
        vars.add(boolVar("groundingSwitchClosed", "Grounding switch closed", "status", false, false));
        vars.add(boolVar("interlockOk", "Interlock OK", "status", true, false));
        vars.add(boolVar("circuitFault", "Circuit fault", "status", false, false));
        vars.add(boolVar("cmdBreakerClose", "Close breaker", "control", false, true));
        vars.add(boolVar("cmdBreakerOpen", "Open breaker", "control", false, true));
        vars.addAll(driverVars(RUMB_DRIVER_CONFIG));
        return model(RUMB_MODEL, "Switchgear 10/0.4 kV", ObjectType.DEVICE, vars, List.of(),
                List.of(MiniTecFunctionHandler.breakerOperateFn()));
    }

    private ModelDefinition buildDguModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(boolVar("cmdStart", "Start DGU", "control", false, true));
        vars.add(boolVar("cmdStop", "Stop DGU", "control", false, true));
        vars.add(boolVar("running", "DGU running", "status", false, false));
        vars.add(boolVar("batteryCharging", "Battery charging", "status", true, false));
        vars.add(meas("fuelLevelPct", "Fuel level", "telemetry", "%", 85));
        vars.add(meas("coolantTemp", "Coolant temperature", "telemetry", "C", 25));
        vars.addAll(driverVars(DGU_DRIVER_CONFIG));
        return model(DGU_MODEL, "Diesel generator unit", ObjectType.DEVICE, vars, List.of(),
                List.of(MiniTecFunctionHandler.dguStartFn(), MiniTecFunctionHandler.dguStopFn()));
    }

    private ModelDefinition buildLoadModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("activePowerKw", "Active power", "telemetry", "kW", 0));
        vars.add(meas("reactivePowerKvar", "Reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("apparentPowerKva", "Apparent power", "telemetry", "kVA", 0));
        vars.add(meas("frequencyHz", "Frequency", "telemetry", "Hz", 50));
        vars.add(meas("currentA", "Phase A current", "telemetry", "A", 0));
        vars.add(meas("currentB", "Phase B current", "telemetry", "A", 0));
        vars.add(meas("currentC", "Phase C current", "telemetry", "A", 0));
        vars.add(meas("voltageAB", "Voltage AB", "telemetry", "V", 400));
        vars.add(meas("voltageBC", "Voltage BC", "telemetry", "V", 400));
        vars.add(meas("voltageCA", "Voltage CA", "telemetry", "V", 400));
        vars.add(meas("voltageA", "Voltage A", "telemetry", "V", 230));
        vars.add(meas("voltageB", "Voltage B", "telemetry", "V", 230));
        vars.add(meas("voltageC", "Voltage C", "telemetry", "V", 230));
        vars.add(meas("loadSetpointPct", "Load setpoint", "control", "%", 35));
        vars.add(boolVar("cmdSetLoad", "Apply load setpoint", "control", false, true));
        vars.addAll(driverVars(LOAD_DRIVER_CONFIG));
        return model(LOAD_MODEL, "Resistive load module ME-1500-K3", ObjectType.DEVICE, vars, List.of(),
                List.of(MiniTecFunctionHandler.loadModuleSetLoadFn()));
    }

    private ModelDefinition buildHubModel() {
        List<ModelVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("totalGenPowerKw", "Total generation", "telemetry", "kW", 0));
        vars.add(meas("totalReactiveKvar", "Total reactive power", "telemetry", "kVAr", 0));
        vars.add(meas("totalLoadKw", "Total load", "telemetry", "kW", 4130));
        vars.add(meas("loadMarginKw", "Load margin", "telemetry", "kW", 310));
        vars.add(meas("bus10kvVoltage", "10 kV bus voltage", "telemetry", "kV", 10.5));
        vars.add(meas("bus04kvVoltage", "0.4 kV bus voltage", "telemetry", "kV", 0.4));
        vars.add(meas("gridFrequencyHz", "Grid frequency", "telemetry", "Hz", 50));
        vars.add(meas("consumerLoad1Kw", "Consumer 1 load", "telemetry", "kW", 2430));
        vars.add(meas("consumerLoad2Kw", "Consumer 2 load", "telemetry", "kW", 1200));
        vars.add(meas("consumerLoad3Kw", "Reserve load", "telemetry", "kW", 500));
        vars.add(boolVar("islandMode", "Island mode", "status", true, false));
        vars.add(ModelVariableDefinition.of("plantState", "Plant state", "status", STR, true, false,
                DataRecord.single(STR, Map.of("value", "RUNNING"))));
        vars.add(boolVar("millLoadPending", "Mill load pending manual", "status", false, true));
        vars.add(boolVar("alarmLatched", "Alarm latched", "protection", false, true));
        vars.add(boolVar("busOvervoltage", "Bus overvoltage", "protection", false, false));
        vars.add(boolVar("busUndervoltage", "Bus undervoltage", "protection", false, false));
        vars.add(boolVar("busFrequencyLow", "Bus frequency low", "protection", false, false));
        vars.add(boolVar("busFrequencyHigh", "Bus frequency high", "protection", false, false));
        vars.add(boolVar("stationUnderpower", "Station underpower", "protection", false, false));
        vars.add(boolVar("gpuSyncFault", "GPU sync fault", "protection", false, false));

        List<ModelBindingRule> bindings = List.of(
                refRule("gen-gpu1", "gpu1Power", MiniTecPaths.GPU_01, "activePowerKw"),
                refRule("gen-gpu2", "gpu2Power", MiniTecPaths.GPU_02, "activePowerKw"),
                refRule("gen-gpu3", "gpu3Power", MiniTecPaths.GPU_03, "activePowerKw"),
                sumRule("total-gen", "totalGenPowerKw", "self.gpu1Power[\"value\"] + self.gpu2Power[\"value\"] + self.gpu3Power[\"value\"]"),
                sumRule("total-reactive", "totalReactiveKvar", "self.totalGenPowerKw[\"value\"] * 0.15"),
                sumRule("total-load", "totalLoadKw",
                        "self.consumerLoad1Kw[\"value\"] + self.consumerLoad2Kw[\"value\"] + self.consumerLoad3Kw[\"value\"]"),
                sumRule("load-margin", "loadMarginKw", "4440 - self.totalLoadKw[\"value\"]"),
                sumRule("bus-freq", "gridFrequencyHz", "refAt(\"" + MiniTecPaths.LOAD_MODULE + "\", frequencyHz)"),
                sumRule("bus-10kv", "bus10kvVoltage", "10.5"),
                sumRule("bus-04kv", "bus04kvVoltage", "0.4"),
                ModelBindingRule.of("bus-overvoltage", "busOvervoltage", "self.bus10kvVoltage[\"value\"] > 11.0"),
                ModelBindingRule.of("bus-undervoltage", "busUndervoltage", "self.bus10kvVoltage[\"value\"] < 9.5"),
                ModelBindingRule.of("bus-freq-low", "busFrequencyLow", "self.gridFrequencyHz[\"value\"] < 49.5"),
                ModelBindingRule.of("bus-freq-high", "busFrequencyHigh", "self.gridFrequencyHz[\"value\"] > 50.5"),
                ModelBindingRule.of("station-underpower", "stationUnderpower",
                        "self.totalGenPowerKw[\"value\"] < self.totalLoadKw[\"value\"] - 100"),
                ModelBindingRule.of("gpu-sync-fault", "gpuSyncFault",
                        "(refAt(\"" + MiniTecPaths.GPU_01 + "\", synced)[\"value\"] == false && refAt(\""
                                + MiniTecPaths.GPU_01 + "\", running)[\"value\"] == true) || "
                                + "(refAt(\"" + MiniTecPaths.GPU_02 + "\", synced)[\"value\"] == false && refAt(\""
                                + MiniTecPaths.GPU_02 + "\", running)[\"value\"] == true) || "
                                + "(refAt(\"" + MiniTecPaths.GPU_03 + "\", synced)[\"value\"] == false && refAt(\""
                                + MiniTecPaths.GPU_03 + "\", running)[\"value\"] == true)")
        );

        // Add hidden aggregate vars for gpu powers
        vars.add(meas("gpu1Power", "GPU-01 power mirror", "internal", "kW", 0));
        vars.add(meas("gpu2Power", "GPU-02 power mirror", "internal", "kW", 0));
        vars.add(meas("gpu3Power", "GPU-03 power mirror", "internal", "kW", 0));

        return model(HUB_MODEL, "Mini-TEC station hub", ObjectType.CUSTOM, vars, bindings,
                List.of(MiniTecFunctionHandler.acknowledgeAlarmFn()));
    }

    private static ModelBindingRule refRule(String id, String target, String remotePath, String remoteVar) {
        return new ModelBindingRule(
                id,
                target,
                true,
                10,
                new BindingActivators(false, List.of(new BindingVariableRef(remotePath, remoteVar)), null, 0),
                "",
                "refAt(\"" + remotePath + "\", " + remoteVar + ")",
                target,
                "value"
        );
    }

    private static ModelBindingRule sumRule(String id, String target, String expression) {
        return ModelBindingRule.of(id, target, expression);
    }

    private static ModelDefinition model(
            String name,
            String description,
            ObjectType type,
            List<ModelVariableDefinition> variables,
            List<ModelBindingRule> bindings,
            List<FunctionDescriptor> functions
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
                functions,
                bindings,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition model(
            String name,
            String description,
            ObjectType type,
            List<ModelVariableDefinition> variables,
            List<ModelBindingRule> bindings
    ) {
        return model(name, description, type, variables, bindings, List.of());
    }
}
