package com.ispf.server.persistence.entity;

import com.ispf.core.object.ObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "object_nodes")
public class ObjectNodeEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 512)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ObjectType type;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "template_id", length = 128)
    private String templateId;

    @Column(name = "applied_model_ids", columnDefinition = "TEXT")
    private String appliedModelIdsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "events_json", columnDefinition = "TEXT")
    private String eventsJson;

    @Column(name = "functions_json", columnDefinition = "TEXT")
    private String functionsJson;

    @Column(nullable = false)
    private long revision;

    @Column(name = "last_changed_by", length = 128)
    private String lastChangedBy;

    @Column(name = "last_changed_at")
    private Instant lastChangedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ObjectType getType() {
        return type;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getAppliedModelIdsJson() {
        return appliedModelIdsJson;
    }

    public void setAppliedModelIdsJson(String appliedModelIdsJson) {
        this.appliedModelIdsJson = appliedModelIdsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getEventsJson() {
        return eventsJson;
    }

    public void setEventsJson(String eventsJson) {
        this.eventsJson = eventsJson;
    }

    public String getFunctionsJson() {
        return functionsJson;
    }

    public void setFunctionsJson(String functionsJson) {
        this.functionsJson = functionsJson;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public String getLastChangedBy() {
        return lastChangedBy;
    }

    public void setLastChangedBy(String lastChangedBy) {
        this.lastChangedBy = lastChangedBy;
    }

    public Instant getLastChangedAt() {
        return lastChangedAt;
    }

    public void setLastChangedAt(Instant lastChangedAt) {
        this.lastChangedAt = lastChangedAt;
    }
}
