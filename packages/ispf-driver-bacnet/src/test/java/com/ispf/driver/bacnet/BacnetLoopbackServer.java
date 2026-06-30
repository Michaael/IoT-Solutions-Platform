package com.ispf.driver.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;

/**
 * In-process BACnet/IP device for loopback driver tests.
 */
final class BacnetLoopbackServer implements AutoCloseable {

    static final String LOOPBACK_HOST = "127.0.0.1";

    private final LocalDevice localDevice;

    BacnetLoopbackServer(int deviceId, int port, float initialValue) throws Exception {
        IpNetwork network = new IpNetworkBuilder()
                .withLocalBindAddress(LOOPBACK_HOST)
                .withSubnet("127.0.0.0", 8)
                .withBroadcast("127.0.0.255", 8)
                .withPort(port)
                .withReuseAddress(true)
                .build();
        localDevice = new LocalDevice(deviceId, new DefaultTransport(network));
        localDevice.initialize();
        new AnalogValueObject(
                localDevice,
                1,
                "setpoint",
                initialValue,
                EngineeringUnits.noUnits,
                false
        ).supportWritable();
    }

    LocalDevice localDevice() {
        return localDevice;
    }

    @Override
    public void close() {
        if (localDevice != null) {
            try {
                localDevice.terminate();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
