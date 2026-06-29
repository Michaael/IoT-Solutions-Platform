package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;

import java.time.Instant;
import java.util.Map;

/**
 * Synthetic telemetry for mini-TEC (gas piston plant) virtual driver profiles.
 */
final class VirtualTecPoll {

    static final DataSchema MEASUREMENT = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    static final DataSchema BOOL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    static final DataSchema INT = DataSchema.builder("intValue")
            .field("value", FieldType.INTEGER)
            .build();

    static final DataSchema STATUS = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private VirtualTecPoll() {
    }

    static void pollGpu(DeviceDriver.DriverObject driver, GpuState state, double ratedKw, int unitIndex) {
        boolean cmdStart = readBool(driver, "cmdStart", false);
        boolean cmdStop = readBool(driver, "cmdStop", false);
        if (cmdStart) {
            state.targetLoadPct = Math.min(100, state.targetLoadPct + 5);
        }
        if (cmdStop) {
            state.targetLoadPct = 0;
        }
        double setpoint = readDouble(driver, "setpointPowerKw", ratedKw * 0.7);
        if (setpoint > 0) {
            state.targetLoadPct = Math.min(100, (setpoint / ratedKw) * 100);
        }

        double ramp = state.targetLoadPct > state.loadPct ? 2.5 : 4.0;
        if (state.loadPct < state.targetLoadPct) {
            state.loadPct = Math.min(state.targetLoadPct, state.loadPct + ramp);
        } else if (state.loadPct > state.targetLoadPct) {
            state.loadPct = Math.max(state.targetLoadPct, state.loadPct - ramp);
        }

        boolean running = state.loadPct > 2;
        double loadFactor = state.loadPct / 100.0;
        double power = ratedKw * loadFactor;
        double rpm = running ? 1500 + loadFactor * 50 + unitIndex * 3 : 0;
        double baseTemp = 75 + loadFactor * 25 + unitIndex * 0.5;

        long elapsed = (System.currentTimeMillis() - state.startedAt) / 1000;
        double wave = Math.sin(elapsed / 30.0 + unitIndex) * 2;

        updateMeas(driver, "jacketWaterTemp", baseTemp - 10 + wave, "C");
        updateMeas(driver, "jacketWaterPressure", running ? 1.8 + loadFactor * 0.4 : 0.1, "bar");
        updateMeas(driver, "lubeOilTemp", baseTemp - 5 + wave, "C");
        updateMeas(driver, "lubeOilPressure", running ? 4.5 + loadFactor : 0.2, "bar");
        updateMeas(driver, "mixtureTemp", 35 + loadFactor * 15, "C");
        updateMeas(driver, "coolantTemp", baseTemp - 8, "C");
        updateMeas(driver, "coolantPressure", running ? 1.2 + loadFactor * 0.3 : 0.05, "bar");
        updateMeas(driver, "exhaustGasTemp", running ? 420 + loadFactor * 180 : 25, "C");
        updateMeas(driver, "boostPressure", running ? 0.8 + loadFactor * 0.5 : 0, "bar");
        updateMeas(driver, "oilLevelMin", running ? 45 + loadFactor * 10 : 50, "%");
        updateMeas(driver, "oilLevelMax", running ? 85 - loadFactor * 5 : 90, "%");
        updateMeas(driver, "rpm", rpm, "rpm");
        updateMeas(driver, "activePowerKw", power, "kW");
        updateMeas(driver, "reactivePowerKvar", power * 0.15, "kVAr");
        updateMeas(driver, "excitationVoltage", running ? 120 + loadFactor * 30 : 0, "V");
        updateMeas(driver, "windingTemp", baseTemp + 15, "C");
        updateMeas(driver, "throttlePosition", loadFactor * 100, "%");
        updateMeas(driver, "gasMixerPosition", 40 + loadFactor * 40, "%");
        updateMeas(driver, "gasConcentration", running ? 0.1 + wave * 0.05 : 0, "%LEL");
        updateMeas(driver, "runningHours", state.runningHours + (running ? 0.001 : 0), "h");
        updateMeas(driver, "energyKwh", state.energyKwh + power / 3600.0, "kWh");
        updateMeas(driver, "reactiveEnergyKvarh", state.reactiveEnergyKvarh + (power * 0.15) / 3600.0, "kVArh");

        updateBool(driver, "detonation", running && loadFactor > 0.95);
        updateBool(driver, "running", running);
        updateBool(driver, "synced", running && loadFactor > 0.1);
        updateBool(driver, "manualMode", false);
        updateBool(driver, "remoteEnabled", true);
        updateInt(driver, "startCount", state.startCount);

        if (running && !state.wasRunning) {
            state.startCount++;
        }
        state.wasRunning = running;
        if (running) {
            state.runningHours += 0.001;
            state.energyKwh += power / 3600.0;
            state.reactiveEnergyKvarh += (power * 0.15) / 3600.0;
        }

        updateBool(driver, "cmdStart", false);
        updateBool(driver, "cmdStop", false);
        updateBool(driver, "cmdSync", false);

        updateStatus(driver);
    }

