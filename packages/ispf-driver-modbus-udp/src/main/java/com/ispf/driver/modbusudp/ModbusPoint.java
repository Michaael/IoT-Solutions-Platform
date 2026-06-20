package com.ispf.driver.modbusudp;

import com.ispf.driver.DriverException;

/**
 * Parsed Modbus point reference from mapping string {@code slaveId:type:address[:count]}.
 */
record ModbusPoint(int slaveId, RegisterType type, int address, int count) {

    enum RegisterType {
        HOLDING, INPUT, COIL, DISCRETE
    }

    static ModbusPoint parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 3) {
            throw new DriverException("Invalid Modbus mapping (expected slaveId:type:address): " + mapping);
        }
        try {
            int slaveId = Integer.parseInt(parts[0].trim());
            RegisterType type = RegisterType.valueOf(parts[1].trim().toUpperCase());
            int address = Integer.parseInt(parts[2].trim());
            int count = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 1;
            return new ModbusPoint(slaveId, type, address, count);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid Modbus mapping: " + mapping, e);
        }
    }
}
