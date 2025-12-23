package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCancelCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompleteCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CheckoutSagaCommandSender {

    private static final String SAGA_NAME = "checkout";
    private static final String AGGREGATE_TYPE = "Order";

    private final CommandPublisher commandPublisher;

    public CheckoutSagaCommandSender(CommandPublisher commandPublisher) {
        this.commandPublisher = commandPublisher;
    }

    public void sendInventoryReserve(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = newCommandId();
        InventoryReserveCommandV1 cmd = new InventoryReserveCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                toItems(saga.getItems())
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        applyCommandType(headers, "inventory.reserve");

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.reserve",
                cmd,
                headers
        );
    }

    public void sendPaymentAuthorize(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = newCommandId();
        PaymentAuthorizeCommandV1 cmd = new PaymentAuthorizeCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getAmount(),
                saga.getCurrency(),
                saga.getPaymentMethod()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        applyCommandType(headers, "payment.authorize");

        commandPublisher.publish(
                TopicNames.PAYMENT_COMMANDS_V1,
                saga.getOrderId(),
                "payment.authorize",
                cmd,
                headers
        );
    }

    public void sendOrderComplete(CheckoutSaga saga, String causationId, String sagaStep) {
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
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        applyCommandType(headers, "order.complete");

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.complete",
                cmd,
                headers
        );
    }

    public void sendOrderCancel(CheckoutSaga saga, String causationId, String sagaStep, String reason) {
        String commandId = newCommandId();
        OrderCancelCommandV1 cmd = new OrderCancelCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                reason
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        applyCommandType(headers, "order.cancel");

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cmd,
                headers
        );
    }

    public void sendInventoryRelease(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = newCommandId();
        InventoryReleaseCommandV1 cmd = new InventoryReleaseCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                null
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                commandId,
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        applyCommandType(headers, "inventory.release");

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.release",
                cmd,
                headers
        );
    }

    private List<InventoryReserveCommandV1.Item> toItems(List<CheckoutSagaItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new InventoryReserveCommandV1.Item(item.productId(), item.quantity()))
                .toList();
    }

    private String newCommandId() {
        return UUID.randomUUID().toString();
    }

    private String now() {
        return Instant.now().toString();
    }

    private void applyCommandType(Map<String, String> headers, String commandType) {
        headers.put(HeaderNames.COMMAND_TYPE, commandType);
        headers.put(HeaderNames.EVENT_TYPE, commandType);
    }
}
