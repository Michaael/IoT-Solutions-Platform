package com.ispf.server.plugin.model.dto;

import com.ispf.plugin.model.ModelApplyResult;
import com.ispf.plugin.model.ModelAttachment;
import com.ispf.plugin.model.ModelMergeWarning;

import java.time.Instant;
import java.util.List;

public record ModelAttachmentDto(
        String id,
        String modelId,
        String modelName,
        String modelType,
        String objectPath,
        Instant attachedAt,
        List<ModelMergeWarningDto> warnings
) {
    public static ModelAttachmentDto from(ModelAttachment attachment) {
        return from(attachment, List.of());
    }

    public static ModelAttachmentDto from(ModelApplyResult result) {
        return from(result.attachment(), result.warnings());
    }

    public static ModelAttachmentDto from(ModelAttachment attachment, List<ModelMergeWarning> warnings) {
        return new ModelAttachmentDto(
                attachment.id(),
                attachment.modelId(),
                attachment.modelName(),
                attachment.modelType().name(),
                attachment.objectPath(),
                attachment.attachedAt(),
                warnings.stream().map(ModelMergeWarningDto::from).toList()
        );
    }
}
