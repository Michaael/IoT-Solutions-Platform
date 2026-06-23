package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.bootstrap.MiniTecPaths;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Control functions for the mini-TEC reference operator HMI.
 */
@Component
public class MiniTecFunctionHandler implements FunctionHandler {

    private static final DataSchema VOID_INPUT = DataSchema.builder("voidInput").build();
    private static final DataSchema ACTION_INPUT = DataSchema.builder("actionInput")
            .field("action", FieldType.STRING)
            .build();
    private static final DataSchema LOAD_INPUT = DataSchema.builder("loadInput")
            .field("loadPct", FieldType.DOUBLE)
            .field("millMode", FieldType.BOOLEAN)
            .build();
    private static final DataSchema RESULT = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();
    private static final DataSchema BOOL_VAL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema MEAS = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final Set<String> GPU_FUNCS = Set.of("gpu_start", "gpu_stop", "gpu_sync");
    private static final Set<String> RUMB_FUNCS = Set.of("breaker_operate");
    private static final Set<String> DGU_FUNCS = Set.of("dgu_start", "dgu_stop");
    private static final Set<String> GRPB_FUNCS = Set.of("grpb_valve_control", "grpb_pzk_reset");
    private static final Set<String> LOAD_FUNCS = Set.of("load_module_set_load");
    private static final Set<String> HUB_FUNCS = Set.of("acknowledge_alarm");

    private final ObjectManager objectManager;

    public MiniTecFunctionHandler(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!objectPath.startsWith(MiniTecPaths.FOLDER)) {
            return false;
        }
        if (!allFunctions().contains(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        return node != null && node.functions().containsKey(functionName);
    }

    private static Set<String> allFunctions() {
        Set<String> all = new java.util.HashSet<>();
        all.addAll(GPU_FUNCS);
        all.addAll(RUMB_FUNCS);
        all.addAll(DGU_FUNCS);
        all.addAll(GRPB_FUNCS);
        all.addAll(LOAD_FUNCS);
        all.addAll(HUB_FUNCS);
        return all;
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        objectManager.require(objectPath);
        return switch (functionName) {
            case "gpu_start" -> pulseBool(objectPath, "cmdStart");
            case "gpu_stop" -> pulseBool(objectPath, "cmdStop");
            case "gpu_sync" -> pulseBool(objectPath, "cmdSync");
            case "dgu_start" -> pulseBool(objectPath, "cmdStart");
            case "dgu_stop" -> pulseBool(objectPath, "cmdStop");
            case "breaker_operate" -> breakerOperate(objectPath, input);
            case "grpb_valve_control" -> grpbValve(objectPath, input);
            case "grpb_pzk_reset" -> pulseBool(objectPath, "cmdPzkReset");
            case "load_module_set_load" -> setLoad(objectPath, input);
            case "acknowledge_alarm" -> acknowledge(objectPath);
            default -> throw new IllegalArgumentException("Unsupported: " + functionName);
        };
    }

    private DataRecord pulseBool(String path, String variable) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOL_VAL, Map.of("value", true)));
        return ok("Command sent");
    }

    private DataRecord breakerOperate(String path, DataRecord input) {
        String action = stringField(input, "action");
        if ("close".equalsIgnoreCase(action)) {
            PlatformObject rumb = objectManager.require(path);
            if (!boolVal(rumb, "interlockOk")) {
                return fail("Interlock not OK");
            }
            return pulseBool(path, "cmdBreakerClose");
        }
        if ("open".equalsIgnoreCase(action)) {
            return pulseBool(path, "cmdBreakerOpen");
        }
        return fail("Unknown action: " + action);
    }

    private DataRecord grpbValve(String path, DataRecord input) {
        String action = stringField(input, "action");
        return switch (action.toLowerCase()) {
            case "open" -> pulseBool(path, "cmdValveOpen");
            case "close" -> pulseBool(path, "cmdValveClose");
            case "trip" -> pulseBool(path, "cmdGasTrip");
            default -> fail("Unknown action: " + action);
        };
    }

    private DataRecord setLoad(String path, DataRecord input) {
        double loadPct = numberField(input, "loadPct");
        boolean millMode = boolField(input, "millMode");
        if (millMode) {
            loadPct = Math.max(loadPct, 47);
            objectManager.setVariableValue(
                    MiniTecPaths.STATION_HUB,
                    "millLoadPending",
                    DataRecord.single(BOOL_VAL, Map.of("value", true))
            );
        }
        objectManager.setVariableValue(path, "loadSetpointPct",
                DataRecord.single(MEAS, Map.of("value", loadPct, "unit", "%")));
        objectManager.setVariableValue(path, "cmdSetLoad", DataRecord.single(BOOL_VAL, Map.of("value", true)));
        return ok("Load setpoint " + loadPct + "%");
    }

    private DataRecord acknowledge(String path) {
        objectManager.setVariableValue(path, "alarmLatched", DataRecord.single(BOOL_VAL, Map.of("value", false)));
        objectManager.setVariableValue(path, "millLoadPending", DataRecord.single(BOOL_VAL, Map.of("value", false)));
        return ok("Alarm acknowledged");
    }

    private static DataRecord ok(String message) {
        return DataRecord.single(RESULT, Map.of("success", true, "message", message));
    }

    private static DataRecord fail(String message) {
        return DataRecord.single(RESULT, Map.of("success", false, "message", message));
    }

    private static String stringField(DataRecord input, String name) {
        if (input == null || input.rowCount() == 0) {
            return "";
        }
        Object raw = input.firstRow().get(name);
        return raw != null ? raw.toString() : "";
    }

    private static double numberField(DataRecord input, String name) {
        Object raw = input != null && input.rowCount() > 0 ? input.firstRow().get(name) : null;
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw != null) {
            try {
                return Double.parseDouble(raw.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean boolField(DataRecord input, String name) {
        Object raw = input != null && input.rowCount() > 0 ? input.firstRow().get(name) : null;
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return raw != null && Boolean.parseBoolean(raw.toString());
    }

    private static boolean boolVal(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(v -> v.value())
                .map(r -> r.firstRow().get("value"))
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    public static FunctionDescriptor gpuStartFn() {
        return new FunctionDescriptor("gpu_start", "Start GPU", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor gpuStopFn() {
        return new FunctionDescriptor("gpu_stop", "Stop GPU", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor gpuSyncFn() {
        return new FunctionDescriptor("gpu_sync", "Synchronize GPU", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor breakerOperateFn() {
        return new FunctionDescriptor("breaker_operate", "Operate breaker", ACTION_INPUT, RESULT);
    }

    public static FunctionDescriptor dguStartFn() {
        return new FunctionDescriptor("dgu_start", "Start DGU", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor dguStopFn() {
        return new FunctionDescriptor("dgu_stop", "Stop DGU", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor grpbValveFn() {
        return new FunctionDescriptor("grpb_valve_control", "GRPB valve control", ACTION_INPUT, RESULT);
    }

    public static FunctionDescriptor grpbPzkResetFn() {
        return new FunctionDescriptor("grpb_pzk_reset", "Reset PZK", VOID_INPUT, RESULT);
    }

    public static FunctionDescriptor loadModuleSetLoadFn() {
        return new FunctionDescriptor("load_module_set_load", "Set load module", LOAD_INPUT, RESULT);
    }

    public static FunctionDescriptor acknowledgeAlarmFn() {
        return new FunctionDescriptor("acknowledge_alarm", "Acknowledge alarm", VOID_INPUT, RESULT);
    }
}
