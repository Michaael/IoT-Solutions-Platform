package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelApplyResult;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelException;
import com.ispf.plugin.model.ModelMergeWarning;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified model apply path: structure merge, binding rules, persistence of appliedModelIds.
 */
@Service
public class ModelApplicationService {

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;
    private final ModelBindingRulesMerger bindingRulesMerger;
    private final ObjectManager objectManager;

    public ModelApplicationService(
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ModelBindingRulesMerger bindingRulesMerger,
            ObjectManager objectManager
    ) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.bindingRulesMerger = bindingRulesMerger;
        this.objectManager = objectManager;
    }

    @Transactional
    public ModelApplyResult applyModelWithRules(String modelId, String objectPath) {
        return applyModelWithRules(modelRegistry.requireById(modelId), objectPath, Map.of());
    }

    @Transactional
    public ModelApplyResult applyModelWithRules(ModelDefinition model, String objectPath, Map<String, String> parameters) {
        try {
            ModelApplyResult result = modelEngine.applyModel(model.id(), objectPath);
            bindingRulesMerger.mergeModelRules(objectPath, model, parameters);
            objectManager.persistNodeTree(objectPath);
            return result;
        } catch (ModelException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public ModelApplyResult instantiateWithRules(
            String modelId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        try {
            ModelApplyResult result = modelEngine.instantiateModel(modelId, parentPath, instanceName, parameters);
            PlatformObject instance = objectManager.require(
                    objectManager.tree().resolveChildPath(parentPath, instanceName)
            );
            ModelDefinition model = modelRegistry.requireById(modelId);
            bindingRulesMerger.mergeModelRules(instance.path(), model, parameters != null ? parameters : Map.of());
            List<ModelApplyResult> relativeResults = modelEngine.applyRelativeModels(instance.path());
            for (ModelApplyResult relative : relativeResults) {
                modelRegistry.findById(relative.attachment().modelId()).ifPresent(relativeModel ->
                        bindingRulesMerger.mergeModelRules(
                                instance.path(),
                                relativeModel,
                                relativeModel.parameters()
                        )
                );
            }
            objectManager.persistNodeTree(instance.path());
            return aggregateResults(result, relativeResults);
        } catch (ModelException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public List<ModelApplyResult> applyRelativeModelsWithRules(String objectPath) {
        List<ModelApplyResult> results = modelEngine.applyRelativeModels(objectPath);
        for (ModelApplyResult result : results) {
            modelRegistry.findById(result.attachment().modelId()).ifPresent(model ->
                    bindingRulesMerger.mergeModelRules(objectPath, model, model.parameters())
            );
        }
        if (!results.isEmpty()) {
            objectManager.persistNodeTree(objectPath);
        }
        return results;
    }

    public void restoreAttachments() {
        modelEngine.restoreAttachmentsFromObjects();
    }

    private static ModelApplyResult aggregateResults(ModelApplyResult primary, List<ModelApplyResult> additional) {
        List<ModelMergeWarning> warnings = new ArrayList<>(primary.warnings());
        for (ModelApplyResult result : additional) {
            warnings.addAll(result.warnings());
        }
        return new ModelApplyResult(primary.attachment(), warnings);
    }
}