    static void pollGrpb(DeviceDriver.DriverObject driver, GrpbState state) {
        boolean trip = readBool(driver, "cmdGasTrip", false);
        boolean valveOpen = readBool(driver, "cmdValveOpen", false);
        boolean valveClose = readBool(driver, "cmdValveClose", false);
        if (trip) {
            state.valveOpenPct = 0;
            state.pzkTripped = true;
        } else if (valveOpen) {
            state.valveOpenPct = Math.min(100, state.valveOpenPct + 10);
        } else if (valveClose) {
            state.valveOpenPct = Math.max(0, state.valveOpenPct - 10);
        } else if (state.valveOpenPct < 80) {
            state.valveOpenPct = Math.min(80, state.valveOpenPct + 1);
        }

        boolean resetPzk = readBool(driver, "cmdPzkReset", false);
        if (resetPzk && !state.gasLeak && !state.fireAlarm) {
            state.pzkTripped = false;
        }

        double flow = state.valveOpenPct > 5 ? (state.valveOpenPct / 100.0) * 900 : 0;
        state.gasVolume += flow / 3600.0;

        updateMeas(driver, "gasOutletPressure", state.valveOpenPct > 0 ? 2.5 + flow * 0.01 : 0.1, "bar");
        updateMeas(driver, "gasFlowRate", flow, "m3/h");
        updateMeas(driver, "gasVolume", state.gasVolume, "m3");
        updateMeas(driver, "dpMeter", flow > 0 ? 0.15 + flow * 0.001 : 0, "bar");
        updateMeas(driver, "dpFilter", flow > 0 ? 0.08 + flow * 0.0005 : 0, "bar");
        updateMeas(driver, "valvePosition", state.valveOpenPct, "%");
        updateMeas(driver, "ambientTemp", 18 + Math.sin(System.currentTimeMillis() / 600000.0) * 3, "C");
        updateBool(driver, "pzkTripped", state.pzkTripped);
        updateBool(driver, "gasLeak", state.gasLeak);
        updateBool(driver, "unauthorizedAccess", false);
        updateBool(driver, "fireAlarm", state.fireAlarm);
        updateBool(driver, "cmdValveOpen", false);
        updateBool(driver, "cmdValveClose", false);
        updateBool(driver, "cmdPzkReset", false);
        updateBool(driver, "cmdGasTrip", false);
        updateStatus(driver);
    }

    static void pollRumb(DeviceDriver.DriverObject driver, RumbState state) {
        boolean close = readBool(driver, "cmdBreakerClose", false);
        boolean open = readBool(driver, "cmdBreakerOpen", false);
        if (close && state.interlockOk && !state.emergencyStop) {
            state.breakerClosed = true;
        }
        if (open) {
            state.breakerClosed = false;
        }

        updateBool(driver, "breakerClosed", state.breakerClosed);
        updateBool(driver, "breakerPosition", state.breakerClosed);
        updateBool(driver, "cartPosition", true);
        updateBool(driver, "emergencyStop", state.emergencyStop);
        updateBool(driver, "terminalFault", false);
        updateBool(driver, "groundingSwitchClosed", !state.breakerClosed);
        updateBool(driver, "interlockOk", state.interlockOk);
        updateBool(driver, "circuitFault", false);
        updateBool(driver, "cmdBreakerClose", false);
        updateBool(driver, "cmdBreakerOpen", false);
        updateStatus(driver);
    }

    static void pollDgu(DeviceDriver.DriverObject driver, DguState state) {
        boolean start = readBool(driver, "cmdStart", false);
        boolean stop = readBool(driver, "cmdStop", false);
        if (start) {
            state.running = true;
        }
        if (stop) {
            state.running = false;
        }

        updateBool(driver, "running", state.running);
        updateBool(driver, "batteryCharging", !state.running);
        updateMeas(driver, "fuelLevelPct", Math.max(20, state.fuelLevel - (state.running ? 0.01 : 0)), "%");
        updateMeas(driver, "coolantTemp", state.running ? 82 : 25, "C");
        updateBool(driver, "cmdStart", false);
        updateBool(driver, "cmdStop", false);
        if (state.running) {
            state.fuelLevel -= 0.01;
        }
        updateStatus(driver);
    }

