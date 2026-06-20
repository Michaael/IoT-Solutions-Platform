package com.ispf.driver.dlms;

import com.ispf.driver.DriverException;

/**
 * Parsed DLMS/COSEM point from mapping string {@code logicalDevice:obis}.
 * Example: {@code 1:1.0.1.8.0.255}.
 */
record DlmsPoint(int logicalDevice, String obis) {

    static DlmsPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("DLMS mapping requires logicalDevice:obis: " + mapping);
        }
        int separator = mapping.indexOf(':');
        if (separator <= 0 || separator >= mapping.length() - 1) {
            throw new DriverException("Invalid DLMS mapping (expected logicalDevice:obis): " + mapping);
        }
        try {
            int logicalDevice = Integer.parseInt(mapping.substring(0, separator).trim());
            String obis = mapping.substring(separator + 1).trim();
            if (obis.isBlank()) {
                throw new DriverException("Invalid DLMS OBIS code: " + mapping);
            }
            return new DlmsPoint(logicalDevice, obis);
        } catch (NumberFormatException e) {
            throw new DriverException("Invalid DLMS logical device: " + mapping, e);
        }
    }
}
