package com.ispf.server.application.bff;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BffWireMapper {

    private BffWireMapper() {
    }

    public static Map<String, Object> toWire(DataRecord output, String wireProfile, DataSchema outputSchema) {
        Map<String, Object> row = output != null && output.rowCount() > 0
                ? new LinkedHashMap<>(output.firstRow())
                : new LinkedHashMap<>();

        String errorCode = stringValue(row.remove("error_code"), "OK");
        String errorMessage = stringValue(row.remove("error_message"), "");

        if (AnimaOperatorWireProfile.ID.equals(wireProfile)) {
            return toAnimaOperatorWire(errorCode, errorMessage, row, outputSchema, wireProfile);
        }

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("error_code", errorCode);
        wire.put("error_message", errorMessage);
        if ("OK".equals(errorCode)) {
            wire.put("result", row);
        }
        if (wireProfile != null && !wireProfile.isBlank()) {
            wire.put("wireProfile", wireProfile);
        }
        return wire;
    }

    private static Map<String, Object> toAnimaOperatorWire(
            String errorCode,
            String errorMessage,
            Map<String, Object> row,
            DataSchema outputSchema,
            String wireProfile
    ) {
        Map<String, String> profileDefaults = AnimaOperatorWireProfile.defaultFieldLabels();

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("error_code", errorCode);
        wire.put("error_message", "OK".equals(errorCode) ? "" : errorMessage);
        wire.put("wireProfile", wireProfile);

        if (!"OK".equals(errorCode)) {
            return wire;
        }

        Object tableResult = unwrapTableResult(row);
        if (tableResult instanceof List<?>) {
            wire.put("result", tableResult);
            wire.put("result_field_labels", tableLabels(outputSchema, (List<?>) tableResult, profileDefaults));
        } else if (!row.isEmpty()) {
            wire.put("result", row);
            wire.put("result_field_labels", scalarLabels(row, outputSchema, profileDefaults));
        } else {
            wire.put("result", Map.of());
            wire.put("result_field_labels", Map.of());
        }
        return wire;
    }

    private static Object unwrapTableResult(Map<String, Object> row) {
        if (row.size() == 1) {
            Object only = row.values().iterator().next();
            if (only instanceof List<?>) {
                return only;
            }
        }
        for (Object value : row.values()) {
            if (value instanceof List<?>) {
                return value;
            }
        }
        return row;
    }

    private static Map<String, String> tableLabels(
            DataSchema outputSchema,
            List<?> rows,
            Map<String, String> profileDefaults
    ) {
        if (outputSchema != null) {
            for (FieldDefinition field : outputSchema.fields()) {
                if (field.type() == FieldType.RECORD_LIST && field.nestedSchema() != null) {
                    Map<String, String> labels = AnimaOperatorWireProfile.labelsFromSchemaFields(
                            field.nestedSchema().fields(),
                            profileDefaults
                    );
                    if (!labels.isEmpty()) {
                        return labels;
                    }
                }
            }
            Map<String, String> labels = AnimaOperatorWireProfile.labelsFromSchemaFields(
                    outputSchema.fields(),
                    profileDefaults
            );
            labels.remove("error_code");
            labels.remove("error_message");
            if (!labels.isEmpty()) {
                return labels;
            }
        }
        if (!rows.isEmpty() && rows.get(0) instanceof Map<?, ?> firstRow) {
            Map<String, String> labels = new LinkedHashMap<>();
            firstRow.keySet().forEach(key -> {
                String name = String.valueOf(key);
                labels.put(name, AnimaOperatorWireProfile.resolveLabel(name, null, profileDefaults));
            });
            return labels;
        }
        return Map.of();
    }

    private static Map<String, String> scalarLabels(
            Map<String, Object> row,
            DataSchema outputSchema,
            Map<String, String> profileDefaults
    ) {
        Map<String, String> schemaByName = new LinkedHashMap<>();
        if (outputSchema != null) {
            for (FieldDefinition field : outputSchema.fields()) {
                schemaByName.put(field.name(), field.description());
            }
        }
        Map<String, String> labels = new LinkedHashMap<>();
        row.keySet().forEach(key -> labels.put(
                key,
                AnimaOperatorWireProfile.resolveLabel(key, schemaByName.get(key), profileDefaults)
        ));
        return labels;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