    static void pollLoad(DeviceDriver.DriverObject driver, LoadState state) {
        double setpoint = readDouble(driver, "loadSetpointPct", state.loadPct);
        if (readBool(driver, "cmdSetLoad", false)) {
            state.loadPct = Math.max(0, Math.min(100, setpoint));
        }
        double loadFactor = state.loadPct / 100.0;
        double ratedKw = 1500;
        double p = ratedKw * loadFactor;
        double q = p * 0.12;
        double s = Math.sqrt(p * p + q * q);
        double freq = 50.0 - (1.0 - loadFactor) * 0.05;
        double voltage = 400 + loadFactor * 10;

        updateMeas(driver, "activePowerKw", p, "kW");
        updateMeas(driver, "reactivePowerKvar", q, "kVAr");
        updateMeas(driver, "apparentPowerKva", s, "kVA");
        updateMeas(driver, "frequencyHz", freq, "Hz");
        updateMeas(driver, "currentA", p / (voltage * 1.732 * 0.9) * 1000, "A");
        updateMeas(driver, "currentB", p / (voltage * 1.732 * 0.9) * 1000 * 0.98, "A");
        updateMeas(driver, "currentC", p / (voltage * 1.732 * 0.9) * 1000 * 1.02, "A");
        updateMeas(driver, "voltageAB", voltage * 1.732, "V");
        updateMeas(driver, "voltageBC", voltage * 1.732 * 0.99, "V");
        updateMeas(driver, "voltageCA", voltage * 1.732 * 1.01, "V");
        updateMeas(driver, "voltageA", voltage, "V");
        updateMeas(driver, "voltageB", voltage * 0.99, "V");
        updateMeas(driver, "voltageC", voltage * 1.01, "V");
        updateMeas(driver, "loadSetpointPct", state.loadPct, "%");
        updateBool(driver, "cmdSetLoad", false);
        updateStatus(driver);
    }

    static void updateMeas(DeviceDriver.DriverObject driver, String name, double value, String unit) {
        driver.updateVariable(name, DataRecord.single(MEASUREMENT, Map.of("value", value, "unit", unit)));
    }

    static void updateBool(DeviceDriver.DriverObject driver, String name, boolean value) {
        driver.updateVariable(name, DataRecord.single(BOOL, Map.of("value", value)));
    }

    static void updateInt(DeviceDriver.DriverObject driver, String name, int value) {
        driver.updateVariable(name, DataRecord.single(INT, Map.of("value", value)));
    }

    static void updateStatus(DeviceDriver.DriverObject driver) {
        driver.updateVariable("status", DataRecord.single(STATUS, Map.of(
                "online", true,
                "lastSeen", Instant.now().toString()
        )));
    }

    private static double readDouble(DeviceDriver.DriverObject driver, String name, double fallback) {
        return driver.getVariable(name)
                .map(record -> {
                    Object raw = record.firstRow().get("value");
                    if (raw instanceof Number number) {
                        return number.doubleValue();
                    }
                    if (raw != null) {
                        try {
                            return Double.parseDouble(raw.toString());
                        } catch (NumberFormatException ignored) {
                            return fallback;
                        }
                    }
                    return fallback;
                })
                .orElse(fallback);
    }

    private static boolean readBool(DeviceDriver.DriverObject driver, String name, boolean fallback) {
        return driver.getVariable(name)
                .map(record -> {
                    Object raw = record.firstRow().get("value");
                    if (raw instanceof Boolean bool) {
                        return bool;
                    }
                    if (raw != null) {
                        return Boolean.parseBoolean(raw.toString());
                    }
                    return fallback;
                })
                .orElse(fallback);
    }

    static final class GpuState {
        final long startedAt = System.currentTimeMillis();
        double loadPct;
        double targetLoadPct = 70;
        double runningHours;
        double energyKwh;
        double reactiveEnergyKvarh;
        int startCount;
        boolean wasRunning;
    }

    static final class GrpbState {
        double valveOpenPct = 80;
        double gasVolume;
        boolean pzkTripped;
        boolean gasLeak;
        boolean fireAlarm;
    }

    static final class RumbState {
        boolean breakerClosed = true;
        boolean emergencyStop;
        boolean interlockOk = true;
    }

    static final class DguState {
        boolean running;
        double fuelLevel = 85;
    }

    static final class LoadState {
        double loadPct = 35;
    }
}
