package com.example.cdcsync.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class DebeziumConnectorConfigurator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DebeziumConnectorConfigurator.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Value("${debezium.connect.url}")
    private String connectUrl;

    @Value("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/orders_db}")
    private String dbUrl;

    @Override
    public void run(ApplicationArguments args) {
        executor.submit(this::configureConnectorWithRetry);
    }

    private void configureConnectorWithRetry() {
        String connectorName = "postgres-cdc-connector";
        String registerUrl = connectUrl + "/connectors";
        String statusUrl = registerUrl + "/" + connectorName + "/status";

        log.info("Starting Debezium connector registration background thread targeting: {}", connectUrl);

        int maxAttempts = 30;
        int delayMs = 5000;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Check if Kafka Connect is healthy
                ResponseEntity<String> response = restTemplate.getForEntity(registerUrl, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Kafka Connect is healthy and available.");

                    // Check if connector already exists
                    boolean exists = false;
                    try {
                        ResponseEntity<String> statusResponse = restTemplate.getForEntity(statusUrl, String.class);
                        if (statusResponse.getStatusCode().is2xxSuccessful()) {
                            exists = true;
                            log.info("Debezium Postgres connector '{}' already registered.", connectorName);
                        }
                    } catch (Exception e) {
                        // Connector does not exist (404), which is expected for first run
                    }

                    if (!exists) {
                        registerPostgresConnector(registerUrl, connectorName);
                    }
                    break;
                }
            } catch (Exception e) {
                log.warn("Kafka Connect not ready (Attempt {}/{}). Retrying in {}ms... Error: {}", 
                        attempt, maxAttempts, delayMs, e.getMessage());
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Connector registration thread was interrupted.");
                break;
            }
        }
    }

    private void registerPostgresConnector(String registerUrl, String name) {
        log.info("Registering Debezium Postgres connector...");

        // Determine DB Host from JDBC URL (defaults to postgres-db inside docker compose network)
        String dbHost = "postgres";
        if (dbUrl.contains("localhost")) {
            // If the spring app itself was configured with localhost, it might be running locally.
            // But Debezium runs in Docker, so Debezium must connect to postgres using 'postgres'.
            dbHost = "postgres"; 
        } else {
            // Extract from jdbc URL if possible, e.g. jdbc:postgresql://postgres:5432/orders_db
            try {
                String hostPart = dbUrl.substring(dbUrl.indexOf("//") + 2);
                dbHost = hostPart.substring(0, hostPart.indexOf(":"));
            } catch (Exception e) {
                dbHost = "postgres";
            }
        }

        Map<String, Object> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        config.put("tasks.max", "1");
        config.put("database.hostname", dbHost);
        config.put("database.port", "5432");
        config.put("database.user", "postgres");
        config.put("database.password", "postgres");
        config.put("database.dbname", "orders_db");
        config.put("topic.prefix", "cdc");
        config.put("plugin.name", "pgoutput");
        config.put("table.include.list", "public.orders,public.customers,public.products,public.inventory,public.payments");
        
        // Wait, Debezium Postgres connector doesn't strictly need database.history.kafka.* in newer versions,
        // but topic.prefix and plugin.name are crucial. Let's make sure it is configured correctly.
        config.put("slot.name", "debezium_logical_slot");
        config.put("publication.name", "debezium_publication");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("config", config);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(registerUrl, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully registered Debezium Postgres connector '{}'.", name);
            } else {
                log.error("Failed to register connector. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception during Debezium connector registration: {}", e.getMessage(), e);
        }
    }
}
