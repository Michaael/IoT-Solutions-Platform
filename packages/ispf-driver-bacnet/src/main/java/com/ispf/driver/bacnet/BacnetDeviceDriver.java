package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.cache.RemoteEntityCachePolicy;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.Unsigned16;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.RequestUtils;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BACnet/IP driver — reads object properties via BACnet4J.
 * <p>
 * Point mapping: {@code objectType:instance:property} e.g. {@code analog-input:1:present-value}.
 */
public class BacnetDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "bacnet",
            "BACnet/IP Driver",
            "0.1.0",
            "Polls BACnet/IP devices and maps object properties to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "47808",
                    "localDeviceId", "1234",
                    "remoteDeviceId", "1001",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bacnetValue")
            .field("value", FieldType.STRING)
            .field("property", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private LocalDevice localDevice;
    private RemoteDevice remoteDevice;
    private String host = "127.0.0.1";
    private String bindAddress = "0.0.0.0";
    private int port = 47808;
    private int localDeviceId = 1234;
    private int remoteDeviceId = 1001;
    private int timeoutMs = 5000;
    private int bindPort = -1;
    private final Map<String, BacnetPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("bindAddress", value -> bindAddress = value.trim());
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("localDeviceId", value -> localDeviceId = Integer.parseInt(value));
        readConfig("remoteDeviceId", value -> remoteDeviceId = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
        readConfig("bindPort", value -> bindPort = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            int networkPort = bindPort >= 0 ? bindPort : port;
            IpNetwork network = new IpNetworkBuilder()
                    .withLocalBindAddress(bindAddress)
                    .withBroadcast("255.255.255.255", 24)
                    .withPort(networkPort)
                    .withReuseAddress(true)
                    .build();
            localDevice = new LocalDevice(localDeviceId, new DefaultTransport(network));
            localDevice.initialize();
            Address remoteAddress = toIpAddress(host, port);
            remoteDevice = localDevice.getCachedRemoteDevice(remoteDeviceId);
            if (remoteDevice != null) {
                localDevice.updateRemoteDevice(remoteDeviceId, remoteAddress);
                remoteDevice = localDevice.getCachedRemoteDevice(remoteDeviceId);
            } else {
                remoteDevice = new RemoteDevice(localDevice, remoteDeviceId, remoteAddress);
                localDevice.getRemoteDeviceCache().putEntity(
                        remoteDeviceId,
                        remoteDevice,
                        RemoteEntityCachePolicy.NEVER_EXPIRE
                );
            }
            connected = true;
            driverObject.log(
                    DriverLogLevel.INFO,
                    "Connected to BACnet device " + remoteDeviceId + " at " + host + ":" + port
            );
        } catch (Exception e) {
            connected = false;
            terminateLocalDevice();
            throw new DriverException("BACnet connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        remoteDevice = null;
        terminateLocalDevice();
    }

    @Override
    public boolean isConnected() {
        return connected && localDevice != null && remoteDevice != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            BacnetPoint point = BacnetPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        BacnetPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        if (!isWritable(point)) {
            throw new DriverException("BACnet object type is read-only: " + point.objectType());
        }
        try {
            ObjectIdentifier objectId = new ObjectIdentifier(point.objectType(), point.instance());
            Encodable encoded = encodeWriteValue(point, value);
            RequestUtils.writeProperty(localDevice, remoteDevice, objectId, point.property(), encoded);
            driverObject.updateVariable(pointId, readPoint(point));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("BACnet write failed for point " + pointId, e);
        }
    }

    private static boolean isWritable(BacnetPoint point) {
        ObjectType type = point.objectType();
        if (type.equals(ObjectType.analogInput)
                || type.equals(ObjectType.binaryInput)
                || type.equals(ObjectType.multiStateInput)) {
            return false;
        }
        return point.property().equals(PropertyIdentifier.presentValue);
    }

    private static Encodable encodeWriteValue(BacnetPoint point, DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("raw");
        if (raw == null) {
            raw = value.firstRow().get("value");
        }
        if (raw == null) {
            throw new DriverException("BACnet write requires value or raw field");
        }
        ObjectType type = point.objectType();
        if (type.equals(ObjectType.analogOutput) || type.equals(ObjectType.analogValue)) {
            return new Real((float) extractDouble(raw));
        }
        if (type.equals(ObjectType.binaryOutput) || type.equals(ObjectType.binaryValue)) {
            return extractBoolean(raw) ? BinaryPV.active : BinaryPV.inactive;
        }
        if (type.equals(ObjectType.multiStateOutput) || type.equals(ObjectType.multiStateValue)) {
            return new UnsignedInteger((int) extractDouble(raw));
        }
        throw new DriverException("Unsupported BACnet write type: " + type);
    }

    private static double extractDouble(Object raw) throws DriverException {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new DriverException("BACnet write requires numeric value: " + raw, e);
        }
    }

    private static boolean extractBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(raw).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "on".equals(text) || "active".equals(text);
    }

    private DataRecord readPoint(BacnetPoint point) throws DriverException {
        try {
            ObjectIdentifier objectId = new ObjectIdentifier(point.objectType(), point.instance());
            Encodable rawValue = RequestUtils.getProperty(
                    localDevice,
                    remoteDevice,
                    objectId,
                    point.property()
            );
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", rawValue != null ? rawValue.toString() : "",
                    "property", PropertyIdentifier.nameForId(point.property().intValue())
            ));
        } catch (BACnetException e) {
            throw new DriverException("BACnet read failed for " + point, e);
        }
    }

    private static Address toIpAddress(String host, int port) throws Exception {
        byte[] ip = InetAddress.getByName(host).getAddress();
        byte[] mac = new byte[] {
                ip[0], ip[1], ip[2], ip[3],
                (byte) (port >> 8),
                (byte) port
        };
        return new Address(new Unsigned16(0), new OctetString(mac));
    }

    private void terminateLocalDevice() {
        if (localDevice != null) {
            try {
                localDevice.terminate();
            } catch (Exception ignored) {
                // best effort
            }
            localDevice = null;
        }
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
