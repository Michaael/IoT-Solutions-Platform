package com.ispf.server.platform;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaystackExportServiceTest {

    @Test
    void acceptsPathsUnderRoot() {
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices.lab-userA-01",
                "root.platform.devices"
        ));
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices",
                "root.platform.devices"
        ));
        assertFalse(HaystackExportService.isUnderRoot(
                "root.platform.reports.demo",
                "root.platform.devices"
        ));
    }

    @Test
    void normalizesBlankRootToPlatformDefault() {
        assertTrue(HaystackExportService.normalizeRootPath(null)
                .startsWith("root.platform"));
        assertTrue(HaystackExportService.normalizeRootPath("  ")
                .startsWith("root.platform"));
    }

    @Test
    void tagsMatchRequiresAllMarkers() {
        Map<String, Boolean> tags = Map.of(
                "equip", true,
                "point", true,
                "temp", true
        );
        assertTrue(HaystackExportService.tagsMatch(tags, List.of("equip", "temp")));
        assertFalse(HaystackExportService.tagsMatch(tags, List.of("equip", "power")));
    }

    @Test
    void normalizeTagQuerySplitsCommaSeparatedValues() {
        assertEquals(
                List.of("equip", "point", "temp"),
                HaystackExportService.normalizeTagQuery(List.of("equip,point", "temp"))
        );
    }
}
