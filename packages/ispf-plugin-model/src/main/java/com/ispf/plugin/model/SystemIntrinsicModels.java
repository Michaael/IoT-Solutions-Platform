package com.ispf.plugin.model;

import com.ispf.core.object.ObjectType;

import java.util.Map;
import java.util.Set;

/**
 * Platform 1:1 schemas for system {@link ObjectType}s — embedded in instances, not shown as relative-model catalog entries.
 */
public final class SystemIntrinsicModels {

    public static final String PARAM_SYSTEM_INTRINSIC = "systemIntrinsic";

    public static final Set<String> NAMES = Set.of(
            "data-source-v1",
            "schedule-v1",
            "sql-binding-v1",
            "migration-v1",
            "alert-rule-v1",
            "correlator-v1",
            "dashboard-v1",
            "mimic-v1",
            "report-v1",
            "workflow-v1"
    );

    private static final Map<ObjectType, String> BY_OBJECT_TYPE = Map.ofEntries(
            Map.entry(ObjectType.DATA_SOURCE, "data-source-v1"),
            Map.entry(ObjectType.SCHEDULE, "schedule-v1"),
            Map.entry(ObjectType.BINDING, "sql-binding-v1"),
            Map.entry(ObjectType.MIGRATION, "migration-v1"),
            Map.entry(ObjectType.ALERT, "alert-rule-v1"),
            Map.entry(ObjectType.CORRELATOR, "correlator-v1"),
            Map.entry(ObjectType.DASHBOARD, "dashboard-v1"),
            Map.entry(ObjectType.MIMIC, "mimic-v1"),
            Map.entry(ObjectType.REPORT, "report-v1"),
            Map.entry(ObjectType.WORKFLOW, "workflow-v1")
    );

    private SystemIntrinsicModels() {
    }

    public static boolean isIntrinsicName(String name) {
        return name != null && NAMES.contains(name);
    }

    public static boolean isIntrinsic(ModelDefinition model) {
        return model != null && (model.systemIntrinsic() || isIntrinsicName(model.name()));
    }

    public static String modelNameForObjectType(ObjectType type) {
        return BY_OBJECT_TYPE.get(type);
    }

    public static Map<String, String> parameters() {
        return Map.of(PARAM_SYSTEM_INTRINSIC, "true");
    }
}
