package com.example.cdcsync.service;

import com.example.cdcsync.document.CustomerIndex;
import com.example.cdcsync.document.InventoryIndex;
import com.example.cdcsync.document.OrderIndex;
import com.example.cdcsync.document.ProductIndex;
import com.example.cdcsync.model.AuditLog;
import com.example.cdcsync.model.SyncStatus;
import com.example.cdcsync.repository.AuditLogRepository;
import com.example.cdcsync.repository.SyncStatusRepository;
import com.example.cdcsync.repository.elasticsearch.CustomerIndexRepository;
import com.example.cdcsync.repository.elasticsearch.InventoryIndexRepository;
import com.example.cdcsync.repository.elasticsearch.OrderIndexRepository;
import com.example.cdcsync.repository.elasticsearch.ProductIndexRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class CdcConsumerService {
    private static final Logger log = LoggerFactory.getLogger(CdcConsumerService.class);
    private static final String REDIS_IDEMPOTENCY_PREFIX = "processed:event:";

    private final CdcPayloadParser payloadParser;
    private final SyncStatusRepository syncStatusRepository;
    private final AuditLogRepository auditLogRepository;
    private final CustomerIndexRepository customerIndexRepository;
    private final ProductIndexRepository productIndexRepository;
    private final OrderIndexRepository orderIndexRepository;
    private final InventoryIndexRepository inventoryIndexRepository;
    private final RedisOperations<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    public CdcConsumerService(
            CdcPayloadParser payloadParser,
            SyncStatusRepository syncStatusRepository,
            AuditLogRepository auditLogRepository,
            CustomerIndexRepository customerIndexRepository,
            ProductIndexRepository productIndexRepository,
            OrderIndexRepository orderIndexRepository,
            InventoryIndexRepository inventoryIndexRepository,
            RedisOperations<String, String> redisTemplate,
            MeterRegistry meterRegistry) {
        this.payloadParser = payloadParser;
        this.syncStatusRepository = syncStatusRepository;
        this.auditLogRepository = auditLogRepository;
        this.customerIndexRepository = customerIndexRepository;
        this.productIndexRepository = productIndexRepository;
        this.orderIndexRepository = orderIndexRepository;
        this.inventoryIndexRepository = inventoryIndexRepository;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    // --- Kafka Listeners ---

    @KafkaListener(topics = "cdc.public.customers", groupId = "cdc-sync-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeCustomer(String message, Acknowledgment ack, @Header(name = "correlationId", required = false) String correlationId) {
        processCdcRecord("customers", message, ack, correlationId);
    }

    @KafkaListener(topics = "cdc.public.products", groupId = "cdc-sync-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeProduct(String message, Acknowledgment ack, @Header(name = "correlationId", required = false) String correlationId) {
        processCdcRecord("products", message, ack, correlationId);
    }

    @KafkaListener(topics = "cdc.public.orders", groupId = "cdc-sync-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrder(String message, Acknowledgment ack, @Header(name = "correlationId", required = false) String correlationId) {
        processCdcRecord("orders", message, ack, correlationId);
    }

    @KafkaListener(topics = "cdc.public.inventory", groupId = "cdc-sync-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeInventory(String message, Acknowledgment ack, @Header(name = "correlationId", required = false) String correlationId) {
        processCdcRecord("inventory", message, ack, correlationId);
    }

    // --- Core Sync Logic ---

    private void processCdcRecord(String entityType, String rawMessage, Acknowledgment ack, String correlationId) {
        // Set/Generate Correlation ID for distributed tracing
        String traceId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        MDC.put("correlationId", traceId);

        Timer.Sample sample = Timer.start(meterRegistry);
        CdcPayloadParser.CdcEvent event = null;

        try {
            log.info("Received CDC event for type '{}'", entityType);
            event = payloadParser.parse(rawMessage);
            String eventId = event.getEventId();

            // 1. Idempotency Check
            Boolean isDuplicate = redisTemplate.hasKey(REDIS_IDEMPOTENCY_PREFIX + eventId);
            if (Boolean.TRUE.equals(isDuplicate)) {
                log.info("Duplicate event detected (Idempotent filter). EventID: '{}'. Skipping...", eventId);
                incrementProcessedMetric(entityType, "SKIPPED");
                ack.acknowledge();
                return;
            }

            // 2. Initial state tracking
            SyncStatus syncStatus = initSyncStatus(event);

            // 3. Process operation sync to Elasticsearch
            try {
                syncToElasticsearch(event);

                // 4. Update status and Auditing upon SUCCESS
                updateStatusAndAudit(syncStatus, "PROCESSED", null, event, rawMessage);
                
                // 5. Caching idempotency token in Redis for 24h
                redisTemplate.opsForValue().set(REDIS_IDEMPOTENCY_PREFIX + eventId, "SUCCESS", Duration.ofHours(24));
                
                incrementProcessedMetric(entityType, "SUCCESS");
                ack.acknowledge();
                log.info("Successfully processed and synchronized CDC event '{}'", eventId);

            } catch (Exception e) {
                // Update status and Auditing upon FAILURE
                updateStatusAndAudit(syncStatus, "FAILED", e.getMessage(), event, rawMessage);
                incrementProcessedMetric(entityType, "FAILED");
                
                // Re-throw exception to trigger Spring Kafka retry and eventual DLQ routing
                throw e;
            }

        } catch (Exception e) {
            log.error("Error processing CDC event: {}", e.getMessage(), e);
            incrementFailureMetric(entityType);
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("cdc.events.processing.latency", "entity_type", entityType));
            MDC.remove("correlationId");
        }
    }

    private void syncToElasticsearch(CdcPayloadParser.CdcEvent event) {
        String entityType = event.getEntityType();
        String op = event.getOp();
        Map<String, Object> after = event.getAfter();
        Map<String, Object> before = event.getBefore();

        log.debug("Syncing entity '{}', op '{}' to Elasticsearch", entityType, op);

        if ("d".equalsIgnoreCase(op)) {
            // Delete operation
            String id = event.getEntityId();
            if ("customers".equals(entityType)) {
                customerIndexRepository.deleteById(id);
            } else if ("products".equals(entityType)) {
                productIndexRepository.deleteById(id);
            } else if ("orders".equals(entityType)) {
                orderIndexRepository.deleteById(id);
            } else if ("inventory".equals(entityType)) {
                inventoryIndexRepository.deleteById(id);
            }
            log.info("Deleted doc in ES. Entity: {}, ID: {}", entityType, id);
        } else {
            // Create, Read, Update operation
            if (after == null) {
                log.warn("Payload 'after' block is null for op '{}' in CDC event '{}'", op, event.getEventId());
                return;
            }
            String id = event.getEntityId();

            if ("customers".equals(entityType)) {
                CustomerIndex customerDoc = CustomerIndex.builder()
                        .id(id)
                        .name(after.get("name").toString())
                        .email(after.get("email").toString())
                        .phone(after.get("phone").toString())
                        .build();
                customerIndexRepository.save(customerDoc);
            } else if ("products".equals(entityType)) {
                ProductIndex productDoc = ProductIndex.builder()
                        .id(id)
                        .name(after.get("name").toString())
                        .description(after.get("description") != null ? after.get("description").toString() : null)
                        .price(new BigDecimal(after.get("price").toString()))
                        .sku(after.get("sku").toString())
                        .build();
                productIndexRepository.save(productDoc);
            } else if ("orders".equals(entityType)) {
                OrderIndex orderDoc = OrderIndex.builder()
                        .id(id)
                        .customerId(Long.valueOf(after.get("customer_id").toString()))
                        .status(after.get("status").toString())
                        .totalAmount(new BigDecimal(after.get("total_amount").toString()))
                        .build();
                orderIndexRepository.save(orderDoc);
            } else if ("inventory".equals(entityType)) {
                InventoryIndex inventoryDoc = InventoryIndex.builder()
                        .id(id)
                        .productId(Long.valueOf(after.get("product_id").toString()))
                        .quantity(Integer.valueOf(after.get("quantity").toString()))
                        .location(after.get("location").toString())
                        .build();
                inventoryIndexRepository.save(inventoryDoc);
            }
            log.info("Saved doc in ES. Entity: {}, ID: {}", entityType, id);
        }
    }

    @Transactional
    protected SyncStatus initSyncStatus(CdcPayloadParser.CdcEvent event) {
        return syncStatusRepository.findById(event.getEventId())
                .map(existing -> {
                    existing.setRetryCount(existing.getRetryCount() + 1);
                    return syncStatusRepository.save(existing);
                })
                .orElseGet(() -> syncStatusRepository.save(
                        SyncStatus.builder()
                                .eventId(event.getEventId())
                                .entityType(event.getEntityType())
                                .entityId(event.getEntityId())
                                .status("PENDING")
                                .retryCount(0)
                                .build()
                ));
    }

    @Transactional
    protected void updateStatusAndAudit(SyncStatus syncStatus, String status, String errorMsg, CdcPayloadParser.CdcEvent event, String rawPayload) {
        syncStatus.setStatus(status);
        syncStatus.setErrorMessage(errorMsg);
        syncStatusRepository.save(syncStatus);

        AuditLog auditLog = AuditLog.builder()
                .eventId(event.getEventId())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .op(event.getOp() != null ? event.getOp().toUpperCase() : "UNKNOWN")
                .payload(rawPayload)
                .status(status)
                .errorMessage(errorMsg)
                .build();
        auditLogRepository.save(auditLog);
    }

    private void incrementProcessedMetric(String entityType, String status) {
        Counter.builder("cdc.events.processed.count")
                .tag("entity_type", entityType)
                .tag("status", status)
                .description("Total number of CDC events processed")
                .register(meterRegistry)
                .increment();
    }

    private void incrementFailureMetric(String entityType) {
        Counter.builder("cdc.events.failed.count")
                .tag("entity_type", entityType)
                .description("Total number of CDC events that failed processing")
                .register(meterRegistry)
                .increment();
    }
}
