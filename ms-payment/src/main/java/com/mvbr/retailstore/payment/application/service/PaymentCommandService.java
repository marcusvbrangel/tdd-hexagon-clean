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
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCaptureFailedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Orquestra comandos de payment com idempotencia e outbox.
 */
@Component
public class PaymentCommandService {

    private static final Logger log = Logger.getLogger(PaymentCommandService.class.getName());
    private static final String AGGREGATE_TYPE = "Order";

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedRepository;
    private final EventPublisher eventPublisher;
    private final PaymentGateway paymentGateway;

    public PaymentCommandService(PaymentRepository paymentRepository,
                                 ProcessedMessageRepository processedRepository,
                                 EventPublisher eventPublisher,
                                 PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.processedRepository = processedRepository;
        this.eventPublisher = eventPublisher;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Autoriza pagamento e publica payment.authorized/payment.declined de forma idempotente.
     */
    @Transactional
    public void authorize(AuthorizePaymentCommand cmd, SagaContext ctx) {
        String orderId = cmd.orderId();
        String commandId = cmd.commandId();

        boolean first = processedRepository.markProcessedIfFirst(commandId, "payment.authorize", orderId, Instant.now());
        if (!first) {
            paymentRepository.findByOrderId(orderId).ifPresentOrElse(
                    payment -> republishAuthorizeOutcome(payment, ctx),
                    () -> log.info("Duplicate payment.authorize but payment not found. orderId=" + orderId)
            );
            return;
        }

        Optional<Payment> existingOpt = paymentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            republishAuthorizeOutcome(existingOpt.get(), ctx);
            return;
        }

        BigDecimal amount = parseAmount(cmd.amount());
        String currency = normalizeCurrency(cmd.currency());
        String paymentMethod = normalizePaymentMethod(cmd.paymentMethod());

        PaymentAuthorizationResult result;
        if (amount == null || amount.signum() <= 0) {
            result = PaymentAuthorizationResult.declined("INVALID_AMOUNT");
        } else {
            PaymentAuthorizationRequest request = new PaymentAuthorizationRequest(
                    commandId,
                    orderId,
                    cmd.customerId(),
                    amount,
                    currency,
                    paymentMethod,
                    ctx != null ? ctx.correlationId() : null,
                    ctx != null ? ctx.sagaId() : null
            );
            result = paymentGateway.authorize(request);
        }

        Instant now = Instant.now();
        String providerPaymentId = normalizeProviderPaymentId(result.providerPaymentId());
        String paymentId = providerPaymentId != null ? providerPaymentId : UUID.randomUUID().toString();

        Payment payment = new Payment(
                new PaymentId(paymentId),
                providerPaymentId,
                new OrderId(orderId),
                normalizeCustomer(cmd.customerId()),
                normalizeAmount(amount),
                currency,
                paymentMethod,
                PaymentStatus.PENDING,
                null,
                now,
                now,
                commandId,
                ctx != null ? ctx.correlationId() : null
        );

        if (result.authorized()) {
            payment.markAuthorized();
        } else {
            payment.markDeclined(resolveDeclineReason(result));
        }
        payment.updateLastCommandId(commandId);
        if (ctx != null && ctx.correlationId() != null && !ctx.correlationId().isBlank()) {
            payment.updateCorrelationId(ctx.correlationId());
        }

        paymentRepository.save(payment);

        if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
            publishAuthorized(payment, ctx);
        } else {
            publishDeclined(payment.getOrderId().value(), payment.getReason(), ctx);
        }
    }

    /**
     * Captura pagamento autorizado e publica payment.captured/payment.capture_failed.
     */
    @Transactional
    public void capture(CapturePaymentCommand cmd, SagaContext ctx) {
        String orderId = cmd.orderId();
        String commandId = cmd.commandId();

        boolean first = processedRepository.markProcessedIfFirst(commandId, "payment.capture", orderId, Instant.now());
        if (!first) {
            paymentRepository.findByOrderId(orderId).ifPresentOrElse(
                    payment -> republishCaptureOutcome(payment, ctx),
                    () -> log.info("Duplicate payment.capture but payment not found. orderId=" + orderId)
            );
            return;
        }

        Payment payment = findPayment(orderId, cmd.paymentId());
        if (payment == null) {
            publishCaptureFailed(orderId, cmd.paymentId(), null, "PAYMENT_NOT_FOUND", ctx);
            return;
        }

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            publishCaptured(payment, ctx);
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            publishCaptureFailed(orderId, payment.getPaymentId().value(), payment.getProviderPaymentId(),
                    reasonOrDefault(payment.getReason(), "CAPTURE_FAILED"), ctx);
            return;
        }
        if (payment.getStatus() == PaymentStatus.DECLINED) {
            publishCaptureFailed(orderId, payment.getPaymentId().value(), payment.getProviderPaymentId(),
                    "NOT_AUTHORIZED", ctx);
            return;
        }

        String providerPaymentId = resolveProviderPaymentId(payment);
        if (providerPaymentId == null) {
            publishCaptureFailed(orderId, payment.getPaymentId().value(), null, "PROVIDER_ID_MISSING", ctx);
            return;
        }

        PaymentCaptureRequest request = new PaymentCaptureRequest(
                commandId,
                orderId,
                payment.getPaymentId().value(),
                providerPaymentId,
                ctx != null ? ctx.correlationId() : null,
                ctx != null ? ctx.sagaId() : null
        );

        PaymentCaptureResult result = paymentGateway.capture(request);
        if (result.captured()) {
            payment.markCaptured();
        } else {
            payment.markFailed(reasonOrDefault(result.failureReason(), "CAPTURE_FAILED"));
        }
        payment.updateLastCommandId(commandId);
        if (ctx != null && ctx.correlationId() != null && !ctx.correlationId().isBlank()) {
            payment.updateCorrelationId(ctx.correlationId());
        }

        paymentRepository.save(payment);

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            publishCaptured(payment, ctx);
        } else {
            publishCaptureFailed(orderId, payment.getPaymentId().value(), providerPaymentId,
                    reasonOrDefault(payment.getReason(), "CAPTURE_FAILED"), ctx);
        }
    }

    private Payment findPayment(String orderId, String paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isPresent()) {
            return paymentOpt.get();
        }
        if (paymentId != null && !paymentId.isBlank()) {
            return paymentRepository.findByProviderPaymentId(paymentId).orElse(null);
        }
        return null;
    }

    private CustomerId normalizeCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        return new CustomerId(customerId);
    }

    private String normalizeProviderPaymentId(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            return null;
        }
        return providerPaymentId;
    }

    private String resolveProviderPaymentId(Payment payment) {
        if (payment.getProviderPaymentId() != null && !payment.getProviderPaymentId().isBlank()) {
            return payment.getProviderPaymentId();
        }
        return payment.getPaymentId().value();
    }

    /**
     * Reenvia o resultado conhecido quando o comando de authorize chega duplicado.
     */
    private void republishAuthorizeOutcome(Payment payment, SagaContext ctx) {
        if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
            publishAuthorized(payment, ctx);
            return;
        }
        if (payment.getStatus() == PaymentStatus.DECLINED || payment.getStatus() == PaymentStatus.FAILED) {
            publishDeclined(payment.getOrderId().value(), payment.getReason(), ctx);
            return;
        }
        publishDeclined(payment.getOrderId().value(), "PENDING_STATE", ctx);
    }

    /**
     * Reenvia o resultado conhecido quando o comando de capture chega duplicado.
     */
    private void republishCaptureOutcome(Payment payment, SagaContext ctx) {
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            publishCaptured(payment, ctx);
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            publishCaptureFailed(payment.getOrderId().value(), payment.getPaymentId().value(),
                    payment.getProviderPaymentId(), reasonOrDefault(payment.getReason(), "CAPTURE_FAILED"), ctx);
            return;
        }
        if (payment.getStatus() == PaymentStatus.DECLINED) {
            publishCaptureFailed(payment.getOrderId().value(), payment.getPaymentId().value(),
                    payment.getProviderPaymentId(), "NOT_AUTHORIZED", ctx);
            return;
        }
        publishCaptureFailed(payment.getOrderId().value(), payment.getPaymentId().value(),
                payment.getProviderPaymentId(), "PENDING_STATE", ctx);
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "BRL";
        }
        return currency;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return null;
        }
        return paymentMethod;
    }

    private String resolveDeclineReason(PaymentAuthorizationResult result) {
        if (result.declineReason() != null && !result.declineReason().isBlank()) {
            return result.declineReason();
        }
        if (result.status() != null && !result.status().isBlank()) {
            return "STRIPE_STATUS_" + result.status().toUpperCase();
        }
        return "DECLINED";
    }

    private String reasonOrDefault(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason;
    }

    /**
     * Publica payment.authorized via outbox.
     */
    private void publishAuthorized(Payment payment, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        PaymentAuthorizedEventV1 event = new PaymentAuthorizedEventV1(
                eventId,
                now.toString(),
                payment.getOrderId().value(),
                payment.getPaymentId().value()
        );

        eventPublisher.publish(
                TopicNames.PAYMENT_EVENTS_V1,
                AGGREGATE_TYPE,
                payment.getOrderId().value(),
                "payment.authorized",
                event,
                SagaHeaders.forEvent(eventId, "payment.authorized", now.toString(),
                        AGGREGATE_TYPE, payment.getOrderId().value(), ctx),
                now
        );
    }

    /**
     * Publica payment.declined via outbox.
     */
    private void publishDeclined(String orderId, String reason, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        PaymentDeclinedEventV1 event = new PaymentDeclinedEventV1(
                eventId,
                now.toString(),
                orderId,
                reason == null || reason.isBlank() ? "DECLINED" : reason
        );

        eventPublisher.publish(
                TopicNames.PAYMENT_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "payment.declined",
                event,
                SagaHeaders.forEvent(eventId, "payment.declined", now.toString(), AGGREGATE_TYPE, orderId, ctx),
                now
        );
    }

    /**
     * Publica payment.captured via outbox.
     */
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
                SagaHeaders.forEvent(eventId, "payment.captured", now.toString(), AGGREGATE_TYPE, payment.getOrderId().value(), ctx),
                now
        );
    }

    /**
     * Publica payment.capture_failed via outbox.
     */
    private void publishCaptureFailed(String orderId,
                                      String paymentId,
                                      String providerPaymentId,
                                      String reason,
                                      SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        PaymentCaptureFailedEventV1 event = new PaymentCaptureFailedEventV1(
                eventId,
                now.toString(),
                orderId,
                paymentId,
                providerPaymentId,
                reason == null || reason.isBlank() ? "CAPTURE_FAILED" : reason
        );

        eventPublisher.publish(
                TopicNames.PAYMENT_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "payment.capture_failed",
                event,
                SagaHeaders.forEvent(eventId, "payment.capture_failed", now.toString(), AGGREGATE_TYPE, orderId, ctx),
                now
        );
    }
}
