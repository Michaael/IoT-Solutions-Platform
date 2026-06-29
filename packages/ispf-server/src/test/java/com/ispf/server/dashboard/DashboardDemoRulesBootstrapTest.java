package com.ispf.server.dashboard;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTargetKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardDemoRulesBootstrapTest {

    @Test
    void snmpDemoRulesUseContextTargetsAndOnContextChange() {
        List<BindingRule> rules = DashboardDemoRulesBootstrap.snmpDemoRules();
        assertThat(rules).hasSize(3);
        assertThat(rules).allMatch(rule -> rule.activators().onContextChange());
        assertThat(rules).allMatch(rule -> rule.target().kind() == BindingTargetKind.CONTEXT);
        assertThat(rules.stream().map(rule -> rule.target().path()).toList())
                .containsExactly("params.mode", "widgets.btop-net-down.visible", "widgets.btop-net-down.visible");
    }
}
