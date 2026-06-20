package com.ispf.driver.ethernetip;

import com.ispf.driver.DriverException;

/**
 * Parsed EtherNet/IP point reference from mapping string {@code tagPath}.
 */
record EthernetIpPoint(String tagPath) {

    static EthernetIpPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("EtherNet/IP mapping requires tagPath: " + mapping);
        }
        return new EthernetIpPoint(mapping.trim());
    }
}
