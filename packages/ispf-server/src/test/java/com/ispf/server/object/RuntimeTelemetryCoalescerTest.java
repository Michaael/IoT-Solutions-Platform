package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuntimeTelemetryCoalescerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MqttGatewayIngressDispatchService gatewayIngressDispatch;

    private RuntimeTelemetryCoalescer coalescer;

    @AfterEach
    void tearDown() {
        if (coalescer != null) {
            coalescer.shutdown();
        }
    }

    @Test
    void coalescesMultipleUpdatesIntoSingleEvent() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 2.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 3.0));

        verifyNoInteractions(eventPublisher);

        coalescer.flushNow();

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ObjectChangeEvent event = captor.getValue();
        assertThat(event.path()).isEqualTo("root.dev.sensor");
        assertThat(event.variableName()).isEqualTo("temperature");
        assertThat(event.telemetry()).isTrue();
        assertThat(event.automationEligible()).isTrue();
    }

    @Test
    void marksTelemetryOnlyDevicesAsNotAutomationEligible() {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setEnabled(false);
        DeviceTelemetryPolicyService policyService = org.mockito.Mockito.mock(DeviceTelemetryPolicyService.class);
        org.mockito.Mockito.when(policyService.automationEligible("root.dev.sensor")).thenReturn(false);
        coalescer = new RuntimeTelemetryCoalescer(properties, policyService, eventPublisher, gatewayIngressDispatch);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().automationEligible()).isFalse();
    }

    @Test
    void skipsUnchangedValues() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        DataRecord value = record(schema, 42.0);

        coalescer.recordUpdate("root.dev.sensor", "temperature", value);
        coalescer.flushNow();
        clearInvocations(eventPublisher);

        coalescer.recordUpdate("root.dev.sensor", "temperature", value);
        coalescer.flushNow();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishesDistinctVariablesSeparately() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("metric").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));
        coalescer.recordUpdate("root.dev.sensor", "humidity", record(schema, 55.0));
        coalescer.flushNow();

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ObjectChangeEvent::variableName)
                .containsExactlyInAnyOrder("temperature", "humidity");
    }

    @Test
    void valuesEqualComparesRowContent() {
        DataSchema schema = DataSchema.builder("metric").field("value", FieldType.DOUBLE).build();
        DataRecord left = record(schema, 10.0);
        DataRecord right = record(schema, 10.0);
        DataRecord different = record(schema, 11.0);

        assertThat(RuntimeTelemetryCoalescer.valuesEqual(left, right)).isTrue();
        assertThat(RuntimeTelemetryCoalescer.valuesEqual(left, different)).isFalse();
        assertThat(RuntimeTelemetryCoalescer.valuesEqual(null, left)).isFalse();
    }

    @Test
    void coalescesIngressTopicsOnSeparateLanes() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "1.0"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00002/temperature", "raw", "2.0"))
        );
        coalescer.flushNow();

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ObjectChangeEvent::variableName)
                .containsOnly("lastIngress");
    }

    @Test
    void coalescesIngressPayloadsOnSeparateLanesWhenEnabled() {
        coalescer = newCoalescer(true, 1_000, false, true);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0001\",\"temperature\":\"1\"}"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0002\",\"temperature\":\"2\"}"))
        );
        coalescer.flushNow();

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ObjectChangeEvent::variableName)
                .containsOnly("lastIngress");
    }

    @Test
    void coalescesSameTopicWithoutPayloadLanesIntoOneLane() {
        clearInvocations(eventPublisher);
        coalescer = newCoalescer(true, 1_000, false, false);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0001\",\"temperature\":\"1\"}"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0002\",\"temperature\":\"2\"}"))
        );
        coalescer.flushNow();

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().variableName()).isEqualTo("lastIngress");
    }

    @Test
    void parallelGatewayDispatchSkipsBindingEvent() {
        coalescer = newCoalescer(true, 1_000);
        org.mockito.Mockito.when(gatewayIngressDispatch.tryScheduleDispatch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("lastIngress"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(true);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "3.0"))
        );
        coalescer.flushNow();
        verifyNoInteractions(eventPublisher);
    }

    private RuntimeTelemetryCoalescer newCoalescer(boolean enabled, long coalesceMs) {
        return newCoalescer(enabled, coalesceMs, false, false);
    }

    private RuntimeTelemetryCoalescer newCoalescer(
            boolean enabled,
            long coalesceMs,
            boolean ingressTopicLanes,
            boolean ingressPayloadLanes
    ) {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setEnabled(enabled);
        properties.setCoalesceMs(coalesceMs);
        DeviceTelemetryPolicyService policyService = org.mockito.Mockito.mock(DeviceTelemetryPolicyService.class);
        org.mockito.Mockito.when(policyService.coalesceMs(org.mockito.ArgumentMatchers.anyString())).thenReturn(coalesceMs);
        org.mockito.Mockito.when(policyService.automationEligible(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        org.mockito.Mockito.when(policyService.ingressTopicLanes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressTopicLanes);
        org.mockito.Mockito.when(policyService.ingressPayloadLanes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressPayloadLanes);
        org.mockito.Mockito.when(policyService.parallelIngressDispatch(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressTopicLanes || ingressPayloadLanes);
        return new RuntimeTelemetryCoalescer(properties, policyService, eventPublisher, gatewayIngressDispatch);
    }

    private static DataRecord record(DataSchema schema, double value) {
        return DataRecord.single(schema, Map.of("value", value));
    }
}
