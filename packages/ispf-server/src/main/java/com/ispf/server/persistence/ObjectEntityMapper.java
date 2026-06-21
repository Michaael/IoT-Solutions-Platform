package com.ispf.server.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ObjectEntityMapper {

    private final ObjectMapper objectMapper;

    public ObjectEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNodeEntity toEntity(PlatformObject node) {
        ObjectNodeEntity entity = new ObjectNodeEntity();
        entity.setId(node.id());
        entity.setPath(node.path());
        entity.setType(node.type());
        entity.setDisplayName(node.displayName());
        entity.setDescription(node.description());
        entity.setTemplateId(node.templateId().orElse(null));
        entity.setCreatedAt(node.createdAt());
        entity.setSortOrder(node.sortOrder());
        entity.setEventsJson(writeJson(node.events().values().toArray(new EventDescriptor[0])));
        entity.setFunctionsJson(writeJson(node.functions().values().toArray(new FunctionDescriptor[0])));
        entity.setRevision(node.revision());
        entity.setLastChangedBy(node.lastChangedBy());
        entity.setLastChangedAt(node.lastChangedAt());
        return entity;
    }

    public ObjectVariableEntity toEntity(String objectPath, Variable variable) {
        ObjectVariableEntity entity = new ObjectVariableEntity();
        entity.setObjectPath(objectPath);
        entity.setName(variable.name());
        entity.setSchemaJson(writeJson(variable.schema()));
        entity.setValueJson(variable.value().map(this::writeJson).orElse(null));
        entity.setReadable(variable.readable());
        entity.setWritable(variable.writable());
        entity.setBindingExpr(variable.bindingExpression().orElse(null));
        entity.setUpdatedAt(variable.updatedAt().orElse(null));
        entity.setHistoryEnabled(variable.historyEnabled());
        entity.setHistoryRetentionDays(variable.historyRetentionDays().orElse(null));
        return entity;
    }

    public DataSchema readSchema(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Schema JSON is empty");
        }
        try {
            return objectMapper.readValue(json, DataSchema.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Invalid schema JSON", e);
        }
    }

    public EventDescriptor[] readEvents(String json) {
        if (json == null || json.isBlank()) {
            return new EventDescriptor[0];
        }
        try {
            return objectMapper.readValue(json, EventDescriptor[].class);
        } catch (JacksonException e) {
            return new EventDescriptor[0];
        }
    }

    public FunctionDescriptor[] readFunctions(String json) {
        if (json == null || json.isBlank()) {
            return new FunctionDescriptor[0];
        }
        try {
            return objectMapper.readValue(json, FunctionDescriptor[].class);
        } catch (JacksonException e) {
            return new FunctionDescriptor[0];
        }
    }

    public DataRecord readDataRecord(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DataRecord.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    public String writeDataRecord(DataRecord record) {
        return writeJson(record);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
