package com.example.cdcsync.service;

import com.example.cdcsync.model.FailedEvent;
import com.example.cdcsync.repository.FailedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class DlqConsumerService {
    private static final Logger log = LoggerFactory.getLogger(DlqConsumerService.class);

    private final FailedEventRepository failedEventRepository;
    private final CdcPayloadParser payloadParser;

    public DlqConsumerService(FailedEventRepository failedEventRepository, CdcPayloadParser payloadParser) {
        this.failedEventRepository = failedEventRepository;
        this.payloadParser = payloadParser;
    }

    @Transactional
    @KafkaListener(
            topicPattern = "cdc\\.public\\..*\\.DLT",
            groupId = "cdc-dlq-group",
            properties = {"spring.json.value.default.type=java.lang.String"}
    )
    public void consumeDlq(
            ConsumerRecord<String, String> record,
            Acknowledgment ack,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) byte[] exceptionMessageBytes) {
        
        String exceptionMessage = "Unknown exception";
        if (exceptionMessageBytes != null) {
            exceptionMessage = new String(exceptionMessageBytes, StandardCharsets.UTF_8);
        }

        log.error("DLQ Consumer captured failed event on topic '{}', partition {}, offset {}. Exception: {}",
                record.topic(), record.partition(), record.offset(), exceptionMessage);

        String rawPayload = record.value();
        String eventId = "dlt-unknown-" + java.util.UUID.randomUUID();

        try {
            CdcPayloadParser.CdcEvent event = payloadParser.parse(rawPayload);
            eventId = event.getEventId();
        } catch (Exception e) {
            log.warn("Failed to parse DLQ payload to extract Event ID. Using generated ID. Error: {}", e.getMessage());
        }

        // Save to PostgreSQL failed_events table
        FailedEvent failedEvent = FailedEvent.builder()
                .eventId(eventId)
                .topic(record.topic())
                .partitionId(record.partition())
                .offsetVal(record.offset())
                .payload(rawPayload)
                .errorMessage(exceptionMessage)
                .resolved(false)
                .build();

        failedEventRepository.save(failedEvent);
        log.info("Saved DLQ event '{}' to postgres failed_events table", eventId);

        // Acknowledge the DLQ topic offset
        ack.acknowledge();
    }
}
