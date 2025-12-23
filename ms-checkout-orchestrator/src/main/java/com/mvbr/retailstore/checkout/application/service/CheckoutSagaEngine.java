package com.mvbr.retailstore.checkout.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.application.port.out.ProcessedEventRepository;
import com.mvbr.retailstore.checkout.config.SagaProperties;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class CheckoutSagaEngine {

    private static final Logger log = Logger.getLogger(CheckoutSagaEngine.class.getName());

    private static final String REASON_INVENTORY_REJECTED = "INVENTORY_REJECTED";
    private static final String REASON_PAYMENT_DECLINED = "PAYMENT_DECLINED";

    private final CheckoutSagaRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final CheckoutSagaCommandSender commandSender;
    private final ObjectMapper objectMapper;
    private final SagaProperties sagaProperties;

    public CheckoutSagaEngine(CheckoutSagaRepository sagaRepository,
                              ProcessedEventRepository processedEventRepository,
                              CheckoutSagaCommandSender commandSender,
                              ObjectMapper objectMapper,
                              SagaProperties sagaProperties) {
        this.sagaRepository = sagaRepository;
        this.processedEventRepository = processedEventRepository;
        this.commandSender = commandSender;
        this.objectMapper = objectMapper;
        this.sagaProperties = sagaProperties;
    }

    @Transactional
    public void handle(EventEnvelope env) {
        if (env.eventId() == null || env.eventId().isBlank()) {
            return;
        }
        if (env.eventType() == null || env.eventType().isBlank()) {
            return;
        }

        String eventType = env.eventType();
        String orderId = env.aggregateIdOrKey();

        boolean handled = switch (eventType) {
            case "order.placed" -> handleIfFirst(env, orderId, this::onOrderPlaced);
            case "inventory.reserved" -> handleIfFirst(env, orderId, this::onInventoryReserved);
            case "inventory.rejected" -> handleIfFirst(env, orderId, this::onInventoryRejected);
            case "payment.authorized" -> handleIfFirst(env, orderId, this::onPaymentAuthorized);
            case "payment.declined" -> handleIfFirst(env, orderId, this::onPaymentDeclined);
            case "order.completed" -> handleIfFirst(env, orderId, this::onOrderCompleted);
            case "order.canceled" -> handleIfFirst(env, orderId, this::onOrderCanceled);
            case "inventory.released" -> handleIfFirst(env, orderId, this::onInventoryReleased);
            default -> false;
        };

        if (!handled) {
            log.fine("Ignoring unsupported eventType=" + eventType + " key=" + env.key());
        }
    }

    private boolean handleIfFirst(EventEnvelope env, String orderId, EventHandler handler) {
        if (!processedEventRepository.markProcessedIfFirst(env.eventId(), env.eventType(), orderId)) {
            return true;
        }
        handler.handle(env);
        return true;
    }

    private void onOrderPlaced(EventEnvelope env) {
        OrderPlacedEventV1 placed = env.readPayload(objectMapper, OrderPlacedEventV1.class);
        String orderId = resolveOrderId(placed.orderId(), env);
        if (orderId == null || orderId.isBlank()) {
            log.warning("order.placed without orderId eventId=" + env.eventId());
            return;
        }

        CheckoutSaga saga = sagaRepository.findByOrderId(orderId).orElseGet(() ->
                CheckoutSaga.start(orderId, env.correlationIdOr(orderId)));

        if (saga.getStatus() != SagaStatus.RUNNING || saga.getStep() != SagaStep.STARTED) {
            log.warning("order.placed ignored. orderId=" + orderId
                    + " status=" + saga.getStatus()
                    + " step=" + saga.getStep());
            return;
        }

        AmountSummary summary = resolveAmount(placed, orderId);
        List<CheckoutSagaItem> items = toSagaItems(placed.items());

        saga.onOrderPlaced(
                placed.customerId(),
                summary.total(),
                summary.currency(),
                placed.paymentMethod(),
                items,
                deadlineAfterSeconds(sagaProperties.getTimeouts().getInventorySeconds())
        );
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryReserve(saga, env.eventId(), SagaStep.WAIT_INVENTORY.name());
    }

    private void onInventoryReserved(EventEnvelope env) {
        InventoryReservedEventV1 event = env.readPayload(objectMapper, InventoryReservedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_INVENTORY, env)) {
            return;
        }

        saga.onInventoryReserved(deadlineAfterSeconds(sagaProperties.getTimeouts().getPaymentSeconds()));
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendPaymentAuthorize(saga, env.eventId(), SagaStep.WAIT_PAYMENT.name());
    }

    private void onInventoryRejected(EventEnvelope env) {
        InventoryRejectedEventV1 event = env.readPayload(objectMapper, InventoryRejectedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_INVENTORY, env)) {
            return;
        }

        String lastError = errorWithDetail(REASON_INVENTORY_REJECTED, event.reason());
        saga.onInventoryRejected(lastError);
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendOrderCancel(saga, env.eventId(), SagaStep.COMPENSATING.name(), REASON_INVENTORY_REJECTED);
    }

    private void onPaymentAuthorized(EventEnvelope env) {
        PaymentAuthorizedEventV1 event = env.readPayload(objectMapper, PaymentAuthorizedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_PAYMENT, env)) {
            return;
        }

        saga.onPaymentAuthorized(deadlineAfterSeconds(sagaProperties.getTimeouts().getOrderCompleteSeconds()));
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendOrderComplete(saga, env.eventId(), SagaStep.WAIT_ORDER_COMPLETION.name());
    }

    private void onPaymentDeclined(EventEnvelope env) {
        PaymentDeclinedEventV1 event = env.readPayload(objectMapper, PaymentDeclinedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_PAYMENT, env)) {
            return;
        }

        String lastError = errorWithDetail(REASON_PAYMENT_DECLINED, event.reason());
        saga.onPaymentDeclined(lastError);
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryRelease(saga, env.eventId(), SagaStep.COMPENSATING.name());
        commandSender.sendOrderCancel(saga, env.eventId(), SagaStep.COMPENSATING.name(), REASON_PAYMENT_DECLINED);
    }

    private void onOrderCompleted(EventEnvelope env) {
        OrderCompletedEventV1 event = env.readPayload(objectMapper, OrderCompletedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_ORDER_COMPLETION, env)) {
            return;
        }

        saga.markOrderCompleted();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);
    }

    private void onOrderCanceled(EventEnvelope env) {
        OrderCanceledEventV1 event = env.readPayload(objectMapper, OrderCanceledEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (saga.getStatus() == SagaStatus.RUNNING) {
            saga.markOrderCanceled("ORDER_CANCELED");
            saga.recordLastEvent(env.eventId());
            sagaRepository.save(saga);
        }
    }

    private void onInventoryReleased(EventEnvelope env) {
        CheckoutSaga saga = sagaRepository.findByOrderId(env.aggregateIdOrKey()).orElse(null);
        if (saga == null) {
            log.warning("inventory.released for unknown orderId=" + env.aggregateIdOrKey());
            return;
        }
        saga.markInventoryReleased();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);
    }

    private boolean isExpectedStep(CheckoutSaga saga, SagaStep expected, EventEnvelope env) {
        if (saga.getStatus() != SagaStatus.RUNNING || saga.getStep() != expected) {
            log.warning("Out-of-order event=" + env.eventType()
                    + " orderId=" + saga.getOrderId()
                    + " eventId=" + env.eventId()
                    + " status=" + saga.getStatus()
                    + " step=" + saga.getStep());
            return false;
        }
        return true;
    }

    private CheckoutSaga findSaga(String orderId, String eventType, String eventId) {
        if (orderId == null || orderId.isBlank()) {
            log.warning(eventType + " without orderId eventId=" + eventId);
            return null;
        }
        Optional<CheckoutSaga> sagaOpt = sagaRepository.findByOrderId(orderId);
        if (sagaOpt.isEmpty()) {
            log.warning("Saga not found for orderId=" + orderId + " eventType=" + eventType);
            return null;
        }
        return sagaOpt.get();
    }

    private String resolveOrderId(String eventOrderId, EventEnvelope env) {
        if (eventOrderId != null && !eventOrderId.isBlank()) {
            return eventOrderId;
        }
        return env.aggregateIdOrKey();
    }

    private AmountSummary resolveAmount(OrderPlacedEventV1 placed, String orderId) {
        String currency = (placed.currency() == null || placed.currency().isBlank())
                ? "BRL"
                : placed.currency();

        BigDecimal total = parseBigDecimal(placed.total());
        boolean usedFallback = false;
        if (total == null) {
            total = computeTotalFromItems(placed.items(), placed.discount());
            usedFallback = true;
        }
        if (placed.currency() == null || placed.currency().isBlank()) {
            usedFallback = true;
        }
        if (usedFallback) {
            log.warning("OrderPlaced missing total/currency; using fallback. orderId=" + orderId);
        }
        return new AmountSummary(total.toPlainString(), currency);
    }

    private BigDecimal computeTotalFromItems(List<OrderPlacedEventV1.Item> items, String discountValue) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotal = items.stream()
                .map(item -> parseBigDecimal(item.unitPrice(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = parseBigDecimal(discountValue, BigDecimal.ZERO);
        BigDecimal total = subtotal.subtract(discount);
        return total.signum() < 0 ? BigDecimal.ZERO : total;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value, BigDecimal fallback) {
        BigDecimal parsed = parseBigDecimal(value);
        return parsed == null ? fallback : parsed;
    }

    private List<CheckoutSagaItem> toSagaItems(List<OrderPlacedEventV1.Item> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new CheckoutSagaItem(item.productId(), item.quantity()))
                .toList();
    }

    private Instant deadlineAfterSeconds(long seconds) {
        return Instant.now().plusSeconds(seconds);
    }

    private String errorWithDetail(String base, String detail) {
        if (detail == null || detail.isBlank()) {
            return base;
        }
        return base + ":" + detail;
    }

    private record AmountSummary(String total, String currency) {}

    private interface EventHandler {
        void handle(EventEnvelope env);
    }
}
