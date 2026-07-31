package com.example.cdcsync.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cdc_sync_status")
public class SyncStatus {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSED, FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public SyncStatus() {}

    public SyncStatus(String eventId, String entityType, String entityId, String status, String errorMessage, Integer retryCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.eventId = eventId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static SyncStatusBuilder builder() {
        return new SyncStatusBuilder();
    }

    public static class SyncStatusBuilder {
        private String eventId;
        private String entityType;
        private String entityId;
        private String status;
        private String errorMessage;
        private Integer retryCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public SyncStatusBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        public SyncStatusBuilder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }
        public SyncStatusBuilder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }
        public SyncStatusBuilder status(String status) {
            this.status = status;
            return this;
        }
        public SyncStatusBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        public SyncStatusBuilder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        public SyncStatusBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        public SyncStatusBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        public SyncStatus build() {
            return new SyncStatus(eventId, entityType, entityId, status, errorMessage, retryCount, createdAt, updatedAt);
        }
    }
}
