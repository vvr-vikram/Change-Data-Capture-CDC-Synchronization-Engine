package com.example.cdcsync.controller;

import com.example.cdcsync.model.FailedEvent;
import com.example.cdcsync.repository.FailedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync/failures")
public class FailedEventController {
    private static final Logger log = LoggerFactory.getLogger(FailedEventController.class);
    private static final String REDIS_IDEMPOTENCY_PREFIX = "processed:event:";

    private final FailedEventRepository failedEventRepository;
    private final KafkaOperations<String, String> kafkaTemplate;
    private final RedisOperations<String, String> redisTemplate;

    public FailedEventController(
            FailedEventRepository failedEventRepository,
            KafkaOperations<String, String> kafkaTemplate,
            RedisOperations<String, String> redisTemplate) {
        this.failedEventRepository = failedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public ResponseEntity<Page<FailedEvent>> getFailedEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FailedEvent> failures = failedEventRepository.findAll(
                PageRequest.of(page, size, Sort.by("failedAt").descending())
        );
        return ResponseEntity.ok(failures);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> retryFailedEvent(@PathVariable Long id) {
        FailedEvent failedEvent = failedEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Failed event not found with ID: " + id));

        if (Boolean.TRUE.equals(failedEvent.getResolved())) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Event is already resolved and retried.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String originalTopic = failedEvent.getTopic();
        // Remove .DLT suffix to get original topic name, e.g. cdc.public.orders.DLT -> cdc.public.orders
        if (originalTopic.endsWith(".DLT")) {
            originalTopic = originalTopic.substring(0, originalTopic.length() - 4);
        }

        String eventId = failedEvent.getEventId();
        String payload = failedEvent.getPayload();

        log.info("Manual retry triggered for failed event ID '{}'. Republishing to topic '{}'", eventId, originalTopic);

        // Clear Redis idempotency key to ensure consumer processes it
        redisTemplate.delete(REDIS_IDEMPOTENCY_PREFIX + eventId);

        // Publish message back to the original topic
        kafkaTemplate.send(originalTopic, payload);

        // Update failed event state in DB
        failedEvent.setResolved(true);
        failedEventRepository.save(failedEvent);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Event successfully republished for re-processing.");
        response.put("eventId", eventId);
        response.put("targetTopic", originalTopic);

        return ResponseEntity.ok(response);
    }
}
