package com.ispf.driver.modbusrtu;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.net.AbstractSerialConnection;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modbus RTU serial driver — reads/writes registers over RS-232/RS-485.
 * <p>
 * Point mapping format: {@code slaveId:registerType:address[:count]}
 */
public class ModbusRtuDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "modbus-rtu",
            "Modbus RTU Driver",
            "0.1.0",
            "Polls Modbus RTU slaves over serial and maps register values to ISPF variables",
            "ISPF",
            Map.of(
                    "serialPort", "COM1",
                    "baudRate", "9600",
                    "dataBits", "8",
                    "stopBits", "1",
                    "parity", "NONE",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema COIL_SCHEMA = DataSchema.builder("modbusCoil")
            .field("value", FieldType.BOOLEAN)
            .build();

    private DriverObject driverObject;
    private ModbusSerialMaster master;
    private String serialPort = "COM1";
    private int baudRate = 9600;
    private int dataBits = 8;
    private int stopBits = AbstractSerialConnection.ONE_STOP_BIT;
    private int parity = AbstractSerialConnection.NO_PARITY;
    private int timeoutMs = 3000;
    private final Map<String, ModbusPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("serialPort", value -> serialPort = value);
        readConfig("baudRate", value -> baudRate = Integer.parseInt(value));
        readConfig("dataBits", value -> dataBits = Integer.parseInt(value));
        readConfig("stopBits", value -> stopBits = parseStopBits(value));
        readConfig("parity", value -> parity = parseParity(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "serialPort" -> serialPort = value.trim();
            case "baudRate" -> baudRate = Integer.parseInt(value.trim());
            case "dataBits" -> dataBits = Integer.parseInt(value.trim());
            case "stopBits" -> stopBits = parseStopBits(value.trim());
            case "parity" -> parity = parseParity(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            SerialParameters params = new SerialParameters();
            params.setPortName(serialPort);
            params.setBaudRate(baudRate);
            params.setDatabits(dataBits);
            params.setStopbits(stopBits);
            params.setParity(parity);
            params.setEncoding(Modbus.SERIAL_ENCODING_RTU);
            params.setEcho(false);

            master = new ModbusSerialMaster(params);
            master.setTimeout(timeoutMs);
            master.connect();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to Modbus RTU on " + serialPort);
        } catch (Exception e) {
            connected = false;
            master = null;
            throw new DriverException("Modbus RTU connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (master != null) {
            master.disconnect();
            master = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && master != null && master.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            ModbusPoint point = ModbusPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        ModbusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        try {
            switch (point.type()) {
                case HOLDING -> {
                    long raw = extractNumeric(value);
                    master.writeSingleRegister(point.slaveId(), point.address(), new SimpleRegister((int) raw));
                }
                case COIL -> {
                    boolean coilValue = Boolean.TRUE.equals(value.firstRow().get("value"));
                    master.writeCoil(point.slaveId(), point.address(), coilValue);
                }
                case INPUT, DISCRETE -> throw new DriverException("Register type is read-only: " + point.type());
            }
            driverObject.updateVariable(pointId, readPoint(point));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("Modbus RTU write failed for point " + pointId, e);
        }
    }

    private DataRecord readPoint(ModbusPoint point) throws DriverException {
        try {
            return switch (point.type()) {
                case HOLDING -> {
                    Register register = master.readMultipleRegisters(point.slaveId(), point.address(), 1)[0];
                    yield toRegisterRecord(register.getValue());
                }
                case INPUT -> {
                    InputRegister register = master.readInputRegisters(point.slaveId(), point.address(), 1)[0];
                    yield toRegisterRecord(register.getValue());
                }
                case COIL -> {
                    boolean value = master.readCoils(point.slaveId(), point.address(), 1).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
                case DISCRETE -> {
                    boolean value = master.readInputDiscretes(point.slaveId(), point.address(), 1).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
            };
        } catch (Exception e) {
            throw new DriverException("Modbus RTU read failed at " + point, e);
        }
    }

    private static DataRecord toRegisterRecord(int raw) {
        return DataRecord.single(REGISTER_SCHEMA, Map.of(
                "raw", (long) raw,
                "value", (double) raw
        ));
    }

    private static long extractNumeric(DataRecord value) {
        Object raw = value.firstRow().get("raw");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Modbus write requires numeric raw/value field");
    }

    private static int parseStopBits(String value) {
        return switch (value.trim()) {
            case "2", "TWO" -> AbstractSerialConnection.TWO_STOP_BITS;
            default -> AbstractSerialConnection.ONE_STOP_BIT;
        };
    }

    private static int parseParity(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "EVEN" -> AbstractSerialConnection.EVEN_PARITY;
            case "ODD" -> AbstractSerialConnection.ODD_PARITY;
            default -> AbstractSerialConnection.NO_PARITY;
        };
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
