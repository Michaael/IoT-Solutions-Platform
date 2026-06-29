package com.ispf.server.dashboard;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingTargetKind;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent demo platform rules on built-in dashboards (ADR-0019 phase 2).
 */
@Component
public class DashboardDemoRulesBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DashboardDemoRulesBootstrap.class);
    private static final String SNMP_PATH = "root.platform.dashboards.snmp-host-monitoring";

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;

    public DashboardDemoRulesBootstrap(ObjectManager objectManager, BindingRulesService bindingRulesService) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
    }

    public void ensureDemoRules() {
        ensureSnmpHostMonitoringRules();
    }

    private void ensureSnmpHostMonitoringRules() {
        if (objectManager.tree().findByPath(SNMP_PATH).isEmpty()) {
            log.debug("Skip SNMP demo rules: dashboard {} not present", SNMP_PATH);
            return;
        }
        List<BindingRule> existing = bindingRulesService.listRules(SNMP_PATH);
        if (existing.stream().anyMatch(rule -> rule.id().startsWith("demo-ctx-"))) {
            return;
        }
        List<BindingRule> demoRules = snmpDemoRules();
        List<BindingRule> merged = new ArrayList<>(existing);
        merged.addAll(demoRules);
        bindingRulesService.saveRules(SNMP_PATH, merged);
        log.info("Seeded {} platform demo rules on {}", demoRules.size(), SNMP_PATH);
    }

    static List<BindingRule> snmpDemoRules() {
        BindingActivators onContext = new BindingActivators(
                false,
                List.of(),
                null,
                0,
                false,
                true
        );
        return List.of(
                new BindingRule(
                        "demo-ctx-mode-on-select",
                        "Set detail mode when device selected",
                        true,
                        10,
                        onContext,
                        "context.selection.device != \"\"",
                        "\"detail\"",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "params.mode", null)
                ),
                new BindingRule(
                        "demo-ctx-show-net-chart",
                        "Show net chart in detail mode",
                        true,
                        20,
                        onContext,
                        "context.params.mode == \"detail\"",
                        "true",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "widgets.btop-net-down.visible", null)
                ),
                new BindingRule(
                        "demo-ctx-hide-net-chart",
                        "Hide net chart when idle",
                        true,
                        30,
                        onContext,
                        "context.params.mode != \"detail\"",
                        "false",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "widgets.btop-net-down.visible", null)
                )
        );
    }
}
