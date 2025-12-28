package com.mvbr.retailstore.payment.application.service;

import com.google.gson.JsonObject;
import com.mvbr.retailstore.payment.application.port.out.EventPublisher;
import com.mvbr.retailstore.payment.application.port.out.PaymentRepository;
import com.mvbr.retailstore.payment.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.payment.domain.model.CustomerId;
import com.mvbr.retailstore.payment.domain.model.OrderId;
import com.mvbr.retailstore.payment.domain.model.Payment;
import com.mvbr.retailstore.payment.domain.model.PaymentId;
import com.mvbr.retailstore.payment.domain.model.PaymentStatus;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentFailedEventV1;
import com.stripe.model.Event;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StripeWebhookServiceTest {

    @Test
    void process_payment_intent_succeeded_marks_captured_and_publishes() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();

        StripeWebhookService service = new StripeWebhookService(paymentRepository, processedRepository, publisher);

        Payment payment = new Payment(
                new PaymentId("pay-1"),
                "pi_123",
                new OrderId("order-1"),
                new CustomerId("cust-1"),
                BigDecimal.TEN,
                "BRL",
                "card",
                PaymentStatus.AUTHORIZED,
                null,
                Instant.now(),
                Instant.now(),
                "cmd-1",
                "corr-1"
        );
        paymentRepository.save(payment);

        Event event = stripeEvent("evt-1", "payment_intent.succeeded", "pi_123", "succeeded");
        service.process(event);

        Payment updated = paymentRepository.findByOrderId("order-1").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(publisher.events()).hasSize(1);
        CapturedEvent captured = publisher.events().getFirst();
        assertThat(captured.eventType()).isEqualTo("payment.captured");
        PaymentCapturedEventV1 payload = (PaymentCapturedEventV1) captured.payload();
        assertThat(payload.providerPaymentId()).isEqualTo("pi_123");
    }

    @Test
    void process_payment_intent_failed_marks_failed_and_publishes() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();

        StripeWebhookService service = new StripeWebhookService(paymentRepository, processedRepository, publisher);

        Payment payment = new Payment(
                new PaymentId("pay-2"),
                "pi_456",
                new OrderId("order-2"),
                new CustomerId("cust-2"),
                BigDecimal.TEN,
                "BRL",
                "card",
                PaymentStatus.AUTHORIZED,
                null,
                Instant.now(),
                Instant.now(),
                "cmd-2",
                "corr-2"
        );
        paymentRepository.save(payment);

        Event event = stripeEvent("evt-2", "payment_intent.payment_failed", "pi_456", "requires_payment_method");
        service.process(event);

        Payment updated = paymentRepository.findByOrderId("order-2").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(publisher.events()).hasSize(1);
        CapturedEvent captured = publisher.events().getFirst();
        assertThat(captured.eventType()).isEqualTo("payment.failed");
        PaymentFailedEventV1 payload = (PaymentFailedEventV1) captured.payload();
        assertThat(payload.providerPaymentId()).isEqualTo("pi_456");
    }

    @Test
    void process_dedupes_by_event_id() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();

        StripeWebhookService service = new StripeWebhookService(paymentRepository, processedRepository, publisher);

        Payment payment = new Payment(
                new PaymentId("pay-3"),
                "pi_789",
                new OrderId("order-3"),
                new CustomerId("cust-3"),
                BigDecimal.TEN,
                "BRL",
                "card",
                PaymentStatus.AUTHORIZED,
                null,
                Instant.now(),
                Instant.now(),
                "cmd-3",
                "corr-3"
        );
        paymentRepository.save(payment);

        Event event = stripeEvent("evt-3", "payment_intent.succeeded", "pi_789", "succeeded");
        service.process(event);
        service.process(event);

        assertThat(publisher.events()).hasSize(1);
    }

    private Event stripeEvent(String eventId, String type, String paymentIntentId, String status) {
        Event event = new Event();
        event.setId(eventId);
        event.setType(type);

        Event.Data data = new Event.Data();
        JsonObject object = new JsonObject();
        object.addProperty("id", paymentIntentId);
        object.addProperty("object", "payment_intent");
        if (status != null) {
            object.addProperty("status", status);
        }
        data.setObject(object);
        event.setData(data);

        return event;
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {
        private final Map<String, Payment> payments = new HashMap<>();

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.ofNullable(payments.get(orderId));
        }

        @Override
        public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
            return payments.values().stream()
                    .filter(payment -> providerPaymentId.equals(payment.getProviderPaymentId()))
                    .findFirst();
        }

        @Override
        public Payment save(Payment payment) {
            payments.put(payment.getOrderId().value(), payment);
            return payment;
        }
    }

    private static final class InMemoryProcessedMessageRepository implements ProcessedMessageRepository {
        private final Set<String> messages = new HashSet<>();

        @Override
        public boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt) {
            return messages.add(messageId);
        }
    }

    private static final class CapturingEventPublisher implements EventPublisher {
        private final List<CapturedEvent> events = new ArrayList<>();

        @Override
        public void publish(String topic,
                            String aggregateType,
                            String aggregateId,
                            String eventType,
                            Object payload,
                            Map<String, String> headers,
                            Instant occurredAt) {
            events.add(new CapturedEvent(topic, aggregateType, aggregateId, eventType, payload, headers));
        }

        List<CapturedEvent> events() {
            return events;
        }
    }

    private record CapturedEvent(
            String topic,
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload,
            Map<String, String> headers
    ) {
    }
}
