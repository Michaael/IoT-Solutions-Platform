package com.ispf.plugin.model;

/**
 * Non-fatal warning when a model merge overwrites an existing member (last-wins).
 */
public record ModelMergeWarning(
        String kind,
        String name,
        String previousModelId,
        String appliedModelId
) {
    public static final String KIND_VARIABLE = "variable";
    public static final String KIND_EVENT = "event";
    public static final String KIND_FUNCTION = "function";
}
