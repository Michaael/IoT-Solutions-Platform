package com.ispf.driver.cwmp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TR-069/CWMP ACS client stub — POSTs Inform SOAP envelopes to an ACS URL.
 */
public class CwmpDeviceDriver implements DeviceDriver {

    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "<Name>([^<]+)</Name>\\s*<Value[^>]*>([^<]*)</Value>",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("cwmpValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "cwmp",
            "TR-069 CWMP Client Driver",
            "0.1.0",
            "Posts CWMP Inform SOAP to an ACS and maps parameter values from the response",
            "ISPF",
            Map.of(
                    "acsUrl", "http://127.0.0.1:7547/",
                    "deviceId", "000000-000000000000",
                    "periodicInformInterval", "300",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "300000"
            )
    );

    private DriverObject driverObject;
    private String acsUrl = "http://127.0.0.1:7547/";
    private String deviceId = "000000-000000000000";
    private long periodicInformInterval = 300;
    private long timeoutMs = 5000;
    private HttpClient client;
    private int lastStatusCode = -1;
    private volatile boolean lastInformOk;
    private final Map<String, String> lastParameters = new ConcurrentHashMap<>();
    private final Map<String, CwmpPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "acsUrl" -> acsUrl = value.trim();
            case "deviceId" -> deviceId = value.trim();
            case "periodicInformInterval" -> periodicInformInterval = Long.parseLong(value.trim());
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "CWMP client ready (acsUrl=" + acsUrl + ", deviceId=" + deviceId + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        lastParameters.clear();
        lastInformOk = false;
        lastStatusCode = -1;
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        sendInform();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            CwmpPoint point = CwmpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("CWMP driver is read-only in v0.1");
    }

    private void sendInform() throws DriverException {
        try {
            String envelope = buildInformEnvelope();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(acsUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            lastInformOk = lastStatusCode >= 200 && lastStatusCode < 300;
            lastParameters.clear();
            if (response.body() != null) {
                parseParameters(response.body(), lastParameters);
            }
        } catch (Exception e) {
            lastInformOk = false;
            throw new DriverException("CWMP Inform failed", e);
        }
    }

    private DataRecord readPoint(CwmpPoint point) {
        if (point.isConnectedPoint()) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(lastInformOk),
                    "statusCode", lastStatusCode
            ));
        }
        String value = lastParameters.getOrDefault(point.parameterName(), "");
        if (value.isEmpty()) {
            for (Map.Entry<String, String> entry : lastParameters.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(point.parameterName())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }

    static void parseParameters(String xml, Map<String, String> target) {
        Matcher matcher = PARAM_PATTERN.matcher(xml);
        while (matcher.find()) {
            target.put(matcher.group(1).trim(), matcher.group(2).trim());
        }
    }

    private String buildInformEnvelope() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                  <soap-env:Header>
                    <cwmp:ID soap-env:mustUnderstand="1">1</cwmp:ID>
                  </soap-env:Header>
                  <soap-env:Body>
                    <cwmp:Inform>
                      <DeviceId>
                        <SerialNumber>%s</SerialNumber>
                      </DeviceId>
                      <Event soap-env:arrayType="cwmp:EventStruct[1]">
                        <EventStruct>
                          <EventCode>2 PERIODIC</EventCode>
                          <CommandKey></CommandKey>
                        </EventStruct>
                      </Event>
                      <MaxEnvelopes>1</MaxEnvelopes>
                      <CurrentTime>2020-01-01T00:00:00Z</CurrentTime>
                      <RetryCount>0</RetryCount>
                      <ParameterList soap-env:arrayType="cwmp:ParameterValueStruct[1]">
                        <ParameterValueStruct>
                          <Name>Device.DeviceInfo.SoftwareVersion</Name>
                          <Value>1.0</Value>
                        </ParameterValueStruct>
                      </ParameterList>
                    </cwmp:Inform>
                  </soap-env:Body>
                </soap-env:Envelope>
                """.formatted(deviceId);
    }
}
