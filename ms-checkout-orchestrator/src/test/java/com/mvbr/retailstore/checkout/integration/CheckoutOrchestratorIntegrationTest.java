package com.mvbr.retailstore.checkout.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryCommittedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("testcontainers")
class CheckoutOrchestratorIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ms-checkout-orchestrator")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "ms-checkout-orchestrator-it");
        registry.add("kafka.topics.autoCreate", () -> "true");
        registry.add("kafka.topics.partitions", () -> "1");
        registry.add("kafka.topics.replicationFactor", () -> "1");
        registry.add("outbox.relay.fixedDelayMs", () -> "200");
        registry.add("outbox.retention.enabled", () -> "false");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void happy_path_publishes_commands_and_marks_saga_complete() throws Exception {
        String orderId = "order-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        sendOrderPlaced(orderId, correlationId);

        ConsumerRecord<String, String> inventoryReserve = pollForRecord(
                TopicNames.INVENTORY_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(inventoryReserve, HeaderNames.EVENT_TYPE)).isEqualTo("inventory.reserve");

        sendInventoryReserved(orderId, correlationId);

        ConsumerRecord<String, String> paymentAuthorize = pollForRecord(
                TopicNames.PAYMENT_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(paymentAuthorize, HeaderNames.EVENT_TYPE)).isEqualTo("payment.authorize");

        sendPaymentAuthorized(orderId, correlationId);

        ConsumerRecord<String, String> orderComplete = pollForRecord(
                TopicNames.ORDER_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(orderComplete, HeaderNames.EVENT_TYPE)).isEqualTo("order.complete");

        sendOrderCompleted(orderId, correlationId);

        ConsumerRecord<String, String> paymentCapture = pollForRecord(
                TopicNames.PAYMENT_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(paymentCapture, HeaderNames.EVENT_TYPE)).isEqualTo("payment.capture");

        sendPaymentCaptured(orderId, correlationId);

        ConsumerRecord<String, String> inventoryCommit = pollForRecord(
                TopicNames.INVENTORY_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(inventoryCommit, HeaderNames.EVENT_TYPE)).isEqualTo("inventory.commit");

        sendInventoryCommitted(orderId, correlationId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "select status from checkout_saga where order_id = ?",
                    String.class,
                    orderId
            );
            assertThat(status).isEqualTo("COMPLETED");
        });

        Integer processedCount = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where order_id = ?",
                Integer.class,
                orderId
        );
        assertThat(processedCount).isGreaterThanOrEqualTo(6);
    }

    @Test
    void inventory_rejected_publishes_cancel_and_marks_saga_cancelled() throws Exception {
        String orderId = "order-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        sendOrderPlaced(orderId, correlationId);

        pollForRecord(TopicNames.INVENTORY_COMMANDS_V1, orderId, Duration.ofSeconds(10));

        sendInventoryRejected(orderId, correlationId);

        ConsumerRecord<String, String> orderCancel = pollForRecord(
                TopicNames.ORDER_COMMANDS_V1,
                orderId,
                Duration.ofSeconds(10)
        );
        assertThat(header(orderCancel, HeaderNames.EVENT_TYPE)).isEqualTo("order.cancel");

        sendOrderCanceled(orderId, correlationId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "select status from checkout_saga where order_id = ?",
                    String.class,
                    orderId
            );
            assertThat(status).isEqualTo("CANCELED");
        });
    }

    private void sendOrderPlaced(String orderId, String correlationId) throws Exception {
        OrderPlacedEventV1 payload = new OrderPlacedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                "cust-1",
                List.of(
                        new OrderPlacedEventV1.Item("sku-1", 2, "10.00"),
                        new OrderPlacedEventV1.Item("sku-2", 1, "5.00")
                ),
                "22.50",
                "BRL",
                "2.50",
                "card"
        );

        sendEvent(
                TopicNames.ORDER_EVENTS_V1,
                orderId,
                "order.placed",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendInventoryReserved(String orderId, String correlationId) throws Exception {
        InventoryReservedEventV1 payload = new InventoryReservedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId
        );

        sendEvent(
                TopicNames.INVENTORY_EVENTS_V1,
                orderId,
                "inventory.reserved",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendPaymentAuthorized(String orderId, String correlationId) throws Exception {
        PaymentAuthorizedEventV1 payload = new PaymentAuthorizedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                "pay-1"
        );

        sendEvent(
                TopicNames.PAYMENT_EVENTS_V1,
                orderId,
                "payment.authorized",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendInventoryRejected(String orderId, String correlationId) throws Exception {
        InventoryRejectedEventV1 payload = new InventoryRejectedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                "out_of_stock"
        );

        sendEvent(
                TopicNames.INVENTORY_EVENTS_V1,
                orderId,
                "inventory.rejected",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendOrderCompleted(String orderId, String correlationId) throws Exception {
        OrderCompletedEventV1 payload = new OrderCompletedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId
        );

        sendEvent(
                TopicNames.ORDER_EVENTS_V1,
                orderId,
                "order.completed",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendPaymentCaptured(String orderId, String correlationId) throws Exception {
        PaymentCapturedEventV1 payload = new PaymentCapturedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                "pay-1",
                "pi-1"
        );

        sendEvent(
                TopicNames.PAYMENT_EVENTS_V1,
                orderId,
                "payment.captured",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendInventoryCommitted(String orderId, String correlationId) throws Exception {
        InventoryCommittedEventV1 payload = new InventoryCommittedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId
        );

        sendEvent(
                TopicNames.INVENTORY_EVENTS_V1,
                orderId,
                "inventory.committed",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendOrderCanceled(String orderId, String correlationId) throws Exception {
        OrderCanceledEventV1 payload = new OrderCanceledEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId
        );

        sendEvent(
                TopicNames.ORDER_EVENTS_V1,
                orderId,
                "order.canceled",
                payload.eventId(),
                payload,
                correlationId
        );
    }

    private void sendEvent(String topic,
                           String key,
                           String eventType,
                           String eventId,
                           Object payload,
                           String correlationId) throws Exception {
        String json = objectMapper.writeValueAsString(payload);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add(HeaderNames.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.CAUSATION_ID, eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.AGGREGATE_TYPE, "Order".getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.AGGREGATE_ID, key.getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.PRODUCER, "test".getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.SCHEMA_VERSION, "v1".getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.TOPIC_VERSION, "v1".getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).get();
    }

    private ConsumerRecord<String, String> pollForRecord(String topic,
                                                         String key,
                                                         Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError("Did not receive record for topic=" + topic + " key=" + key);
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
