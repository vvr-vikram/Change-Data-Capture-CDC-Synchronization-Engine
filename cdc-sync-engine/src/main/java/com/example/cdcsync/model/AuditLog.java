package com.example.cdcsync.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cdc_audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String op; // CREATE, UPDATE, DELETE

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(insertable = false, updatable = false)
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(Long id, String eventId, String entityType, String entityId, String op, String payload, String status, String errorMessage, LocalDateTime timestamp) {
        this.id = id;
        this.eventId = eventId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.op = op;
        this.payload = payload;
        this.status = status;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private Long id;
        private String eventId;
        private String entityType;
        private String entityId;
        private String op;
        private String payload;
        private String status;
        private String errorMessage;
        private LocalDateTime timestamp;

        public AuditLogBuilder id(Long id) {
            this.id = id;
            return this;
        }
        public AuditLogBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        public AuditLogBuilder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }
        public AuditLogBuilder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }
        public AuditLogBuilder op(String op) {
            this.op = op;
            return this;
        }
        public AuditLogBuilder payload(String payload) {
            this.payload = payload;
            return this;
        }
        public AuditLogBuilder status(String status) {
            this.status = status;
            return this;
        }
        public AuditLogBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        public AuditLogBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public AuditLog build() {
            return new AuditLog(id, eventId, entityType, entityId, op, payload, status, errorMessage, timestamp);
        }
    }
}
