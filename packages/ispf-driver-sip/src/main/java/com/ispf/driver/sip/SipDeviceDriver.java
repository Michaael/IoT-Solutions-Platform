package com.ispf.driver.sip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP driver — OPTIONS ping or minimal REGISTER check via JAIN-SIP (jain-sip-ri).
 */
public class SipDeviceDriver implements DeviceDriver {

    private static final DataSchema SIP_SCHEMA = DataSchema.builder("sipResult")
            .field("reachable", FieldType.BOOLEAN)
            .field("statusCode", FieldType.INTEGER)
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "sip",
            "SIP Driver",
            "0.1.0",
            "SIP OPTIONS reachability or minimal UDP REGISTER check (JAIN-SIP / jain-sip-ri)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5060",
                    "username", "user",
                    "domain", "example.com",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5060;
    private String username = "user";
    private String domain = "example.com";
    private int timeoutMs = 5000;
    private SipStack sipStack;
    private final Map<String, SipPoint> points = new ConcurrentHashMap<>();
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
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "username" -> username = value.trim();
            case "domain" -> domain = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            SipFactory factory = SipFactory.getInstance();
            factory.setPathName("gov.nist");
            Properties props = new Properties();
            props.setProperty("javax.sip.STACK_NAME", "ispf-sip-" + UUID.randomUUID());
            props.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
            props.setProperty("gov.nist.javax.sip.DEBUG_LOG", "build/sip-debug.log");
            props.setProperty("gov.nist.javax.sip.SERVER_LOG", "build/sip-server.log");
            sipStack = factory.createSipStack(props);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "SIP stack ready for " + host + ":" + port);
        } catch (Exception e) {
            throw new DriverException("SIP stack init failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (sipStack != null) {
            sipStack.stop();
            sipStack = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && sipStack != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            SipPoint point = SipPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), check(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SIP driver is read-only in v0.1");
    }

    private DataRecord check(SipPoint point) throws DriverException {
        try {
            return switch (point.mode()) {
                case OPTIONS -> optionsCheck();
                case REGISTER -> registerCheck();
            };
        } catch (Exception e) {
            throw new DriverException("SIP check failed for " + point.mode(), e);
        }
    }

    private DataRecord optionsCheck() throws Exception {
        SipFactory factory = SipFactory.getInstance();
        MessageFactory messageFactory = factory.createMessageFactory();
        HeaderFactory headerFactory = factory.createHeaderFactory();
        AddressFactory addressFactory = factory.createAddressFactory();

        SipProvider provider = sipStack.createSipProvider(sipStack.createListeningPoint("0.0.0.0", 0, "udp"));
        String transport = "udp";
        String branch = "z9hG4bK-" + UUID.randomUUID();
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = headerFactory.createViaHeader("0.0.0.0", provider.getListeningPoint(transport).getPort(),
                transport, branch);
        viaHeaders.add(via);

        CallIdHeader callId = headerFactory.createCallIdHeader(UUID.randomUUID().toString());
        CSeqHeader cSeq = headerFactory.createCSeqHeader(1L, Request.OPTIONS);
        FromHeader from = headerFactory.createFromHeader(
                addressFactory.createAddress(addressFactory.createSipURI(username, domain)),
                UUID.randomUUID().toString());
        ToHeader to = headerFactory.createToHeader(
                addressFactory.createAddress(addressFactory.createSipURI(host, domain)), null);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(
                addressFactory.createSipURI(host, domain),
                Request.OPTIONS,
                callId,
                cSeq,
                from,
                to,
                viaHeaders,
                maxForwards
        );

        byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(host), port));
            byte[] buffer = new byte[4096];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            String responseText = new String(responsePacket.getData(), 0, responsePacket.getLength(),
                    StandardCharsets.UTF_8);
            int statusCode = parseStatusCode(responseText);
            boolean reachable = statusCode >= 200 && statusCode < 400;
            return DataRecord.single(SIP_SCHEMA, Map.of(
                    "reachable", reachable,
                    "statusCode", statusCode,
                    "value", responseText.trim()
            ));
        } finally {
            sipStack.deleteSipProvider(provider);
        }
    }

    private DataRecord registerCheck() throws Exception {
        String callId = UUID.randomUUID().toString();
        String branch = "z9hG4bK-" + UUID.randomUUID();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String request = "REGISTER sip:" + domain + " SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 0.0.0.0:5060;branch=" + branch + "\r\n"
                + "From: <sip:" + username + "@" + domain + ">;tag=" + tag + "\r\n"
                + "To: <sip:" + username + "@" + domain + ">\r\n"
                + "Call-ID: " + callId + "\r\n"
                + "CSeq: 1 REGISTER\r\n"
                + "Contact: <sip:" + username + "@0.0.0.0:5060>\r\n"
                + "Max-Forwards: 70\r\n"
                + "Expires: 60\r\n"
                + "Content-Length: 0\r\n\r\n";

        byte[] payload = request.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(host), port));
            byte[] buffer = new byte[4096];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            String responseText = new String(responsePacket.getData(), 0, responsePacket.getLength(),
                    StandardCharsets.UTF_8);
            int statusCode = parseStatusCode(responseText);
            boolean reachable = statusCode == Response.OK || statusCode == Response.UNAUTHORIZED
                    || statusCode == Response.PROXY_AUTHENTICATION_REQUIRED;
            return DataRecord.single(SIP_SCHEMA, Map.of(
                    "reachable", reachable,
                    "statusCode", statusCode,
                    "value", statusLabel(statusCode)
            ));
        }
    }

    private static int parseStatusCode(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return 0;
        }
        String firstLine = responseText.split("\\r?\\n", 2)[0].trim();
        String[] parts = firstLine.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String statusLabel(int statusCode) {
        return switch (statusCode) {
            case Response.OK -> "registered";
            case Response.UNAUTHORIZED, Response.PROXY_AUTHENTICATION_REQUIRED -> "challenge";
            case 0 -> "no-response";
            default -> "status-" + statusCode;
        };
    }

}
