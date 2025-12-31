package com.mvbr.retailstore.payment.application.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mvbr.retailstore.payment.application.command.SagaContext;
import com.mvbr.retailstore.payment.application.port.out.EventPublisher;
import com.mvbr.retailstore.payment.application.port.out.PaymentRepository;
import com.mvbr.retailstore.payment.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.payment.domain.model.Payment;
import com.mvbr.retailstore.payment.domain.model.PaymentStatus;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentFailedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Processa eventos Stripe recebidos via webhook.
 */
@Component
public class StripeWebhookService {

    private static final Logger log = Logger.getLogger(StripeWebhookService.class.getName());
    private static final String AGGREGATE_TYPE = "Order";

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedRepository;
    private final EventPublisher eventPublisher;

    public StripeWebhookService(PaymentRepository paymentRepository,
                                ProcessedMessageRepository processedRepository,
                                EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.processedRepository = processedRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processa um evento Stripe com idempotencia por eventId.
     */
    @Transactional
    public void process(Event event) {
        if (event == null || event.getId() == null || event.getId().isBlank()) {
            log.warning("StripeWebhookService: event missing id");
            return;
        }

        if (event.getType() == null || event.getType().isBlank()) {
            log.warning("StripeWebhookService: event missing type id=" + event.getId());
            return;
        }

        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null || intent.getId() == null || intent.getId().isBlank()) {
            log.warning("StripeWebhookService: unsupported event payload id=" + event.getId());
            return;
        }

        String providerPaymentId = intent.getId();
        boolean first = processedRepository.markProcessedIfFirst(
                event.getId(),
                "stripe.webhook",
                providerPaymentId,
                Instant.now()
        );
        if (!first) {
            log.info("StripeWebhookService: duplicate eventId=" + event.getId());
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByProviderPaymentId(providerPaymentId);
        if (paymentOpt.isEmpty()) {
            log.warning("StripeWebhookService: payment not found providerPaymentId=" + providerPaymentId
                    + " eventId=" + event.getId());
            return;
        }

        Payment payment = paymentOpt.get();
        SagaContext ctx = sagaContextFor(payment);

        switch (event.getType()) {
            case "payment_intent.succeeded" -> onSucceeded(payment, intent, ctx);
            case "payment_intent.payment_failed" -> onPaymentFailed(payment, intent, ctx);
            case "payment_intent.amount_capturable_updated" -> onCapturableUpdated(payment);
            default -> log.info("StripeWebhookService: ignoring eventType=" + event.getType());
        }
    }

    private void onSucceeded(Payment payment, PaymentIntent intent, SagaContext ctx) {
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }
        payment.markCaptured();
        paymentRepository.save(payment);
        publishCaptured(payment, ctx);
    }

    private void onPaymentFailed(Payment payment, PaymentIntent intent, SagaContext ctx) {
        if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        String reason = extractDeclineReason(intent);
        payment.markFailed(reason == null || reason.isBlank() ? "PAYMENT_FAILED" : reason);
        paymentRepository.save(payment);
        publishFailed(payment, reason, ctx);
    }

    private void onCapturableUpdated(Payment payment) {
        if (payment.getStatus() == PaymentStatus.AUTHORIZED || payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }
        payment.markAuthorized();
        paymentRepository.save(payment);
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject instanceof PaymentIntent paymentIntent) {
            return paymentIntent;
        }
        if (event.getData() == null || event.getData().getObject() == null) {
            return null;
        }
        Object rawObject = event.getData().getObject();
        if (rawObject instanceof PaymentIntent paymentIntent) {
            return paymentIntent;
        }
        if (rawObject instanceof JsonObject jsonObject) {
            return paymentIntentFromJson(jsonObject);
        }
        if (rawObject instanceof Map<?, ?> map) {
            return paymentIntentFromMap(map);
        }
        return null;
    }

    private PaymentIntent paymentIntentFromJson(JsonObject jsonObject) {
        JsonElement idElement = jsonObject.get("id");
        if (idElement == null || idElement.isJsonNull()) {
            return null;
        }
        String id = idElement.getAsString();
        if (id == null || id.isBlank()) {
            return null;
        }
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        JsonElement statusElement = jsonObject.get("status");
        if (statusElement != null && !statusElement.isJsonNull()) {
            intent.setStatus(statusElement.getAsString());
        }
        return intent;
    }

    private PaymentIntent paymentIntentFromMap(Map<?, ?> map) {
        Object idValue = map.get("id");
        if (!(idValue instanceof String id) || id.isBlank()) {
            return null;
        }
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        Object statusValue = map.get("status");
        if (statusValue instanceof String status && !status.isBlank()) {
            intent.setStatus(status);
        }
        return intent;
    }

    private String extractDeclineReason(PaymentIntent intent) {
        if (intent.getLastPaymentError() == null) {
            return null;
        }
        String decline = intent.getLastPaymentError().getDeclineCode();
        if (decline != null && !decline.isBlank()) {
            return decline.toUpperCase();
        }
        String code = intent.getLastPaymentError().getCode();
        if (code != null && !code.isBlank()) {
            return code.toUpperCase();
        }
        return null;
    }

    private SagaContext sagaContextFor(Payment payment) {
        if (payment.getCorrelationId() == null || payment.getCorrelationId().isBlank()) {
            return null;
        }
        return new SagaContext(
                null,
                payment.getCorrelationId(),
                null,
                null,
                null,
                AGGREGATE_TYPE,
                payment.getOrderId().value()
        );
    }

    private void publishCaptured(Payment payment, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        PaymentCapturedEventV1 event = new PaymentCapturedEventV1(
                eventId,
                now.toString(),
                payment.getOrderId().value(),
                payment.getPaymentId().value(),
                payment.getProviderPaymentId()
        );

        eventPublisher.publish(
                TopicNames.PAYMENT_EVENTS_V1,
                AGGREGATE_TYPE,
                payment.getOrderId().value(),
                "payment.captured",
                event,
                SagaHeaders.forEvent(eventId, "payment.captured", now.toString(), AGGREGATE_TYPE,
                        payment.getOrderId().value(), ctx),
                now
        );
    }

    private void publishFailed(Payment payment, String reason, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        PaymentFailedEventV1 event = new PaymentFailedEventV1(
                eventId,
                now.toString(),
                payment.getOrderId().value(),
                payment.getPaymentId().value(),
                payment.getProviderPaymentId(),
                reason == null || reason.isBlank() ? "PAYMENT_FAILED" : reason
        );

        eventPublisher.publish(
                TopicNames.PAYMENT_EVENTS_V1,
                AGGREGATE_TYPE,
                payment.getOrderId().value(),
                "payment.failed",
                event,
                SagaHeaders.forEvent(eventId, "payment.failed", now.toString(), AGGREGATE_TYPE,
                        payment.getOrderId().value(), ctx),
                now
        );
    }
}
