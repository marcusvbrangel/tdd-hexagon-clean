package com.mvbr.retailstore.payment.application.service;

import com.mvbr.retailstore.payment.application.command.AuthorizePaymentCommand;
import com.mvbr.retailstore.payment.application.command.CapturePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;
import com.mvbr.retailstore.payment.application.port.out.EventPublisher;
import com.mvbr.retailstore.payment.application.port.out.PaymentAuthorizationRequest;
import com.mvbr.retailstore.payment.application.port.out.PaymentAuthorizationResult;
import com.mvbr.retailstore.payment.application.port.out.PaymentCaptureRequest;
import com.mvbr.retailstore.payment.application.port.out.PaymentCaptureResult;
import com.mvbr.retailstore.payment.application.port.out.PaymentGateway;
import com.mvbr.retailstore.payment.application.port.out.PaymentRepository;
import com.mvbr.retailstore.payment.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.payment.domain.model.CustomerId;
import com.mvbr.retailstore.payment.domain.model.OrderId;
import com.mvbr.retailstore.payment.domain.model.Payment;
import com.mvbr.retailstore.payment.domain.model.PaymentId;
import com.mvbr.retailstore.payment.domain.model.PaymentStatus;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers.HeaderNames;
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

class PaymentCommandServiceTest {

    @Test
    void authorize_payment_authorizes_and_publishes_event() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();
        FakePaymentGateway gateway = new FakePaymentGateway(
                new PaymentAuthorizationResult(true, "pi_1", "requires_capture", null),
                new PaymentCaptureResult(true, "pi_1", "succeeded", null)
        );

        PaymentCommandService service = new PaymentCommandService(
                paymentRepository,
                processedRepository,
                publisher,
                gateway
        );

        SagaContext ctx = new SagaContext("saga-1", "corr-1", "cause-1", "checkout", "WAIT_PAYMENT", "Order", "order-1");
        AuthorizePaymentCommand cmd = new AuthorizePaymentCommand(
                "cmd-1",
                "order-1",
                "cust-1",
                "10.00",
                "BRL",
                "card"
        );

        service.authorize(cmd, ctx);

        Payment payment = paymentRepository.findByOrderId("order-1").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getPaymentId().value()).isEqualTo("pi_1");

        assertThat(publisher.events()).hasSize(1);
        CapturedEvent event = publisher.events().getFirst();
        assertThat(event.eventType()).isEqualTo("payment.authorized");

        PaymentAuthorizedEventV1 payload = (PaymentAuthorizedEventV1) event.payload();
        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.paymentId()).isEqualTo("pi_1");
        assertThat(event.headers().get(HeaderNames.CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(event.headers().get(HeaderNames.SAGA_ID)).isEqualTo("saga-1");
        assertThat(gateway.authorizeCalls()).isEqualTo(1);
    }

    @Test
    void authorize_payment_declines_invalid_amount() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();
        FakePaymentGateway gateway = new FakePaymentGateway(
                new PaymentAuthorizationResult(true, "pi_2", "requires_capture", null),
                new PaymentCaptureResult(true, "pi_2", "succeeded", null)
        );

        PaymentCommandService service = new PaymentCommandService(
                paymentRepository,
                processedRepository,
                publisher,
                gateway
        );

        AuthorizePaymentCommand cmd = new AuthorizePaymentCommand(
                "cmd-2",
                "order-2",
                "cust-2",
                "0",
                "BRL",
                "card"
        );

        service.authorize(cmd, null);

        Payment payment = paymentRepository.findByOrderId("order-2").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getReason()).isEqualTo("INVALID_AMOUNT");
        assertThat(gateway.authorizeCalls()).isEqualTo(0);

        CapturedEvent event = publisher.events().getFirst();
        assertThat(event.eventType()).isEqualTo("payment.declined");
        PaymentDeclinedEventV1 payload = (PaymentDeclinedEventV1) event.payload();
        assertThat(payload.reason()).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void authorize_duplicate_command_republishes_outcome() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();
        FakePaymentGateway gateway = new FakePaymentGateway(
                new PaymentAuthorizationResult(true, "pi_3", "requires_capture", null),
                new PaymentCaptureResult(true, "pi_3", "succeeded", null)
        );

        PaymentCommandService service = new PaymentCommandService(
                paymentRepository,
                processedRepository,
                publisher,
                gateway
        );

        SagaContext ctx = new SagaContext("saga-1", "corr-1", "cause-1", "checkout", "WAIT_PAYMENT", "Order", "order-3");
        AuthorizePaymentCommand cmd = new AuthorizePaymentCommand(
                "cmd-3",
                "order-3",
                "cust-3",
                "12.50",
                "BRL",
                "card"
        );

        service.authorize(cmd, ctx);
        service.authorize(cmd, ctx);

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(publisher.events()).hasSize(2);
        assertThat(publisher.events().getFirst().eventType()).isEqualTo("payment.authorized");
        assertThat(publisher.events().getLast().eventType()).isEqualTo("payment.authorized");
        assertThat(gateway.authorizeCalls()).isEqualTo(1);
    }

    @Test
    void capture_payment_captures_and_publishes_event() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryProcessedMessageRepository processedRepository = new InMemoryProcessedMessageRepository();
        CapturingEventPublisher publisher = new CapturingEventPublisher();
        FakePaymentGateway gateway = new FakePaymentGateway(
                new PaymentAuthorizationResult(true, "pi_4", "requires_capture", null),
                new PaymentCaptureResult(true, "pi_4", "succeeded", null)
        );

        PaymentCommandService service = new PaymentCommandService(
                paymentRepository,
                processedRepository,
                publisher,
                gateway
        );

        Payment payment = new Payment(
                new PaymentId("pi_4"),
                "pi_4",
                new OrderId("order-4"),
                new CustomerId("cust-4"),
                BigDecimal.TEN,
                "BRL",
                "card",
                PaymentStatus.AUTHORIZED,
                null,
                Instant.now(),
                Instant.now(),
                "cmd-4",
                "corr-4"
        );
        paymentRepository.save(payment);

        service.capture(new CapturePaymentCommand("cmd-5", "order-4", "pi_4"),
                new SagaContext("saga-4", "corr-4", "cause-4", "checkout", "WAIT_CAPTURE", "Order", "order-4"));

        Payment updated = paymentRepository.findByOrderId("order-4").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        CapturedEvent event = publisher.events().getLast();
        assertThat(event.eventType()).isEqualTo("payment.captured");
        PaymentCapturedEventV1 payload = (PaymentCapturedEventV1) event.payload();
        assertThat(payload.orderId()).isEqualTo("order-4");
        assertThat(payload.providerPaymentId()).isEqualTo("pi_4");
        assertThat(gateway.captureCalls()).isEqualTo(1);
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

        int count() {
            return payments.size();
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

    private static final class FakePaymentGateway implements PaymentGateway {
        private final PaymentAuthorizationResult authorizeResult;
        private final PaymentCaptureResult captureResult;
        private int authorizeCalls;
        private int captureCalls;

        private FakePaymentGateway(PaymentAuthorizationResult authorizeResult, PaymentCaptureResult captureResult) {
            this.authorizeResult = authorizeResult;
            this.captureResult = captureResult;
        }

        @Override
        public PaymentAuthorizationResult authorize(PaymentAuthorizationRequest request) {
            authorizeCalls++;
            return authorizeResult;
        }

        @Override
        public PaymentCaptureResult capture(PaymentCaptureRequest request) {
            captureCalls++;
            return captureResult;
        }

        int authorizeCalls() {
            return authorizeCalls;
        }

        int captureCalls() {
            return captureCalls;
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
