package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.config.SagaProperties;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.observability.CheckoutBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSagaTimeoutSchedulerTest {

    @Test
    void timeout_wait_payment_retries_authorize() {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        SagaProperties sagaProperties = new SagaProperties();

        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");
        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().minusSeconds(5)
        );
        saga.onInventoryReserved(Instant.now().minusSeconds(5));
        sagaRepository.save(saga);

        CheckoutSagaTimeoutScheduler scheduler = new CheckoutSagaTimeoutScheduler(
                sagaRepository,
                new CheckoutSagaCommandSender(publisher),
                sagaProperties,
                metrics()
        );

        scheduler.tick();

        CheckoutSaga updated = sagaRepository.get("order-1");
        assertThat(updated.getAttemptsPayment()).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().getFirst().commandType()).isEqualTo("payment.authorize");
    }

    @Test
    void timeout_wait_payment_exceeds_max_compensates() {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        SagaProperties sagaProperties = new SagaProperties();
        sagaProperties.getRetries().setPaymentMax(1);

        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");
        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().minusSeconds(5)
        );
        saga.onInventoryReserved(Instant.now().minusSeconds(5));
        saga.schedulePaymentRetry(Instant.now().minusSeconds(5));
        sagaRepository.save(saga);

        CheckoutSagaTimeoutScheduler scheduler = new CheckoutSagaTimeoutScheduler(
                sagaRepository,
                new CheckoutSagaCommandSender(publisher),
                sagaProperties,
                metrics()
        );

        scheduler.tick();

        CheckoutSaga updated = sagaRepository.get("order-1");
        assertThat(updated.getStatus()).isEqualTo(SagaStatus.CANCELED);
        assertThat(updated.getStep()).isEqualTo(SagaStep.DONE);
        assertThat(publisher.published()).hasSize(2);
        assertThat(publisher.published().get(0).commandType()).isEqualTo("inventory.release");
        assertThat(publisher.published().get(1).commandType()).isEqualTo("order.cancel");
    }

    @Test
    void timeout_wait_payment_retries_keep_same_command_id() {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        SagaProperties sagaProperties = new SagaProperties();
        sagaProperties.getTimeouts().setPaymentSeconds(0);
        sagaProperties.getRetries().setPaymentMax(3);

        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");
        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().minusSeconds(5)
        );
        saga.onInventoryReserved(Instant.now().minusSeconds(5));
        sagaRepository.save(saga);

        CheckoutSagaTimeoutScheduler scheduler = new CheckoutSagaTimeoutScheduler(
                sagaRepository,
                new CheckoutSagaCommandSender(publisher),
                sagaProperties,
                metrics()
        );

        scheduler.tick();
        scheduler.tick();

        assertThat(publisher.published()).hasSize(2);
        PaymentAuthorizeCommandV1 first =
                (PaymentAuthorizeCommandV1) publisher.published().get(0).payload();
        PaymentAuthorizeCommandV1 second =
                (PaymentAuthorizeCommandV1) publisher.published().get(1).payload();
        assertThat(first.commandId()).isEqualTo(second.commandId());
    }

    @Test
    void timeout_wait_payment_capture_retries_capture() {
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        SagaProperties sagaProperties = new SagaProperties();

        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");
        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().minusSeconds(5)
        );
        saga.onInventoryReserved(Instant.now().minusSeconds(5));
        saga.onPaymentAuthorized(Instant.now().minusSeconds(5));
        saga.onOrderCompleted(Instant.now().minusSeconds(5));
        sagaRepository.save(saga);

        CheckoutSagaTimeoutScheduler scheduler = new CheckoutSagaTimeoutScheduler(
                sagaRepository,
                new CheckoutSagaCommandSender(publisher),
                sagaProperties,
                metrics()
        );

        scheduler.tick();

        CheckoutSaga updated = sagaRepository.get("order-1");
        assertThat(updated.getAttemptsPaymentCapture()).isEqualTo(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().getFirst().commandType()).isEqualTo("payment.capture");
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
        public List<CheckoutSaga> findTimedOut(Instant now) {
            return store.values().stream()
                    .filter(saga -> saga.getDeadlineAt() != null)
                    .filter(saga -> !saga.getDeadlineAt().isAfter(now))
                    .filter(saga -> saga.getStatus() == SagaStatus.RUNNING)
                    .filter(saga -> saga.getStep() == SagaStep.WAIT_INVENTORY
                            || saga.getStep() == SagaStep.WAIT_PAYMENT
                            || saga.getStep() == SagaStep.WAIT_ORDER_COMPLETION
                            || saga.getStep() == SagaStep.WAIT_PAYMENT_CAPTURE
                            || saga.getStep() == SagaStep.WAIT_INVENTORY_COMMIT)
                    .toList();
        }

        CheckoutSaga get(String orderId) {
            return store.get(orderId);
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

    private CheckoutBusinessMetrics metrics() {
        return new CheckoutBusinessMetrics(new SimpleMeterRegistry());
    }
}
