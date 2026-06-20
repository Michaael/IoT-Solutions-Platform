package com.ispf.driver.modbusudp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModbusPointTest {

    @Test
    void parsesCoilMapping() throws Exception {
        ModbusPoint point = ModbusPoint.parse("2:COIL:0");
        assertEquals(2, point.slaveId());
        assertEquals(ModbusPoint.RegisterType.COIL, point.type());
        assertEquals(0, point.address());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> ModbusPoint.parse("1:HOLDING"));
    }
}
