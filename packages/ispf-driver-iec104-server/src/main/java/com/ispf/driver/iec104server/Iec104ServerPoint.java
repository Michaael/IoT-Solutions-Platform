package com.ispf.driver.iec104server;

import com.ispf.driver.DriverException;

/**
 * Parsed IEC 60870-5-104 server point from mapping string {@code ioa}.
 */
record Iec104ServerPoint(int ioa) {

    static Iec104ServerPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("IEC104 server mapping requires ioa: " + mapping);
        }
        try {
            return new Iec104ServerPoint(Integer.parseInt(mapping.trim()));
        } catch (NumberFormatException e) {
            throw new DriverException("Invalid IEC104 server ioa: " + mapping, e);
        }
    }
}
