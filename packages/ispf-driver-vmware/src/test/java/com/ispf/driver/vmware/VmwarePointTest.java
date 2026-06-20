package com.ispf.driver.vmware;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VmwarePointTest {

    @Test
    void parsesPropertyPath() {
        VmwarePoint point = VmwarePoint.parse("about.version");
        assertEquals("about.version", point.propertyPath());
        assertTrue(!point.isConnectedPoint());
    }

    @Test
    void parsesConnectedStatus() {
        VmwarePoint point = VmwarePoint.parse("connected");
        assertTrue(point.isConnectedPoint());
        assertEquals("connected", point.propertyPath());
    }

    @Test
    void parsesPropertiesFromSoapResponse() {
        String xml = """
                <returnval>
                  <about>
                    <version>8.0.1</version>
                  </about>
                </returnval>
                """;
        Map<String, String> props = new HashMap<>();
        VmwareDeviceDriver.parseProperties(xml, props);
        assertEquals("8.0.1", props.get("version"));
    }
}
