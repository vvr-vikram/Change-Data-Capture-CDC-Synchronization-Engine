package com.example.cdcsync.controller;

import com.example.cdcsync.model.AuditLog;
import com.example.cdcsync.model.SyncStatus;
import com.example.cdcsync.repository.AuditLogRepository;
import com.example.cdcsync.repository.SyncStatusRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sync")
public class SyncStatusController {

    private final SyncStatusRepository syncStatusRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisOperations<String, String> redisTemplate;

    public SyncStatusController(
            SyncStatusRepository syncStatusRepository,
            AuditLogRepository auditLogRepository,
            RedisOperations<String, String> redisTemplate) {
        this.syncStatusRepository = syncStatusRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/status")
    public ResponseEntity<List<SyncStatus>> getAllStatuses() {
        return ResponseEntity.ok(syncStatusRepository.findAll());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditLog> auditLogs = auditLogRepository.findAll(
                PageRequest.of(page, size, Sort.by("timestamp").descending())
        );
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSyncStatistics() {
        List<SyncStatus> statuses = syncStatusRepository.findAll();
        
        long totalEvents = statuses.size();
        long processed = statuses.stream().filter(s -> "PROCESSED".equals(s.getStatus())).count();
        long failed = statuses.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long pending = statuses.stream().filter(s -> "PENDING".equals(s.getStatus())).count();

        // Get key count in Redis idempotency cache
        Set<String> redisKeys = redisTemplate.keys("processed:event:*");
        long cachedEventCount = redisKeys != null ? redisKeys.size() : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEventsCaptured", totalEvents);
        stats.put("processedSuccessfully", processed);
        stats.put("failedProcessing", failed);
        stats.put("pendingProcessing", pending);
        stats.put("idempotencyCacheSize", cachedEventCount);
        stats.put("successRatePercentage", totalEvents > 0 ? (double) processed / totalEvents * 100 : 0.0);

        return ResponseEntity.ok(stats);
    }
}
