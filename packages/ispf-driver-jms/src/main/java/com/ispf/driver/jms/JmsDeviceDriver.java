package com.ispf.driver.jms;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMS driver — Apache ActiveMQ client for queue/topic consume and queue browse depth.
 */
public class JmsDeviceDriver implements DeviceDriver {

    private static final DataSchema MESSAGE_SCHEMA = DataSchema.builder("jmsMessage")
            .field("value", FieldType.STRING)
            .field("depth", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "jms",
            "JMS ActiveMQ Driver",
            "0.1.0",
            "Consumes JMS messages or browses queue depth via Apache ActiveMQ",
            "ISPF",
            Map.of(
                    "brokerUrl", "tcp://localhost:61616",
                    "destination", "ispf.queue",
                    "destinationType", "queue",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            )
    );

    private DriverObject driverObject;
    private String brokerUrl = "tcp://localhost:61616";
    private String destination = "ispf.queue";
    private String destinationType = "queue";
    private long timeoutMs = 5000;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private final Map<String, JmsPoint> points = new ConcurrentHashMap<>();
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
            case "brokerUrl" -> brokerUrl = value.trim();
            case "destination", "queue", "topic" -> destination = value.trim();
            case "destinationType" -> destinationType = value.trim().toLowerCase();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "JMS connected (broker=" + brokerUrl + ", destination=" + destination + ")");
        } catch (JMSException e) {
            throw new DriverException("JMS connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeQuietly(session);
        session = null;
        closeQuietly(connection);
        connection = null;
        connectionFactory = null;
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null && session != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            JmsPoint point = JmsPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), execute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("JMS driver is read-only in v0.1");
    }

    private DataRecord execute(JmsPoint point) throws DriverException {
        try {
            if (point.mode() == JmsPoint.JmsMode.CONSUME) {
                return consumeMessage();
            }
            if (isTopicDestination()) {
                throw new DriverException("Browse depth is only supported for queue destinations");
            }
            return browseDepth(point.browseDepth());
        } catch (JMSException e) {
            throw new DriverException("JMS operation failed", e);
        }
    }

    private DataRecord consumeMessage() throws JMSException {
        try (MessageConsumer consumer = session.createConsumer(createDestination())) {
            Message message = consumer.receive(timeoutMs);
            String value = "";
            if (message instanceof TextMessage textMessage) {
                value = textMessage.getText() == null ? "" : textMessage.getText();
            } else if (message != null) {
                value = message.toString();
            }
            return DataRecord.single(MESSAGE_SCHEMA, Map.of("value", value, "depth", 0));
        }
    }

    private DataRecord browseDepth(int maxDepth) throws JMSException {
        Queue queue = session.createQueue(destination);
        try (QueueBrowser browser = session.createBrowser(queue)) {
            Enumeration<?> messages = browser.getEnumeration();
            int depth = 0;
            while (messages.hasMoreElements()) {
                depth++;
                if (depth >= maxDepth) {
                    break;
                }
                messages.nextElement();
            }
            while (messages.hasMoreElements()) {
                depth++;
                messages.nextElement();
            }
            return DataRecord.single(MESSAGE_SCHEMA, Map.of("value", String.valueOf(depth), "depth", depth));
        }
    }

    private jakarta.jms.Destination createDestination() throws JMSException {
        if (isTopicDestination()) {
            return session.createTopic(destination);
        }
        return session.createQueue(destination);
    }

    private boolean isTopicDestination() {
        return "topic".equalsIgnoreCase(destinationType);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
