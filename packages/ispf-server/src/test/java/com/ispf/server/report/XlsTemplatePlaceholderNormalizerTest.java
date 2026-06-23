package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XlsTemplatePlaceholderNormalizerTest {

    @Test
    void rewritesBand1PrefixedPlaceholders() {
        assertEquals(
                "${DEVICEPATH} / ${VALUE}",
                XlsTemplatePlaceholderNormalizer.normalizeText("${Band1.DEVICEPATH} / ${Band1.VALUE}")
        );
    }
}
