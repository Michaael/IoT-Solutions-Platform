package com.ispf.server.plugin.model;

import com.ispf.expression.ExpressionEngine;
import com.ispf.core.object.ObjectTree;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelPluginConfig {

    @Bean
    ModelRegistry modelRegistry() {
        return new ModelRegistry();
    }

    @Bean
    ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }

    @Bean
    ModelEngine modelEngine(
            ModelRegistry modelRegistry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine
    ) {
        return new ModelEngine(modelRegistry, objectTree, expressionEngine);
    }

    @Bean
    TypedModelFacade relativeModelFacade(
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ModelPersistenceService modelPersistence,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedModelFacade(
                ModelType.RELATIVE,
                modelRegistry,
                modelEngine,
                modelPersistence,
                modelApplicationService,
                objectManager
        );
    }

    @Bean
    TypedModelFacade instanceTypeFacade(
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ModelPersistenceService modelPersistence,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedModelFacade(
                ModelType.INSTANCE,
                modelRegistry,
                modelEngine,
                modelPersistence,
                modelApplicationService,
                objectManager
        );
    }

    @Bean
    TypedModelFacade absoluteModelFacade(
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ModelPersistenceService modelPersistence,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedModelFacade(
                ModelType.ABSOLUTE,
                modelRegistry,
                modelEngine,
                modelPersistence,
                modelApplicationService,
                objectManager
        );
    }
}
