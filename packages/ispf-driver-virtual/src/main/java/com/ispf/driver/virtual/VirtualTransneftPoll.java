package com.ispf.driver.virtual;

import com.ispf.driver.DeviceDriver;

import java.time.Instant;
import java.util.Map;

/**
 * Synthetic telemetry for Transneft Omsk RDP tank farm demo (virtual driver profiles).
 */
final class VirtualTransneftPoll {

    private VirtualTransneftPoll() {
    }

    static final class TankState {
        boolean initialized;
        double levelMm;
        double rateMmPerHour;
        final long startedAt = System.currentTimeMillis();
    }

    static final class RdpHubState {
        final long startedAt = System.currentTimeMillis();
    }

    static void pollTank(
            DeviceDriver.DriverObject driver,
            TankState state,
            int tankIndex,
            double initialLevelMm,
            double rateBiasMmPerHour,
            double maxLevelMm
    ) {
        if (!state.initialized) {
            state.levelMm = initialLevelMm;
            state.rateMmPerHour = rateBiasMmPerHour;
            state.initialized = true;
        }

        long elapsedSec = (System.currentTimeMillis() - state.startedAt) / 1000;
        double wave = Math.sin(elapsedSec / 45.0 + tankIndex * 0.7) * 35;
        state.rateMmPerHour = rateBiasMmPerHour + wave;

        double deltaMm = state.rateMmPerHour / 3600.0;
        state.levelMm = Math.max(500, Math.min(maxLevelMm - 200, state.levelMm + deltaMm));

        VirtualTecPoll.updateMeas(driver, "fillLevelMm", state.levelMm, "mm");
        VirtualTecPoll.updateMeas(driver, "rateMmPerHour", state.rateMmPerHour, "mm/h");
        VirtualTecPoll.updateMeas(driver, "maxLevelMm", maxLevelMm, "mm");
        VirtualTecPoll.updateBool(driver, "valveOpen", Math.abs(state.rateMmPerHour) > 5);
        VirtualTecPoll.updateStatus(driver);
    }

    static void pollRdpHub(DeviceDriver.DriverObject driver, RdpHubState state) {
        long elapsedSec = (System.currentTimeMillis() - state.startedAt) / 1000;
        double flowBase = 1180 + Math.sin(elapsedSec / 50.0) * 80;
        double pressure = 0.78 + Math.sin(elapsedSec / 70.0 + 1) * 0.06;
        double temp = 11 + Math.sin(elapsedSec / 90.0 + 2) * 2.5;
        double deltaP = 0.03 + Math.abs(Math.sin(elapsedSec / 35.0)) * 0.025;

        VirtualTecPoll.updateMeas(driver, "linePressureMpa", pressure, "MPa");
        VirtualTecPoll.updateMeas(driver, "lineFlowM3h", flowBase, "m³/h");
        VirtualTecPoll.updateMeas(driver, "lineTempC", temp, "°C");
        VirtualTecPoll.updateMeas(driver, "deltaPressureMpa", deltaP, "MPa");
        VirtualTecPoll.updateMeas(driver, "totalVolumeM3", 81000 + Math.sin(elapsedSec / 120.0) * 1200, "m³");
        VirtualTecPoll.updateStatus(driver);
    }
}
