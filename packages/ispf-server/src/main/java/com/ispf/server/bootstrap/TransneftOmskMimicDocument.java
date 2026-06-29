package com.ispf.server.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SCADA mimic document JSON for the Transneft Omsk RDP demo (exported from
 * {@code apps/web-console/src/scada/templates/transneftOmskMimic.ts}).
 */
public final class TransneftOmskMimicDocument {

    public static final String DIAGRAM_JSON = loadDiagramJson();

    private static String loadDiagramJson() {
        try (InputStream in = TransneftOmskMimicDocument.class.getResourceAsStream("/bootstrap/transneft-omsk-mimic.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource bootstrap/transneft-omsk-mimic.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load transneft-omsk-mimic.json", e);
        }
    }

    private TransneftOmskMimicDocument() {
    }
}
