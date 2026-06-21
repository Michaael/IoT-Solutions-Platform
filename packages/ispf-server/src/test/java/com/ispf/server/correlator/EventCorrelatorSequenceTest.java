package com.ispf.server.correlator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventCorrelatorSequenceTest {

    @Autowired
    private EventCorrelatorService correlatorService;

    @Test
    @Transactional
    void sequencePatternTriggersOnlyAfterFirstThenSecondEvent() {
        EventCorrelator created = correlatorService.create(new EventCorrelatorService.CreateCorrelatorRequest(
                "Test sequence",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.SEQUENCE,
                "eventA",
                "eventB",
                60,
                1,
                0,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                true
        ));

        correlatorService.processEventFired("root.platform.devices.demo-sensor-01", "eventB");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.devices.demo-sensor-01", "eventA");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.devices.demo-sensor-01", "eventB");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }

    @Test
    @Transactional
    void sequenceDoesNotTriggerWhenSecondEventOutsideWindow() {
        EventCorrelator created = correlatorService.create(new EventCorrelatorService.CreateCorrelatorRequest(
                "Short window sequence",
                "root.test.device",
                CorrelatorPatternType.SEQUENCE,
                "alpha",
                "beta",
                1,
                1,
                0,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                true
        ));

        correlatorService.processEventFired("root.test.device", "alpha");
        try {
            Thread.sleep(1100L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        correlatorService.processEventFired("root.test.device", "beta");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();
    }
}
