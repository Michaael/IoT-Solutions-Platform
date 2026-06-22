package com.ispf.server.application.bundle;

import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BundleDependencyVerifier {

    private final ApplicationBundleSnapshotStore snapshotStore;

    public BundleDependencyVerifier(ApplicationBundleSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public void verify(String appId, List<ApplicationBundleDeployService.BundleDependency> requires) {
        if (requires == null || requires.isEmpty()) {
            return;
        }
        for (ApplicationBundleDeployService.BundleDependency dependency : requires) {
            if (dependency.appId() == null || dependency.appId().isBlank()) {
                throw new BundleDependencyException("Bundle dependency appId is required");
            }
            if (appId.equals(dependency.appId())) {
                throw new BundleDependencyException("Bundle cannot depend on itself: " + appId);
            }
            String minVersion = dependency.minVersion() != null && !dependency.minVersion().isBlank()
                    ? dependency.minVersion()
                    : "0.0.0";
            ApplicationBundleSnapshotStore.BundleSnapshot active = snapshotStore.findActive(dependency.appId())
                    .orElseThrow(() -> new BundleDependencyException(
                            "Missing required bundle dependency: " + dependency.appId()
                    ));
            if (PlatformVersionSupport.compare(active.bundleVersion(), minVersion) < 0) {
                throw new BundleDependencyException(
                        "Dependency " + dependency.appId() + " version "
                                + active.bundleVersion() + " is below required " + minVersion
                );
            }
        }
    }
}
