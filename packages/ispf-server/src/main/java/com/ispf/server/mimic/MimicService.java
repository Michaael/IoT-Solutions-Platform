package com.ispf.server.mimic;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MimicService {

    private static final DataSchema DIAGRAM_SCHEMA = DataSchema.builder("diagram")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema TITLE_SCHEMA = DataSchema.builder("title")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema REFRESH_SCHEMA = DataSchema.builder("refreshIntervalMs")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;

    public MimicService(ObjectManager objectManager, SystemObjectStructureService structureService) {
        this.objectManager = objectManager;
        this.structureService = structureService;
    }

    @Transactional
    public void ensureMimicStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.MIMIC) {
            throw new IllegalArgumentException("Not a mimic object: " + path);
        }
        structureService.ensureMimicStructure(path);
    }

    public MimicView getMimic(String path) {
        ensureMimicStructure(path);
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.MIMIC) {
            throw new IllegalArgumentException("Not a mimic object: " + path);
        }
        String title = readString(node, "title").orElse(node.displayName());
        int refreshIntervalMs = readInteger(node, "refreshIntervalMs").orElse(5000);
        String diagramJson = readString(node, "diagram").orElse(MimicLayouts.EMPTY_MIMIC);
        return new MimicView(path, title, refreshIntervalMs, diagramJson);
    }

    @Transactional
    public MimicView saveDiagram(String path, String diagramJson) {
        if (diagramJson == null || diagramJson.isBlank()) {
            throw new IllegalArgumentException("diagramJson is required");
        }
        ensureMimicStructure(path);
        objectManager.setVariableValue(
                path,
                "diagram",
                DataRecord.single(DIAGRAM_SCHEMA, java.util.Map.of("value", diagramJson))
        );
        return getMimic(path);
    }

    @Transactional
    public MimicView updateTitle(String path, String title) {
        ensureMimicStructure(path);
        objectManager.setVariableValue(
                path,
                "title",
                DataRecord.single(TITLE_SCHEMA, java.util.Map.of("value", title))
        );
        return getMimic(path);
    }

    private static java.util.Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    private static java.util.Optional<Integer> readInteger(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                });
    }

    public record MimicView(String path, String title, int refreshIntervalMs, String diagramJson) {
    }
}
