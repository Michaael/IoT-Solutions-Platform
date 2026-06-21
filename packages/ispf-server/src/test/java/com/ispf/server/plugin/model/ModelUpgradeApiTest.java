package com.ispf.server.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelUpgradeApiTest {

    private static final String VENDOR_PATH = "root.platform.devices.vendor-sensor-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelRegistry modelRegistry;

    @Autowired
    private ModelEngine modelEngine;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ModelApplicationRunner modelApplicationRunner;

    @Test
    void vendorDemoDeviceExistsAndBulkUpgradeSucceeds() throws Exception {
        modelApplicationRunner.applyDemoModels();

        var vendorModel = modelRegistry.requireByName("vendor-sensor-ext-v1");
        objectManager.require(VENDOR_PATH);

        mockMvc.perform(get("/api/v1/models/{id}/instances", vendorModel.id()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/models/{id}/upgrade-instances", vendorModel.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));

        var device = objectManager.require(VENDOR_PATH);
        org.assertj.core.api.Assertions.assertThat(device.getVariable("humidity")).isPresent();
        org.assertj.core.api.Assertions.assertThat(device.getVariable("temperature")).isPresent();
    }

    @Test
    void singlePathUpgradeApi() throws Exception {
        modelApplicationRunner.applyDemoModels();
        var vendorModel = modelRegistry.requireByName("vendor-sensor-ext-v1");

        mockMvc.perform(post("/api/v1/models/{id}/upgrade", vendorModel.id())
                        .param("targetPath", VENDOR_PATH)
                        .param("targetVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.targetPath").value(VENDOR_PATH));
    }
}
