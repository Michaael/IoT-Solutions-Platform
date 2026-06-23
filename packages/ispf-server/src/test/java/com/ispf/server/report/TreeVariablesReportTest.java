package com.ispf.server.report;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.LabModelBootstrap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeVariablesReportTest {

    private static final DataSchema TABLE_ROW_SCHEMA = DataSchema.builder("tableRow")
            .field("int", FieldType.INTEGER)
            .field("string", FieldType.STRING)
            .build();

    private static final DataSchema TABLE_SCHEMA = DataSchema.builder("table")
            .field(new FieldDefinition("rows", FieldType.RECORD_LIST, "", true, TABLE_ROW_SCHEMA))
            .build();

    @Test
    void matchesPrefixAndGlobPatterns() {
        assertTrue(ReportService.matchesDevicePathPattern(
                "root.platform.devices.lab-userA-01",
                "root.platform.devices.lab-"
        ));
        assertTrue(ReportService.matchesDevicePathPattern(
                "root.platform.devices.lab-userB-01",
                "root.platform.devices.lab-*"
        ));
        assertFalse(ReportService.matchesDevicePathPattern(
                "root.platform.devices.demo-sensor-01",
                "root.platform.devices.lab-*"
        ));
    }

    @Test
    void flattenRecordListTableRows() {
        PlatformObject device = new PlatformObject(
                "dev-a",
                "root.platform.devices.lab-userA-01",
                ObjectType.DEVICE,
                "Lab A",
                "",
                LabModelBootstrap.VIRTUAL_LAB_MODEL
        );
        List<Map<String, Object>> tableRows = List.of(
                Map.of("int", 1, "string", "alpha"),
                Map.of("int", 2, "string", "beta")
        );
        device.addVariable(new Variable(
                "table",
                TABLE_SCHEMA,
                true,
                true, DataRecord.single(TABLE_SCHEMA, Map.of("rows", tableRows)),
                false,
                0
        ));

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        device.getVariable("table")
                .flatMap(Variable::value)
                .ifPresent(record -> flatten(device.path(), record, rows));

        assertEquals(2, rows.size());
        assertEquals("root.platform.devices.lab-userA-01", rows.get(0).get("devicepath"));
        assertEquals(1, rows.get(0).get("int"));
        assertEquals("alpha", rows.get(0).get("string"));
    }

    @Test
    void flattenScalarDoubleValue() {
        DataSchema valueSchema = DataSchema.builder("doubleValue")
                .field("value", FieldType.DOUBLE)
                .build();
        PlatformObject device = new PlatformObject(
                "dev-a",
                "root.platform.devices.lab-userA-01",
                ObjectType.DEVICE,
                "Lab A",
                "",
                LabModelBootstrap.VIRTUAL_LAB_MODEL
        );
        device.addVariable(new Variable(
                "sineWave",
                valueSchema,
                true,
                true,
                DataRecord.single(valueSchema, Map.of("value", 7.5)),
                false,
                0
        ));

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        device.getVariable("sineWave")
                .flatMap(Variable::value)
                .ifPresent(record -> flattenScalar(device.path(), record, rows));

        assertEquals(1, rows.size());
        assertEquals("root.platform.devices.lab-userA-01", rows.get(0).get("devicepath"));
        assertEquals(7.5, rows.get(0).get("value"));
    }

    private static void flattenScalar(String devicePath, DataRecord record, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : record.rows()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("devicepath", devicePath);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                mapped.put(entry.getKey(), entry.getValue());
            }
            rows.add(mapped);
        }
    }

    private static void flatten(String devicePath, DataRecord record, List<Map<String, Object>> rows) {
        var listField = record.schema().fields().stream()
                .filter(field -> field.type() == FieldType.RECORD_LIST)
                .map(FieldDefinition::name)
                .findFirst();
        if (listField.isPresent() && record.rowCount() > 0) {
            Object tableRowsObject = record.firstRow().get(listField.get());
            if (tableRowsObject instanceof List<?> tableRows) {
                for (Object rowObject : tableRows) {
                    if (rowObject instanceof Map<?, ?> row) {
                        Map<String, Object> mapped = new LinkedHashMap<>();
                        mapped.put("devicepath", devicePath);
                        mapped.put("int", row.get("int"));
                        mapped.put("string", row.get("string"));
                        rows.add(mapped);
                    }
                }
            }
        }
    }
}
