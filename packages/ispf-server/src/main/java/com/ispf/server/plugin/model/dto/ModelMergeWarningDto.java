package com.ispf.server.plugin.model.dto;

import com.ispf.plugin.model.ModelMergeWarning;

public record ModelMergeWarningDto(
        String kind,
        String name,
        String previousModelId,
        String appliedModelId
) {
    public static ModelMergeWarningDto from(ModelMergeWarning warning) {
        return new ModelMergeWarningDto(
                warning.kind(),
                warning.name(),
                warning.previousModelId(),
                warning.appliedModelId()
        );
    }
}
