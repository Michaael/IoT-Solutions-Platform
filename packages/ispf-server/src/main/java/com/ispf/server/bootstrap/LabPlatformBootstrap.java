package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.report.ReportService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 15: lab models and demo devices for multi-user collaboration exercises.
 */
@Component
public class LabPlatformBootstrap {

    private static final String DEVICES_ROOT = "root.platform.devices";

    private final LabModelBootstrap labModelBootstrap;
    private final ObjectTemplateService objectTemplateService;
    private final ObjectManager objectManager;
    private final ReportService reportService;

    public LabPlatformBootstrap(
            LabModelBootstrap labModelBootstrap,
            ObjectTemplateService objectTemplateService,
            ObjectManager objectManager,
            ReportService reportService
    ) {
        this.labModelBootstrap = labModelBootstrap;
        this.objectTemplateService = objectTemplateService;
        this.objectManager = objectManager;
        this.reportService = reportService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    @Transactional
    public void onReady() {
        labModelBootstrap.ensureLabModels();
        ensureLabDevice(
                "lab-userA-01",
                "Lab User A Device 01",
                "Collaboration lab device for user A"
        );
        ensureLabDevice(
                "lab-userB-01",
                "Lab User B Device 01",
                "Collaboration lab device for user B"
        );
        LabVirtualReports.deployAll(reportService);
    }

    private void ensureLabDevice(String name, String displayName, String description) {
        String path = DEVICES_ROOT + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    DEVICES_ROOT,
                    name,
                    ObjectType.DEVICE,
                    displayName,
                    description,
                    LabModelBootstrap.VIRTUAL_LAB_MODEL
            );
        } else {
            objectManager.reconcileType(path, ObjectType.DEVICE);
        }

        objectTemplateService.applyTemplate(path, LabModelBootstrap.VIRTUAL_LAB_MODEL);
    }
}
