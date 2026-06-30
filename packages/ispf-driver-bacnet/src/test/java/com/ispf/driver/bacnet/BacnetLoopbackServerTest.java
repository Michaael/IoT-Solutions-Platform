package com.ispf.driver.bacnet;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BacnetLoopbackServerTest {

    @Test
    void exposesWritableAnalogValueObject() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        try (BacnetLoopbackServer server = new BacnetLoopbackServer(1001, port, 18.5f)) {
            assertEquals(1001, server.localDevice().getInstanceNumber());
            assertFalse(server.localDevice().getLocalObjects().isEmpty());
        }
    }
}
