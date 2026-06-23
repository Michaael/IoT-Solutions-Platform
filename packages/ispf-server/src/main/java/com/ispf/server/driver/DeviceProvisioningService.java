package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.plugin.model.ModelApplicationService;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.plugin.model.ModelBootstrap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Applies the generic device-driver model and configures driver defaults on new DEVICE objects.
 */
@Service
public class DeviceProvisioningService {

    private final ModelApplicationService modelApplicationService;
    private final ModelRegistry modelRegistry;
    private final DriverCatalog driverCatalog;
    private final DriverRuntimeService driverRuntimeService;

    public DeviceProvisioningService(
            ModelApplicationService modelApplicationService,
            ModelRegistry modelRegistry,
            DriverCatalog driverCatalog,
            DriverRuntimeService driverRuntimeService
    ) {
        this.modelApplicationService = modelApplicationService;
        this.modelRegistry = modelRegistry;
        this.driverCatalog = driverCatalog;
        this.driverRuntimeService = driverRuntimeService;
    }

    public void provisionDriver(String devicePath, String driverId, Integer pollIntervalMs, boolean autoStart) {
        if (driverId == null || driverId.isBlank()) {
            return;
        }
        String resolvedDriverId = driverId.trim();
        DriverMetadata metadata = driverCatalog.list().stream()
                .filter(driver -> driver.id().equals(resolvedDriverId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown driverId: " + resolvedDriverId
                ));

        try {
            modelApplicationService.applyModelWithRules(
                    modelRegistry.requireByName(ModelBootstrap.DEVICE_DRIVER_MODEL).id(),
                    devicePath
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        int interval = pollIntervalMs != null && pollIntervalMs > 0 ? pollIntervalMs : 5000;
        Map<String, String> configuration = metadata.configurationSchema() != null
                ? Map.copyOf(metadata.configurationSchema())
                : Map.of();

        DriverBinding binding = new DriverBinding(resolvedDriverId, interval, configuration, Map.of());
        driverRuntimeService.configure(devicePath, binding);
        if (autoStart) {
            driverRuntimeService.start(devicePath);
        }
    }
}
