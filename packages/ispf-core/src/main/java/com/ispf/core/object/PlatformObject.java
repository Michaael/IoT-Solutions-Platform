package com.ispf.core.object;

import com.ispf.core.model.DataRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A node in the hierarchical resource graph — the central ISPF concept.
 * <p>
 * Path example: {@code tenants.acme.devices.modbus-pump-01}
 */
public class PlatformObject {

    private final String id;
    private final String path;
    private ObjectType type;
    private volatile String displayName;
    private volatile String description;
    private final String templateId;
    private final List<String> appliedModelIds = new CopyOnWriteArrayList<>();
    private volatile int sortOrder;
    private volatile boolean bindingAuditEnabled;
    private volatile boolean functionAuditEnabled;
    private final Instant createdAt;
    private volatile long revision;
    private volatile String lastChangedBy;
    private volatile Instant lastChangedAt;
    private final Map<String, Variable> variables = new ConcurrentHashMap<>();
    private final Map<String, FunctionDescriptor> functions = new ConcurrentHashMap<>();
    private final Map<String, EventDescriptor> events = new ConcurrentHashMap<>();

    public PlatformObject(
            String id,
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        this(id, path, type, displayName, description, templateId, 0);
    }

    public PlatformObject(
            String id,
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId,
            int sortOrder
    ) {
        this.id = id;
        this.path = path;
        this.type = type;
        this.displayName = displayName != null ? displayName : path.substring(path.lastIndexOf('.') + 1);
        this.description = description != null ? description : "";
        this.templateId = templateId;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.revision = 0L;
    }

    public PlatformObject(
            String id,
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId,
            int sortOrder,
            long revision,
            String lastChangedBy,
            Instant lastChangedAt
    ) {
        this.id = id;
        this.path = path;
        this.type = type;
        this.displayName = displayName != null ? displayName : path.substring(path.lastIndexOf('.') + 1);
        this.description = description != null ? description : "";
        this.templateId = templateId;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.revision = revision;
        this.lastChangedBy = lastChangedBy;
        this.lastChangedAt = lastChangedAt;
    }

    public String id() {
        return id;
    }

    public String path() {
        return path;
    }

    public ObjectType type() {
        return type;
    }

    public void setType(ObjectType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        this.type = type;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Optional<String> templateId() {
        return Optional.ofNullable(templateId);
    }

    public List<String> appliedModelIds() {
        return List.copyOf(appliedModelIds);
    }

    public void setAppliedModelIds(List<String> modelIds) {
        appliedModelIds.clear();
        if (modelIds != null) {
            for (String modelId : modelIds) {
                if (modelId != null && !modelId.isBlank()) {
                    appliedModelIds.add(modelId);
                }
            }
        }
    }

    public void addAppliedModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        if (!appliedModelIds.contains(modelId)) {
            appliedModelIds.add(modelId);
        }
    }

    public String lastAppliedModelId() {
        if (appliedModelIds.isEmpty()) {
            return null;
        }
        return appliedModelIds.get(appliedModelIds.size() - 1);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long revision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public String lastChangedBy() {
        return lastChangedBy;
    }

    public void setLastChangedBy(String lastChangedBy) {
        this.lastChangedBy = lastChangedBy;
    }

    public Instant lastChangedAt() {
        return lastChangedAt;
    }

    public void setLastChangedAt(Instant lastChangedAt) {
        this.lastChangedAt = lastChangedAt;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean bindingAuditEnabled() {
        return bindingAuditEnabled;
    }

    public void setBindingAuditEnabled(boolean bindingAuditEnabled) {
        this.bindingAuditEnabled = bindingAuditEnabled;
    }

    public boolean functionAuditEnabled() {
        return functionAuditEnabled;
    }

    public void setFunctionAuditEnabled(boolean functionAuditEnabled) {
        this.functionAuditEnabled = functionAuditEnabled;
    }

    public Map<String, Variable> variables() {
        return Map.copyOf(variables);
    }

    public Map<String, FunctionDescriptor> functions() {
        return Map.copyOf(functions);
    }

    public Map<String, EventDescriptor> events() {
        return Map.copyOf(events);
    }

    public void addVariable(Variable variable) {
        variables.put(variable.name(), variable);
    }

    public void removeVariable(String name) {
        variables.remove(name);
    }

    public Optional<Variable> getVariable(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public void setVariableValue(String name, DataRecord value) {
        Variable variable = variables.get(name);
        if (variable == null) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
        variable.setValue(value);
    }

    public void addFunction(FunctionDescriptor function) {
        functions.put(function.name(), function);
    }

    public void removeFunction(String name) {
        functions.remove(name);
    }

    public void addEvent(EventDescriptor event) {
        events.put(event.name(), event);
    }

    public void removeEvent(String name) {
        events.remove(name);
    }

    public void updateInfo(String displayName, String description) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
