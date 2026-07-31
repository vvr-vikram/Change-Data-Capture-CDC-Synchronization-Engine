package com.example.cdcsync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class CdcPayloadParser {
    private static final Logger log = LoggerFactory.getLogger(CdcPayloadParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class CdcEvent {
        private final String op;
        private final Map<String, Object> before;
        private final Map<String, Object> after;
        private final Map<String, Object> source;
        private final String eventId;
        private final String entityId;
        private final String entityType;

        public CdcEvent(String op, Map<String, Object> before, Map<String, Object> after, Map<String, Object> source) {
            this.op = op;
            this.before = before;
            this.after = after;
            this.source = source;
            this.eventId = generateEventId();
            this.entityType = extractEntityType();
            this.entityId = extractEntityId();
        }

        private String generateEventId() {
            if (source != null) {
                Object lsn = source.get("lsn");
                Object tsMs = source.get("ts_ms");
                Object table = source.get("table");
                if (table != null) {
                    String prefix = "evt:" + table + ":";
                    if (lsn != null) {
                        return prefix + lsn + ":" + (tsMs != null ? tsMs : "0");
                    }
                    if (tsMs != null) {
                        return prefix + tsMs;
                    }
                }
            }
            return "evt:gen:" + java.util.UUID.randomUUID();
        }

        private String extractEntityType() {
            if (source != null && source.get("table") != null) {
                return source.get("table").toString();
            }
            return "unknown";
        }

        private String extractEntityId() {
            Map<String, Object> target = (after != null) ? after : before;
            if (target != null && target.get("id") != null) {
                return target.get("id").toString();
            }
            return "unknown";
        }

        public String getOp() { return op; }
        public Map<String, Object> getBefore() { return before; }
        public Map<String, Object> getAfter() { return after; }
        public Map<String, Object> getSource() { return source; }
        public String getEventId() { return eventId; }
        public String getEntityId() { return entityId; }
        public String getEntityType() { return entityType; }

        @Override
        public String toString() {
            return "CdcEvent{" +
                    "op='" + op + '\'' +
                    ", eventId='" + eventId + '\'' +
                    ", entityId='" + entityId + '\'' +
                    ", entityType='" + entityType + '\'' +
                    '}';
        }
    }

    public CdcEvent parse(String rawJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(rawJson, new TypeReference<>() {});
            
            // Check if Debezium message is wrapped inside "payload"
            Map<String, Object> payload = root;
            if (root.containsKey("payload") && root.get("payload") instanceof Map) {
                payload = (Map<String, Object>) root.get("payload");
            }

            String op = (String) payload.get("op");
            Map<String, Object> before = (Map<String, Object>) payload.get("before");
            Map<String, Object> after = (Map<String, Object>) payload.get("after");
            Map<String, Object> source = (Map<String, Object>) payload.get("source");

            return new CdcEvent(op, before, after, source);
        } catch (Exception e) {
            log.error("Failed to parse raw CDC payload JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid CDC message format", e);
        }
    }
}
