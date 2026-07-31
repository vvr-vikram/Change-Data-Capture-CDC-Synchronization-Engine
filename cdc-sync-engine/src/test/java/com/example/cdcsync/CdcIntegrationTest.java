package com.example.cdcsync;

import com.example.cdcsync.document.CustomerIndex;
import com.example.cdcsync.model.AuditLog;
import com.example.cdcsync.model.FailedEvent;
import com.example.cdcsync.model.SyncStatus;
import com.example.cdcsync.repository.AuditLogRepository;
import com.example.cdcsync.repository.FailedEventRepository;
import com.example.cdcsync.repository.SyncStatusRepository;
import com.example.cdcsync.repository.elasticsearch.CustomerIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class CdcIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.4-alpine"))
            .withExposedPorts(6379);

    @Container
    public static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.3")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private CustomerIndexRepository customerIndexRepository;

    @Autowired
    private SyncStatusRepository syncStatusRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FailedEventRepository failedEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String adminToken;
    private String operatorToken;

    @BeforeEach
    public void setup() throws Exception {
        // Log in to get tokens
        adminToken = obtainJwtToken("admin", "admin123");
        operatorToken = obtainJwtToken("operator", "operator123");

        // Clear repositories before each test
        customerIndexRepository.deleteAll();
        syncStatusRepository.deleteAll();
        auditLogRepository.deleteAll();
        failedEventRepository.deleteAll();
        
        // Clear Redis
        redisTemplate.delete(redisTemplate.keys("*"));
    }

    private String obtainJwtToken(String username, String password) throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        return "Bearer " + responseMap.get("token");
    }

    @Test
    public void testAuthenticationAndAuthorization() throws Exception {
        // Unauthenticated access
        mockMvc.perform(get("/api/sync/status"))
                .andExpect(status().isForbidden());

        // Authenticated access (Operator)
        mockMvc.perform(get("/api/sync/status")
                        .header("Authorization", operatorToken))
                .andExpect(status().isOk());

        // Admin only endpoint access denied for operator
        mockMvc.perform(post("/api/sync/failures/1/retry")
                        .header("Authorization", operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testEndToEndCdcConsumptionAndElasticsearchSync() throws Exception {
        // 1. Arrange: Create a mock Debezium JSON event for customers
        String mockCdcMessage = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"before\": null,\n" +
                "    \"after\": {\n" +
                "      \"id\": 101,\n" +
                "      \"name\": \"Vikram Dev\",\n" +
                "      \"email\": \"vikramdev@example.com\",\n" +
                "      \"phone\": \"9876500000\"\n" +
                "    },\n" +
                "    \"source\": {\n" +
                "      \"table\": \"customers\",\n" +
                "      \"lsn\": 123456,\n" +
                "      \"ts_ms\": 1690000000000\n" +
                "    }\n" +
                "  }\n" +
                "}";

        // 2. Act: Publish message to Kafka topic
        kafkaTemplate.send("cdc.public.customers", mockCdcMessage);

        // 3. Assert: Wait and verify sync in Elasticsearch
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<CustomerIndex> customerOpt = customerIndexRepository.findById("101");
            assertThat(customerOpt).isPresent();
            assertThat(customerOpt.get().getName()).isEqualTo("Vikram Dev");
            assertThat(customerOpt.get().getEmail()).isEqualTo("vikramdev@example.com");
        });

        // 4. Assert: Verify sync status table in PostgreSQL
        String eventId = "evt:customers:123456:1690000000000";
        Optional<SyncStatus> statusOpt = syncStatusRepository.findById(eventId);
        assertThat(statusOpt).isPresent();
        assertThat(statusOpt.get().getStatus()).isEqualTo("PROCESSED");

        // 5. Assert: Verify audit log entry in PostgreSQL
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).isNotEmpty();
        assertThat(auditLogs.get(0).getEventId()).isEqualTo(eventId);
        assertThat(auditLogs.get(0).getOp()).isEqualTo("C");

        // 6. Assert: Verify Redis cache entry for idempotency
        Boolean hasKey = redisTemplate.hasKey("processed:event:" + eventId);
        assertThat(hasKey).isTrue();

        // 7. Act Again: Send duplicate event to verify Idempotency (should be skipped and not re-indexed)
        customerIndexRepository.deleteAll(); // Delete from ES to prove it's not indexed again
        kafkaTemplate.send("cdc.public.customers", mockCdcMessage);

        Thread.sleep(2000); // Wait a brief moment to ensure duplicate is consumed and skipped
        Optional<CustomerIndex> shouldBeEmpty = customerIndexRepository.findById("101");
        assertThat(shouldBeEmpty).isEmpty(); // Proves duplicate was skipped!
    }

    @Test
    public void testFailedEventRoutingToDlqAndManualAdminRetry() throws Exception {
        // 1. Arrange: Send a malformed message (causing parsing/processing error)
        String malformedCdcMessage = "{\n" +
                "  \"payload\": {\n" +
                "    \"op\": \"c\",\n" +
                "    \"before\": null,\n" +
                "    \"after\": {\n" +
                "      \"id\": 202,\n" +
                "      \"name\": \"Bala Nair\",\n" +
                "      \"email\": null,\n" + // Null email will cause NPE in mapping or validation
                "      \"phone\": \"9876511111\"\n" +
                "    },\n" +
                "    \"source\": {\n" +
                "      \"table\": \"customers\",\n" +
                "      \"lsn\": 654321,\n" +
                "      \"ts_ms\": 1690000000001\n" +
                "    }\n" +
                "  }\n" +
                "}";

        // 2. Act: Send malformed record to Kafka
        kafkaTemplate.send("cdc.public.customers", malformedCdcMessage);

        // 3. Assert: Wait for DLT processing and verify FailedEvent record in DB
        String eventId = "evt:customers:654321:1690000000001";
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<FailedEvent> failedEventOpt = failedEventRepository.findByEventId(eventId);
            assertThat(failedEventOpt).isPresent();
            assertThat(failedEventOpt.get().getResolved()).isFalse();
            assertThat(failedEventOpt.get().getTopic()).isEqualTo("cdc.public.customers.DLT");
        });

        // 4. Assert: Get failed event ID
        FailedEvent failedEvent = failedEventRepository.findByEventId(eventId).get();

        // 5. Act: Correct the payload data manually and perform Admin retry simulation
        // In the real system, the admin edits the payload or triggers retry on the existing payload.
        // Let's first test that calling the retry endpoint on this record republishes it.
        mockMvc.perform(post("/api/sync/failures/" + failedEvent.getId() + "/retry")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 6. Assert: Verify the FailedEvent is now marked as resolved
        Optional<FailedEvent> updatedFailedEvent = failedEventRepository.findById(failedEvent.getId());
        assertThat(updatedFailedEvent).isPresent();
        assertThat(updatedFailedEvent.get().getResolved()).isTrue();
    }
}
