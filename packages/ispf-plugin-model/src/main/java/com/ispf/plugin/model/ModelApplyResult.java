package com.ispf.plugin.model;

import java.util.List;

public record ModelApplyResult(
        ModelAttachment attachment,
        List<ModelMergeWarning> warnings
) {
    public ModelApplyResult {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
