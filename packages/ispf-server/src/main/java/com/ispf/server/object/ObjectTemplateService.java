package com.ispf.server.object;

import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelException;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.plugin.model.ModelApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies model templates ({@code templateId}) to objects after creation.
 */
@Service
public class ObjectTemplateService {

    private static final Map<String, List<String>> COMPANION_MODELS_BY_NAME = Map.of(
            LabModelBootstrap.VIRTUAL_LAB_MODEL,
            List.of(LabModelBootstrap.VIRTUAL_LAB_WAVES_SUM_MODEL),
            LabModelBootstrap.VIRTUAL_UNIFIED_MODEL,
            List.of(LabModelBootstrap.VIRTUAL_LAB_WAVES_SUM_MODEL)
    );

    private final ModelRegistry modelRegistry;
    private final ModelApplicationService modelApplicationService;

    public ObjectTemplateService(
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService
    ) {
        this.modelRegistry = modelRegistry;
        this.modelApplicationService = modelApplicationService;
    }

    @Transactional
    public void applyTemplate(String objectPath, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        ModelDefinition model = resolveTemplate(templateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown templateId: " + templateId
                ));
        applyResolvedModel(objectPath, model);
        for (String companionName : COMPANION_MODELS_BY_NAME.getOrDefault(model.name(), List.of())) {
            modelRegistry.findByName(companionName).ifPresent(companion ->
                    applyResolvedModel(objectPath, companion)
            );
        }
    }

    private void applyResolvedModel(String objectPath, ModelDefinition model) {
        try {
            modelApplicationService.applyModelWithRules(model, objectPath, model.parameters());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Optional<ModelDefinition> resolveTemplate(String templateId) {
        Optional<ModelDefinition> byId = modelRegistry.findById(templateId);
        if (byId.isPresent()) {
            return byId;
        }
        return modelRegistry.findByName(templateId);
    }
}
