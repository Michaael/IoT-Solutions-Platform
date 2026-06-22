package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
public class CommercialBundleLicenseVerifier {

    private static final Logger log = LoggerFactory.getLogger(CommercialBundleLicenseVerifier.class);

    private final CommercialLicenseProperties properties;
    private final InstallationIdService installationIdService;
    private final ObjectMapper objectMapper;
    private final Optional<BuildProperties> buildProperties;

    public CommercialBundleLicenseVerifier(
            CommercialLicenseProperties properties,
            InstallationIdService installationIdService,
            ObjectMapper objectMapper,
            Optional<BuildProperties> buildProperties
    ) {
        this.properties = properties;
        this.installationIdService = installationIdService;
        this.objectMapper = objectMapper;
        this.buildProperties = buildProperties;
    }

    public void verifyOrWarn(String appId, Object manifest) {
        Map<String, Object> root = objectMapper.convertValue(manifest, Map.class);
        Object licenseRaw = root.get("license");
        if (licenseRaw == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        BundleLicenseClaims claims = BundleLicenseClaims.fromMap((Map<String, Object>) licenseRaw);
        if (claims == null) {
            return;
        }
        try {
            verify(appId, manifest, claims);
        } catch (CommercialLicenseException ex) {
            if (properties.isEnforce()) {
                throw ex;
            }
            log.warn("Commercial bundle license check failed (enforce=false): {}", ex.getMessage());
        }
    }

    public void verifyForValidation(String appId, Object manifest) {
        Map<String, Object> root = objectMapper.convertValue(manifest, Map.class);
        Object licenseRaw = root.get("license");
        if (licenseRaw == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        BundleLicenseClaims claims = BundleLicenseClaims.fromMap((Map<String, Object>) licenseRaw);
        if (claims == null) {
            return;
        }
        verify(appId, manifest, claims);
    }

    void verify(String appId, Object manifest, BundleLicenseClaims claims) {
        requireField(claims.bundleId(), "bundleId");
        requireField(claims.minPlatformVersion(), "minPlatformVersion");
        requireField(claims.installationId(), "installationId");
        requireField(claims.contentSha256(), "contentSha256");
        requireField(claims.expiresAt(), "expiresAt");
        requireField(claims.signature(), "signature");

        if (!claims.bundleId().equals(appId)) {
            throw new CommercialLicenseException("License bundleId mismatch: expected " + appId);
        }

        String computedHash = BundleManifestCanonicalizer.contentSha256(manifest, objectMapper);
        if (!computedHash.equalsIgnoreCase(claims.contentSha256())) {
            throw new CommercialLicenseException("License contentSha256 mismatch");
        }

        String currentInstallation = installationIdService.currentInstallationId();
        if (!claims.installationId().equalsIgnoreCase(currentInstallation)) {
            throw new CommercialLicenseException("License installationId mismatch");
        }

        Instant expiry = Instant.parse(claims.expiresAt());
        if (Instant.now().isAfter(expiry)) {
            throw new CommercialLicenseException("License expired at " + claims.expiresAt());
        }

        String platformVersion = PlatformVersionSupport.currentVersion(buildProperties);
        if (PlatformVersionSupport.compare(platformVersion, claims.minPlatformVersion()) < 0) {
            throw new CommercialLicenseException(
                    "Platform version " + platformVersion + " below minPlatformVersion "
                            + claims.minPlatformVersion()
            );
        }

        String publicKeyPem = properties.getPublicKeyPem();
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new CommercialLicenseException("ispf.license.public-key-pem is not configured");
        }

        verifySignature(claims, publicKeyPem);
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CommercialLicenseException("License field missing: " + name);
        }
    }

    private void verifySignature(BundleLicenseClaims claims, String publicKeyPem) {
        try {
            String payload = BundleManifestCanonicalizer.canonicalJson(
                    Map.of(
                            "bundleId", claims.bundleId(),
                            "minPlatformVersion", claims.minPlatformVersion(),
                            "installationId", claims.installationId(),
                            "contentSha256", claims.contentSha256(),
                            "expiresAt", claims.expiresAt()
                    )
            );
            PublicKey publicKey = loadPublicKey(publicKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(claims.signature());
            if (!signature.verify(signatureBytes)) {
                throw new CommercialLicenseException("License signature invalid");
            }
        } catch (CommercialLicenseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommercialLicenseException("License signature verify error: " + ex.getMessage());
        }
    }

    private static PublicKey loadPublicKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }
}
