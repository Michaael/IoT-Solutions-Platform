package com.ispf.driver.opcuaserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.eclipse.milo.opcua.stack.server.security.ServerCertificateValidator;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Embedded OPC UA server driver — exposes mapped variables via Eclipse Milo 0.6.15.
 * <p>
 * Point mapping: {@code nodeId} e.g. {@code ns=2;s=Temperature} or bare identifier in configured namespace.
 */
public class OpcUaServerDeviceDriver implements DeviceDriver {

    private static final String APPLICATION_URI = "urn:ispf:driver:opcua-server";

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opcua-server",
            "OPC UA Server Driver",
            "0.1.0",
            "Hosts an embedded OPC UA server (Eclipse Milo) and maps node values to ISPF variables",
            "ISPF",
            Map.of(
                    "bindPort", "4840",
                    "namespace", "2",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcUaServerValue")
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .field("nodeId", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private OpcUaServer server;
    private IspfOpcUaNamespace namespace;
    private int bindPort = 4840;
    private int namespaceIndex = 2;
    private int timeoutMs = 5000;
    private final Map<String, OpcUaServerPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("bindPort", value -> bindPort = Integer.parseInt(value));
        readConfig("namespace", value -> namespaceIndex = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "bindPort" -> bindPort = Integer.parseInt(value.trim());
            case "namespace" -> namespaceIndex = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            File securityDir = Files.createTempDirectory("ispf-opcua-server-").toFile();
            KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            X509Certificate certificate = new SelfSignedCertificateBuilder(keyPair)
                    .setCommonName("ISPF OPC UA Server")
                    .setOrganization("ISPF")
                    .setApplicationUri(APPLICATION_URI)
                    .addDnsName("localhost")
                    .setValidityPeriod(Period.ofYears(3))
                    .build();

            DefaultCertificateManager certificateManager = new DefaultCertificateManager(keyPair, certificate);
            DefaultTrustListManager trustListManager = new DefaultTrustListManager(securityDir);
            ServerCertificateValidator certificateValidator = new DefaultServerCertificateValidator(trustListManager);

            EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress("0.0.0.0")
                    .setBindPort(bindPort)
                    .setHostname("localhost")
                    .setPath("/ispf")
                    .setCertificate(certificate)
                    .setSecurityPolicy(org.eclipse.milo.opcua.stack.core.security.SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)
                    .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
                    .addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME)
                    .build();

            OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                    .setApplicationUri(APPLICATION_URI)
                    .setApplicationName(LocalizedText.english("ISPF OPC UA Server"))
                    .setProductUri(APPLICATION_URI)
                    .setEndpoints(Set.of(endpoint))
                    .setCertificateManager(certificateManager)
                    .setTrustListManager(trustListManager)
                    .setCertificateValidator(certificateValidator)
                    .setIdentityValidator(new CompositeValidator(
                            AnonymousIdentityValidator.INSTANCE,
                            new UsernameIdentityValidator(true, authChallenge -> true)
                    ))
                    .setBuildInfo(new BuildInfo(
                            APPLICATION_URI,
                            "ISPF",
                            "OPC UA Server Driver",
                            OpcUaServer.SDK_VERSION,
                            "0.1.0",
                            DateTime.now()
                    ))
                    .build();

            server = new OpcUaServer(serverConfig);
            namespace = new IspfOpcUaNamespace(server);
            server.getAddressSpaceManager().register(namespace);
            server.startup().get(timeoutMs, TimeUnit.MILLISECONDS);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "OPC UA server listening on port " + bindPort);
        } catch (Exception e) {
            connected = false;
            shutdownServer();
            throw new DriverException("OPC UA server start failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        shutdownServer();
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected && server != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpcUaServerPoint point = OpcUaServerPoint.parse(entry.getValue(), namespaceIndex);
            points.put(entry.getKey(), point);
            namespace.ensureVariable(point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OpcUaServerPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        String newValue = extractString(value);
        namespace.writeValue(point.nodeId(), newValue);
        driverObject.updateVariable(pointId, readPoint(point));
    }

    private DataRecord readPoint(OpcUaServerPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", namespace.readValue(point.nodeId()),
                "quality", StatusCode.GOOD.toString(),
                "nodeId", point.nodeIdText()
        ));
    }

    private static String extractString(DataRecord value) {
        Object raw = value.firstRow().get("value");
        return raw == null ? "" : raw.toString();
    }

    private void shutdownServer() {
        if (server != null) {
            try {
                server.shutdown().get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // best effort
            }
            server = null;
            namespace = null;
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
