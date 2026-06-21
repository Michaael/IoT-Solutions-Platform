package com.ispf.server.federation;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes object-change notifications for federated proxy paths so WebSocket clients refresh.
 */
@Component
public class FederationWebSocketFanoutService {

    private final ApplicationEventPublisher eventPublisher;

    public FederationWebSocketFanoutService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void notifyFederatedPathUpdated(String federatedPath, String variableName) {
        if (federatedPath == null || federatedPath.isBlank()) {
            return;
        }
        if (variableName != null && !variableName.isBlank()) {
            eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(federatedPath, variableName));
        } else {
            eventPublisher.publishEvent(ObjectChangeEvent.of(ObjectChangeType.UPDATED, federatedPath));
        }
    }
}
