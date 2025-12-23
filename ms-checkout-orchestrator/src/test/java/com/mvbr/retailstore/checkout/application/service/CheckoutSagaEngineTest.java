package com.mvbr.retailstore.checkout.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.application.port.out.ProcessedEventRepository;
import com.mvbr.retailstore.checkout.config.SagaProperties;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSagaEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SagaProperties sagaProperties = new SagaProperties();

    @Test
    void handle_orderPlaced_creates_saga_and_publishes_inventory_reserve() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        OrderPlacedEventV1 payload = orderPlacedEvent("evt-1", "order-1");
        EventEnvelope env = envelope("order.placed", "evt-1", "order-1", payload, "");

        engine.handle(env);

        CheckoutSaga saga = sagaRepository.get("order-1");
        assertThat(saga.getStep()).isEqualTo(SagaStep.WAIT_INVENTORY);
        assertThat(saga.getAmount()).isEqualTo("22.50");
        assertThat(saga.getCurrency()).isEqualTo("BRL");
        assertThat(saga.getCorrelationId()).isEqualTo("order-1");

        assertThat(publisher.published()).hasSize(1);
        PublishedCommand command = publisher.published().getFirst();
        assertThat(command.topic()).isEqualTo(TopicNames.INVENTORY_COMMANDS_V1);
        assertThat(command.key()).isEqualTo("order-1");
        assertThat(command.commandType()).isEqualTo("inventory.reserve");

        var payloadCmd = (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1)
                command.payload();
        assertThat(command.headers().get(HeaderNames.EVENT_ID)).isEqualTo(payloadCmd.commandId());
        assertThat(command.headers().get(HeaderNames.COMMAND_ID)).isEqualTo(payloadCmd.commandId());
        assertThat(command.headers().get(HeaderNames.CORRELATION_ID)).isEqualTo("order-1");
        assertThat(command.headers().get(HeaderNames.CAUSATION_ID)).isEqualTo("evt-1");
        assertThat(command.headers().get(HeaderNames.SAGA_STEP)).isEqualTo("WAIT_INVENTORY");

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.contains("evt-1")).isTrue();
    }

    @Test
    void handle_inventoryReserved_publishes_payment_authorize() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        engine.handle(envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), "corr-1"));
        engine.handle(envelope("inventory.reserved", "evt-2", "order-1",
                new InventoryReservedEventV1("evt-2", "2025-01-01T00:00:00Z", "order-1"), "corr-1"));

        assertThat(publisher.published()).hasSize(2);
        PublishedCommand command = publisher.published().getLast();
        assertThat(command.commandType()).isEqualTo("payment.authorize");

        com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1 payload =
                (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1) command.payload();

        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.amount()).isEqualTo("22.50");
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(command.headers().get(HeaderNames.EVENT_ID)).isEqualTo(payload.commandId());
        assertThat(command.headers().get(HeaderNames.CAUSATION_ID)).isEqualTo("evt-2");
        assertThat(command.headers().get(HeaderNames.CORRELATION_ID)).isEqualTo("corr-1");
    }

    @Test
    void handle_paymentDeclined_publishes_release_and_cancel_and_marks_canceled() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        engine.handle(envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), ""));
        engine.handle(envelope("inventory.reserved", "evt-2", "order-1",
                new InventoryReservedEventV1("evt-2", "2025-01-01T00:00:00Z", "order-1"), "corr-1"));
        engine.handle(envelope("payment.declined", "evt-3", "order-1",
                new PaymentDeclinedEventV1("evt-3", "2025-01-01T00:00:00Z", "order-1", "card_declined"), "corr-1"));

        assertThat(publisher.published()).hasSize(4);
        PublishedCommand release = publisher.published().get(2);
        PublishedCommand cancel = publisher.published().get(3);

        assertThat(release.commandType()).isEqualTo("inventory.release");
        assertThat(cancel.commandType()).isEqualTo("order.cancel");

        CheckoutSaga saga = sagaRepository.get("order-1");
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void handle_orderCompleted_marks_saga_complete() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        engine.handle(envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), ""));
        engine.handle(envelope("inventory.reserved", "evt-2", "order-1",
                new InventoryReservedEventV1("evt-2", "2025-01-01T00:00:00Z", "order-1"), "corr-1"));
        engine.handle(envelope("payment.authorized", "evt-3", "order-1",
                new PaymentAuthorizedEventV1("evt-3", "2025-01-01T00:00:00Z", "order-1", "pay-1"), "corr-1"));
        engine.handle(envelope("order.completed", "evt-4", "order-1",
                new OrderCompletedEventV1("evt-4", "2025-01-01T00:00:00Z", "order-1"), "corr-1"));

        assertThat(publisher.published()).hasSize(3);
        CheckoutSaga saga = sagaRepository.get("order-1");
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void handle_duplicate_event_is_ignored() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        EventEnvelope env = envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), "");

        engine.handle(env);
        engine.handle(env);

        assertThat(publisher.published()).hasSize(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void handle_missing_event_id_is_ignored() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        EventEnvelope env = envelope("order.placed", "", "order-1", orderPlacedEvent("evt-1", "order-1"), "");
        engine.handle(env);

        assertThat(publisher.published()).isEmpty();
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    void handle_inventoryRejected_publishes_cancel_and_marks_canceled() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        engine.handle(envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), ""));
        engine.handle(envelope("inventory.rejected", "evt-2", "order-1",
                new InventoryRejectedEventV1("evt-2", "2025-01-01T00:00:00Z", "order-1", "out_of_stock"), "corr-1"));

        assertThat(publisher.published()).hasSize(2);
        PublishedCommand cancel = publisher.published().getLast();
        assertThat(cancel.commandType()).isEqualTo("order.cancel");

        CheckoutSaga saga = sagaRepository.get("order-1");
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void handle_out_of_order_event_is_ignored() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        engine.handle(envelope("order.placed", "evt-1", "order-1", orderPlacedEvent("evt-1", "order-1"), ""));
        engine.handle(envelope("payment.authorized", "evt-2", "order-1",
                new PaymentAuthorizedEventV1("evt-2", "2025-01-01T00:00:00Z", "order-1", "pay-1"), "corr-1"));

        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().getFirst().commandType()).isEqualTo("inventory.reserve");
    }

    @Test
    void handle_orderPlaced_missing_total_currency_uses_fallback() throws Exception {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();

        CheckoutSagaEngine engine = newEngine(sagaRepository, processedEventRepository, publisher);

        OrderPlacedEventV1 payload = new OrderPlacedEventV1(
                "evt-1",
                "2025-01-01T00:00:00Z",
                "order-1",
                "cust-1",
                List.of(
                        new OrderPlacedEventV1.Item("sku-1", 2, "10.00"),
                        new OrderPlacedEventV1.Item("sku-2", 1, "5.00")
                ),
                null,
                null,
                "2.50",
                null
        );

        engine.handle(envelope("order.placed", "evt-1", "order-1", payload, ""));

        CheckoutSaga saga = sagaRepository.get("order-1");
        assertThat(saga.getAmount()).isEqualTo("22.50");
        assertThat(saga.getCurrency()).isEqualTo("BRL");
    }

    private CheckoutSagaEngine newEngine(InMemorySagaRepository sagaRepository,
                                         InMemoryProcessedEventRepository processedEventRepository,
                                         CapturingCommandPublisher publisher) {
        CheckoutSagaCommandSender sender = new CheckoutSagaCommandSender(publisher);
        return new CheckoutSagaEngine(
                sagaRepository,
                processedEventRepository,
                sender,
                objectMapper,
                sagaProperties
        );
    }

    private EventEnvelope envelope(String eventType, String eventId, String key, Object payload, String correlationId)
            throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        return new EventEnvelope(
                "topic",
                key,
                json,
                eventId,
                eventType,
                "2025-01-01T00:00:00Z",
                correlationId,
                eventId,
                "",
                "",
                "",
                "Order",
                key
        );
    }

    private OrderPlacedEventV1 orderPlacedEvent(String eventId, String orderId) {
        return new OrderPlacedEventV1(
                eventId,
                "2025-01-01T00:00:00Z",
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
    }

    private static class InMemorySagaRepository implements CheckoutSagaRepository {
        private final Map<String, CheckoutSaga> store = new HashMap<>();

        @Override
        public void save(CheckoutSaga saga) {
            store.put(saga.getOrderId(), saga);
        }

        @Override
        public Optional<CheckoutSaga> findByOrderId(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<CheckoutSaga> findTimedOut(java.time.Instant now) {
            return store.values().stream()
                    .filter(saga -> saga.getDeadlineAt() != null)
                    .filter(saga -> !saga.getDeadlineAt().isAfter(now))
                    .filter(saga -> saga.getStatus() == SagaStatus.RUNNING)
                    .filter(saga -> saga.getStep() == SagaStep.WAIT_INVENTORY
                            || saga.getStep() == SagaStep.WAIT_PAYMENT
                            || saga.getStep() == SagaStep.WAIT_ORDER_COMPLETION)
                    .toList();
        }

        CheckoutSaga get(String orderId) {
            return store.get(orderId);
        }
    }

    private static class InMemoryProcessedEventRepository implements ProcessedEventRepository {
        private final Map<String, String> processed = new HashMap<>();

        @Override
        public boolean markProcessedIfFirst(String eventId, String eventType, String orderId) {
            return processed.putIfAbsent(eventId, eventType + ":" + orderId) == null;
        }

        int count() {
            return processed.size();
        }

        boolean contains(String eventId) {
            return processed.containsKey(eventId);
        }
    }

    private static class CapturingCommandPublisher implements CommandPublisher {
        private final List<PublishedCommand> published = new ArrayList<>();

        @Override
        public void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers) {
            published.add(new PublishedCommand(topic, key, commandType, payload, headers));
        }

        List<PublishedCommand> published() {
            return published;
        }
    }

    private record PublishedCommand(
            String topic,
            String key,
            String commandType,
            Object payload,
            Map<String, String> headers
    ) {}
}
