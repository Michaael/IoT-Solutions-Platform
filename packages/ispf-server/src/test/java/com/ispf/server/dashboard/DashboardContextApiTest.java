package com.ispf.server.dashboard;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingTargetKind;
import com.ispf.server.object.BindingRulesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardContextApiTest {

    private static final String PATH = "root.platform.dashboards.demo-sensor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Test
    void getContextEnsuresVariable() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/by-path/context").param("path", PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.context.selection").isMap())
                .andExpect(jsonPath("$.context.params").isMap());
    }

    @Test
    void putContextRoundTripAndRuleReaction() throws Exception {
        bindingRulesService.saveRules(PATH, List.of(
                new BindingRule(
                        "ctx-mode",
                        "Set mode on selection",
                        true,
                        10,
                        new BindingActivators(false, List.of(), null, 0, false, true),
                        "context.selection.device != \"\"",
                        "\"alarm\"",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "params.mode", null)
                )
        ));

        mockMvc.perform(put("/api/v1/dashboards/by-path/context")
                        .param("path", PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context": {
                                    "selection": { "device": "root.platform.devices.demo-sensor-01" },
                                    "params": { "mode": "normal" }
                                  },
                                  "updatedBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.selection.device")
                        .value("root.platform.devices.demo-sensor-01"))
                .andExpect(jsonPath("$.context.params.mode").value("alarm"));

        bindingRulesService.deleteRule(PATH, "ctx-mode");
    }

    @Test
    void putContextWidgetVisibilityRule() throws Exception {
        bindingRulesService.saveRules(PATH, List.of(
                new BindingRule(
                        "ctx-widget-vis",
                        "Hide panel",
                        true,
                        10,
                        new BindingActivators(false, List.of(), null, 0, false, true),
                        "context.params.mode == \"alarm\"",
                        "false",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "widgets.alarm-panel.visible", null)
                )
        ));

        mockMvc.perform(put("/api/v1/dashboards/by-path/context")
                        .param("path", PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context": {
                                    "selection": {},
                                    "params": { "mode": "alarm" },
                                    "widgets": {}
                                  },
                                  "updatedBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.widgets.alarm-panel.visible").value(false));

        bindingRulesService.deleteRule(PATH, "ctx-widget-vis");
    }
}
