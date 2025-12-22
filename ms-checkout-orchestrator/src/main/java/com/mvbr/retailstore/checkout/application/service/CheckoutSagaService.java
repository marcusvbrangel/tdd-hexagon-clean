package com.mvbr.retailstore.checkout.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.application.port.out.ProcessedEventRepository;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCancelCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompleteCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutSagaService {

    private final CheckoutSagaRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final CommandPublisher commandPublisher;
    private final ObjectMapper objectMapper;

    public CheckoutSagaService(CheckoutSagaRepository sagaRepository,
                              ProcessedEventRepository processedEventRepository,
                              CommandPublisher commandPublisher,
                              ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.processedEventRepository = processedEventRepository;
        this.commandPublisher = commandPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(EventEnvelope env) {
        if (env.eventId() == null || env.eventId().isBlank()) {
            return;
        }
        if (processedEventRepository.alreadyProcessed(env.eventId())) {
            return;
        }

        boolean handled = switch (env.eventType()) {
            case "order.placed" -> {
                onOrderPlaced(env);
                yield true;
            }
            case "inventory.reserved" -> {
                onInventoryReserved(env);
                yield true;
            }
            case "inventory.rejected" -> {
                onInventoryRejected(env);
                yield true;
            }
            case "payment.authorized" -> {
                onPaymentAuthorized(env);
                yield true;
            }
            case "payment.declined" -> {
                onPaymentDeclined(env);
                yield true;
            }
            case "order.completed" -> {
                onOrderCompleted(env);
                yield true;
            }
            case "inventory.released" -> {
                onInventoryReleased(env);
                yield true;
            }
            case "order.canceled" -> {
                onOrderCanceled(env);
                yield true;
            }
            default -> false;
        };

        if (handled) {
            processedEventRepository.markProcessed(env.eventId(), env.eventType(), env.aggregateIdOrKey());
        }
    }

    private void onOrderPlaced(EventEnvelope env) {
        OrderPlacedEventV1 placed = env.readPayload(objectMapper, OrderPlacedEventV1.class);

        String orderId = placed.orderId();
        String correlationId = env.correlationIdOr(orderId);

        CheckoutSaga saga = sagaRepository.findByOrderId(orderId)
                .orElseGet(() -> CheckoutSaga.start(orderId, correlationId));

        BigDecimal subtotal = placed.items().stream()
                .map(i -> new BigDecimal(i.unitPrice()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = (placed.discount() == null || placed.discount().isBlank())
                ? BigDecimal.ZERO
                : new BigDecimal(placed.discount());

        BigDecimal total = subtotal.subtract(discount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }

        saga.onOrderPlaced(placed.customerId(), total.toPlainString(), "BRL");
        sagaRepository.save(saga);

        String commandId = newCommandId();
        InventoryReserveCommandV1 cmd = new InventoryReserveCommandV1(
                commandId,
                now(),
                orderId,
                placed.items().stream()
                        .map(i -> new InventoryReserveCommandV1.Line(i.productId(), i.quantity()))
                        .toList()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "INVENTORY_RESERVE_PENDING",
                "Order",
                orderId
        );

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                orderId,
                "inventory.reserve",
                cmd,
                headers
        );
    }

    private void onInventoryReserved(EventEnvelope env) {
        InventoryReservedEventV1 event = env.readPayload(objectMapper, InventoryReservedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(event.orderId());

        saga.onInventoryReserved();
        sagaRepository.save(saga);

        String commandId = newCommandId();
        PaymentAuthorizeCommandV1 cmd = new PaymentAuthorizeCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getAmount(),
                saga.getCurrency()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "PAYMENT_AUTHORIZE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.PAYMENT_COMMANDS_V1,
                saga.getOrderId(),
                "payment.authorize",
                cmd,
                headers
        );
    }

    private void onInventoryRejected(EventEnvelope env) {
        InventoryRejectedEventV1 event = env.readPayload(objectMapper, InventoryRejectedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(event.orderId());

        saga.onInventoryRejected();
        sagaRepository.save(saga);

        String commandId = newCommandId();
        OrderCancelCommandV1 cmd = new OrderCancelCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                "inventory.rejected:" + event.reason()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_ORDER_CANCEL_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cmd,
                headers
        );
    }

    private void onPaymentAuthorized(EventEnvelope env) {
        PaymentAuthorizedEventV1 event = env.readPayload(objectMapper, PaymentAuthorizedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(event.orderId());

        saga.onPaymentAuthorized();
        sagaRepository.save(saga);

        String commandId = newCommandId();
        OrderCompleteCommandV1 cmd = new OrderCompleteCommandV1(
                commandId,
                now(),
                saga.getOrderId()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "ORDER_COMPLETE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.complete",
                cmd,
                headers
        );
    }

    private void onPaymentDeclined(EventEnvelope env) {
        PaymentDeclinedEventV1 event = env.readPayload(objectMapper, PaymentDeclinedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(event.orderId());

        saga.onPaymentDeclined();
        sagaRepository.save(saga);

        String releaseCommandId = newCommandId();
        InventoryReleaseCommandV1 release = new InventoryReleaseCommandV1(
                releaseCommandId,
                now(),
                saga.getOrderId()
        );

        Map<String, String> releaseHeaders = SagaHeaders.forCommand(
                releaseCommandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_INVENTORY_RELEASE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.release",
                release,
                releaseHeaders
        );

        String cancelCommandId = newCommandId();
        OrderCancelCommandV1 cancel = new OrderCancelCommandV1(
                cancelCommandId,
                now(),
                saga.getOrderId(),
                "payment.declined:" + event.reason()
        );

        Map<String, String> cancelHeaders = SagaHeaders.forCommand(
                cancelCommandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_ORDER_CANCEL_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cancel,
                cancelHeaders
        );
    }

    private void onOrderCompleted(EventEnvelope env) {
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markOrderCompleted();
        sagaRepository.save(saga);
    }

    private void onInventoryReleased(EventEnvelope env) {
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markInventoryReleased();
        sagaRepository.save(saga);
    }

    private void onOrderCanceled(EventEnvelope env) {
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markOrderCanceled();
        sagaRepository.save(saga);
    }

    private String newCommandId() {
        return UUID.randomUUID().toString();
    }

    private String now() {
        return Instant.now().toString();
    }
}
