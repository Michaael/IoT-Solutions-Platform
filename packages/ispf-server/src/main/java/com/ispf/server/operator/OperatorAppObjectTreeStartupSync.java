package com.ispf.server.operator;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class OperatorAppObjectTreeStartupSync {

    private final OperatorAppObjectTreeService treeService;

    public OperatorAppObjectTreeStartupSync(OperatorAppObjectTreeService treeService) {
        this.treeService = treeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void syncOperatorAppsIntoObjectTree() {
        treeService.syncAll();
    }
}
