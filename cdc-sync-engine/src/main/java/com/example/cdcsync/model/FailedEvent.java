package com.example.cdcsync.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
public class FailedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    @Column(name = "offset_val", nullable = false)
    private Long offsetVal;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failed_at", insertable = false, updatable = false)
    private LocalDateTime failedAt;

    @Column(nullable = false)
    private Boolean resolved;

    public FailedEvent() {}

    public FailedEvent(Long id, String eventId, String topic, Integer partitionId, Long offsetVal, String payload, String errorMessage, LocalDateTime failedAt, Boolean resolved) {
        this.id = id;
        this.eventId = eventId;
        this.topic = topic;
        this.partitionId = partitionId;
        this.offsetVal = offsetVal;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.failedAt = failedAt;
        this.resolved = resolved;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Integer getPartitionId() { return partitionId; }
    public void setPartitionId(Integer partitionId) { this.partitionId = partitionId; }

    public Long getOffsetVal() { return offsetVal; }
    public void setOffsetVal(Long offsetVal) { this.offsetVal = offsetVal; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }

    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }

    public static FailedEventBuilder builder() {
        return new FailedEventBuilder();
    }

    public static class FailedEventBuilder {
        private Long id;
        private String eventId;
        private String topic;
        private Integer partitionId;
        private Long offsetVal;
        private String payload;
        private String errorMessage;
        private LocalDateTime failedAt;
        private Boolean resolved;

        public FailedEventBuilder id(Long id) {
            this.id = id;
            return this;
        }
        public FailedEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        public FailedEventBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }
        public FailedEventBuilder partitionId(Integer partitionId) {
            this.partitionId = partitionId;
            return this;
        }
        public FailedEventBuilder offsetVal(Long offsetVal) {
            this.offsetVal = offsetVal;
            return this;
        }
        public FailedEventBuilder payload(String payload) {
            this.payload = payload;
            return this;
        }
        public FailedEventBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        public FailedEventBuilder failedAt(LocalDateTime failedAt) {
            this.failedAt = failedAt;
            return this;
        }
        public FailedEventBuilder resolved(Boolean resolved) {
            this.resolved = resolved;
            return this;
        }
        public FailedEvent build() {
            return new FailedEvent(id, eventId, topic, partitionId, offsetVal, payload, errorMessage, failedAt, resolved);
        }
    }
}
