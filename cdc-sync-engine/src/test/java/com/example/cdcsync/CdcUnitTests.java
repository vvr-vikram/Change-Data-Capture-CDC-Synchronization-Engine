package com.example.cdcsync;

import com.example.cdcsync.controller.AuthController;
import com.example.cdcsync.controller.FailedEventController;
import com.example.cdcsync.controller.SyncStatusController;
import com.example.cdcsync.document.CustomerIndex;
import com.example.cdcsync.model.AuditLog;
import com.example.cdcsync.model.FailedEvent;
import com.example.cdcsync.model.SyncStatus;
import com.example.cdcsync.repository.AuditLogRepository;
import com.example.cdcsync.repository.FailedEventRepository;
import com.example.cdcsync.repository.SyncStatusRepository;
import com.example.cdcsync.repository.elasticsearch.CustomerIndexRepository;
import com.example.cdcsync.repository.elasticsearch.InventoryIndexRepository;
import com.example.cdcsync.repository.elasticsearch.OrderIndexRepository;
import com.example.cdcsync.repository.elasticsearch.ProductIndexRepository;
import com.example.cdcsync.security.JwtTokenProvider;
import com.example.cdcsync.service.CdcConsumerService;
import com.example.cdcsync.service.CdcPayloadParser;
import com.example.cdcsync.service.DlqConsumerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CdcUnitTests {

    @Mock
    private SyncStatusRepository syncStatusRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private CustomerIndexRepository customerIndexRepository;
    @Mock
    private ProductIndexRepository productIndexRepository;
    @Mock
    private OrderIndexRepository orderIndexRepository;
    @Mock
    private InventoryIndexRepository inventoryIndexRepository;
    @Mock
    private RedisOperations<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private MeterRegistry meterRegistry;
    
    @Mock
    private Acknowledgment acknowledgment;
    @Mock
    private FailedEventRepository failedEventRepository;
    @Mock
    private KafkaOperations<String, String> kafkaTemplate;
    @Mock
    private AuthenticationManager authenticationManager;

    private CdcPayloadParser payloadParser;
    private CdcConsumerService consumerService;
    private DlqConsumerService dlqConsumerService;
    private JwtTokenProvider jwtTokenProvider;
    private AuthController authController;
    private SyncStatusController syncStatusController;
    private FailedEventController failedEventController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        payloadParser = new CdcPayloadParser();
        jwtTokenProvider = new JwtTokenProvider(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 
                3600000
        );

        // Mock Redis Operations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Use real SimpleMeterRegistry to avoid Mockito issues on modern JDKs
        meterRegistry = new SimpleMeterRegistry();

        consumerService = new CdcConsumerService(
                payloadParser, syncStatusRepository, auditLogRepository,
                customerIndexRepository, productIndexRepository, orderIndexRepository,
                inventoryIndexRepository, redisTemplate, meterRegistry
        );

        dlqConsumerService = new DlqConsumerService(failedEventRepository, payloadParser);
        
        authController = new AuthController(authenticationManager, jwtTokenProvider);
        
        syncStatusController = new SyncStatusController(syncStatusRepository, auditLogRepository, redisTemplate);
        
        failedEventController = new FailedEventController(failedEventRepository, kafkaTemplate, redisTemplate);
    }

    @Test
    public void testPayloadParser_success() {
        String json = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"after\": {\n" +
                "      \"id\": 12,\n" +
                "      \"name\": \"Vikram\"\n" +
                "    },\n" +
                "    \"source\": {\n" +
                "      \"table\": \"customers\",\n" +
                "      \"lsn\": 999\n" +
                "    }\n" +
                "  }\n" +
                "}";
        CdcPayloadParser.CdcEvent event = payloadParser.parse(json);
        assertThat(event.getOp()).isEqualTo("c");
        assertThat(event.getEntityType()).isEqualTo("customers");
        assertThat(event.getEntityId()).isEqualTo("12");
        assertThat(event.getEventId()).isEqualTo("evt:customers:999:0");
    }

    @Test
    public void testConsumerService_successProcessing() {
        String json = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"after\": {\n" +
                "      \"id\": 10,\n" +
                "      \"name\": \"Magesh\",\n" +
                "      \"email\": \"magesh@example.com\",\n" +
                "      \"phone\": \"987654\"\n" +
                "    },\n" +
                "    \"source\": {\n" +
                "      \"table\": \"customers\",\n" +
                "      \"lsn\": 123\n" +
                "    }\n" +
                "  }\n" +
                "}";

        // Idempotency: Redis returns false (not duplicate)
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        
        // Sync Status Repository mock
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        when(syncStatusRepository.save(any(SyncStatus.class))).thenAnswer(i -> i.getArguments()[0]);

        consumerService.consumeCustomer(json, acknowledgment, "trace-123");

        // Verify ES Sync called
        verify(customerIndexRepository, times(1)).save(any(CustomerIndex.class));
        
        // Verify Postgres state saved
        verify(syncStatusRepository, times(2)).save(any(SyncStatus.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        
        // Verify Redis caching of idempotency key
        verify(valueOperations, times(1)).set(eq("processed:event:evt:customers:123:0"), eq("SUCCESS"), any());
        
        // Verify Ack called
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    public void testConsumerService_duplicateSkipped() {
        String json = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"after\": {\"id\": 10, \"name\": \"Magesh\"},\n" +
                "    \"source\": {\"table\": \"customers\", \"lsn\": 123}\n" +
                "  }\n" +
                "}";

        // Idempotency: Redis returns true (duplicate)
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        consumerService.consumeCustomer(json, acknowledgment, "trace-123");

        // Verify ES and Postgres NOT called
        verify(customerIndexRepository, never()).save(any(CustomerIndex.class));
        verify(syncStatusRepository, never()).save(any(SyncStatus.class));

        // Verify Ack is still called to commit duplicate record offset
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    public void testConsumerService_exceptionTriggersRollbackAndRethrow() {
        String json = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"after\": {\"id\": 10, \"name\": \"Magesh\", \"email\": \"magesh@example.com\", \"phone\": \"123\"},\n" +
                "    \"source\": {\"table\": \"customers\", \"lsn\": 123}\n" +
                "  }\n" +
                "}";

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        when(syncStatusRepository.save(any(SyncStatus.class))).thenAnswer(i -> i.getArguments()[0]);

        // Force exception in ES indexing
        doThrow(new RuntimeException("Elasticsearch connection failed"))
                .when(customerIndexRepository).save(any(CustomerIndex.class));

        // Consume should throw the exception
        assertThrows(RuntimeException.class, () -> {
            consumerService.consumeCustomer(json, acknowledgment, "trace-123");
        });

        // Verify fail state is logged to PostgreSQL
        verify(syncStatusRepository, atLeast(2)).save(any(SyncStatus.class));
        ArgumentCaptor<SyncStatus> statusCaptor = ArgumentCaptor.forClass(SyncStatus.class);
        verify(syncStatusRepository, atLeast(2)).save(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getStatus()).isEqualTo("FAILED");

        // Verify offset is NOT acknowledged (no ack)
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    public void testDlqConsumerService_success() {
        String rawPayload = "{\"payload\": {\"op\": \"c\", \"after\": {\"id\": 99}, \"source\": {\"table\": \"orders\", \"lsn\": 555}}}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("cdc.public.orders", 0, 100L, "key", rawPayload);
        
        byte[] exceptionBytes = "Database deadlock occurred".getBytes();

        dlqConsumerService.consumeDlq(record, acknowledgment, exceptionBytes);

        // Verify failed event saved to PostgreSQL failed_events table
        ArgumentCaptor<FailedEvent> eventCaptor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository, times(1)).save(eventCaptor.capture());
        
        FailedEvent saved = eventCaptor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt:orders:555:0");
        assertThat(saved.getTopic()).isEqualTo("cdc.public.orders");
        assertThat(saved.getErrorMessage()).isEqualTo("Database deadlock occurred");
        assertThat(saved.getResolved()).isFalse();

        // Verify offset acknowledged
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    public void testAuthController_loginSuccess() {
        AuthController.LoginRequest request = new AuthController.LoginRequest("admin", "admin123");
        Authentication auth = mock(Authentication.class);
        
        org.springframework.security.core.userdetails.User principal = new User(
                "admin", "password", 
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(principal);

        ResponseEntity<AuthController.JwtResponse> response = authController.authenticateUser(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
    }

    @Test
    public void testSyncStatusController_endpoints() {
        SyncStatus status1 = SyncStatus.builder().eventId("evt-1").entityType("orders").entityId("1").status("PROCESSED").retryCount(0).build();
        SyncStatus status2 = SyncStatus.builder().eventId("evt-2").entityType("customers").entityId("2").status("FAILED").retryCount(1).build();

        when(syncStatusRepository.findAll()).thenReturn(Arrays.asList(status1, status2));
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>(Collections.singletonList("processed:event:evt-1")));

        ResponseEntity<List<SyncStatus>> statuses = syncStatusController.getAllStatuses();
        assertThat(statuses.getBody()).hasSize(2);

        ResponseEntity<Map<String, Object>> stats = syncStatusController.getSyncStatistics();
        assertThat(stats.getBody().get("totalEventsCaptured")).isEqualTo(2L);
        assertThat(stats.getBody().get("processedSuccessfully")).isEqualTo(1L);
        assertThat(stats.getBody().get("failedProcessing")).isEqualTo(1L);
        assertThat(stats.getBody().get("idempotencyCacheSize")).isEqualTo(1L);
    }

    @Test
    public void testFailedEventController_retrySuccess() {
        FailedEvent failedEvent = FailedEvent.builder()
                .id(1L)
                .eventId("evt-dlt-1")
                .topic("cdc.public.orders.DLT")
                .partitionId(0)
                .offsetVal(12L)
                .payload("raw-payload-string")
                .errorMessage("Error message")
                .resolved(false)
                .build();

        when(failedEventRepository.findById(1L)).thenReturn(Optional.of(failedEvent));

        ResponseEntity<Map<String, Object>> response = failedEventController.retryFailedEvent(1L);

        // Verify Redis key cleared
        verify(redisTemplate, times(1)).delete("processed:event:evt-dlt-1");

        // Verify republished to original topic (without .DLT)
        verify(kafkaTemplate, times(1)).send(eq("cdc.public.orders"), eq("raw-payload-string"));

        // Verify database marked resolved
        assertThat(failedEvent.getResolved()).isTrue();
        verify(failedEventRepository, times(1)).save(failedEvent);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
    }
}
