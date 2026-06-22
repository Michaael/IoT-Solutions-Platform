package com.ispf.server.ai.validation;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class BundleManifestValidatorTest {

    @Autowired
    private BundleManifestValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptsWarehouseReferenceBundle() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = readBundle("warehouse-bundle.json");
        BundleValidationResult result = validator.validate("warehouse", manifest);
        assertEquals(BundleValidationResult.OK, result.status());
    }

    @Test
    void acceptsMesReferenceBundle() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = readBundle("mes-reference-bundle.json");
        BundleValidationResult result = validator.validate("mes-reference", manifest);
        assertEquals(BundleValidationResult.OK, result.status());
    }

    @Test
    void rejectsInvalidFunctionScript() {
        ApplicationBundleDeployService.BundleManifest manifest = new ApplicationBundleDeployService.BundleManifest(
                "1.0.0",
                "Invalid",
                "wh_",
                "app_invalid",
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(new ApplicationBundleDeployService.BundleFunction(
                        "root.platform.applications.invalid.functions.demo",
                        "badFn",
                        "1.0.0",
                        null,
                        new com.ispf.server.application.api.ApplicationController.FunctionSourceDto(
                                "script",
                                "{\"steps\":[{\"type\":\"unknown_step\"}]}"
                        )
                )),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        BundleValidationResult result = validator.validate("invalid", manifest);
        assertEquals(BundleValidationResult.ERROR, result.status());
    }

    private ApplicationBundleDeployService.BundleManifest readBundle(String resource) throws Exception {
        String json = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, ApplicationBundleDeployService.BundleManifest.class);
    }
}
