package com.ispf.server.license;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommercialBundleDeployApiTest {

    private static final KeyPair keyPair;
    private static final String publicKeyPem;

    static {
        try {
            keyPair = LicenseTestSupport.generateRsaKeyPair();
            publicKeyPem = LicenseTestSupport.toPemPublicKey(keyPair);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void licenseProperties(DynamicPropertyRegistry registry) {
        registry.add("ispf.license.enforce", () -> "true");
        registry.add("ispf.license.public-key-pem", () -> publicKeyPem);
        registry.add("ispf.bootstrap.fixtures-enabled", () -> "false");
    }

    @Test
    void deployWithValidLicenseSucceeds() throws Exception {
        String installationId = mockMvc.perform(get("/api/v1/platform/installation-id"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(installationId).get("installationId").asText();

        Map<String, Object> manifest = baseManifest();
        manifest.put("license", LicenseTestSupport.signedLicense(
                objectMapper, manifest, "licensed-deploy", id, keyPair
        ));

        mockMvc.perform(post("/api/v1/applications/licensed-deploy/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void deployWithInvalidLicenseReturnsForbidden() throws Exception {
        Map<String, Object> manifest = baseManifest();
        Map<String, Object> license = new LinkedHashMap<>();
        license.put("bundleId", "licensed-deploy");
        license.put("minPlatformVersion", "0.1.0");
        license.put("installationId", "00".repeat(32));
        license.put("contentSha256", "00".repeat(64));
        license.put("expiresAt", "2099-01-01T00:00:00Z");
        license.put("signature", "invalid");
        manifest.put("license", license);

        mockMvc.perform(post("/api/v1/applications/licensed-deploy/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isForbidden());
    }

    private static Map<String, Object> baseManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Licensed Deploy");
        manifest.put("schemaName", "app_licensed_deploy");
        manifest.put("migrations", java.util.List.of());
        manifest.put("functions", java.util.List.of());
        return manifest;
    }
}
