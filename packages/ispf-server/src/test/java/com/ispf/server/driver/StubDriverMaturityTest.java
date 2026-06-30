package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StubDriverMaturityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ethernet-ip",
            "opc-da",
            "opc-bridge",
            "corba",
            "vmware",
            "smi-s"
    })
    void promotedStubDriversAreBeta(String driverId) {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve(driverId), driverId);
    }

    @Test
    void noRemainingBl26StubsInRegistry() {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("ethernet-ip"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("opc-da"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("opc-bridge"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("corba"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("vmware"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("smi-s"));
    }
}
