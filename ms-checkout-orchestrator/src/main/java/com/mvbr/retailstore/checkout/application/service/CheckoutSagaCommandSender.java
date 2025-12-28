package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryCommitCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCancelCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompleteCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCaptureCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
/**
 * Construtor de comandos da saga e ponto de saida para o barramento.
 * Chamado pelo CheckoutSagaEngine e pelo CheckoutSagaTimeoutScheduler.
 */
public class CheckoutSagaCommandSender {

    private static final String SAGA_NAME = "checkout";
    private static final String AGGREGATE_TYPE = "Order";

    private final CommandPublisher commandPublisher;

    public CheckoutSagaCommandSender(CommandPublisher commandPublisher) {
        this.commandPublisher = commandPublisher;
    }

    /**
     * Envia comando para reservar estoque.
     * Fluxo: CheckoutSagaEngine/TimeoutScheduler -> este sender -> CommandPublisher.
     */
    public void sendInventoryReserve(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreateInventoryReserveCommandId();
        InventoryReserveCommandV1 cmd = new InventoryReserveCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                toItems(saga.getItems())
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "inventory.reserve");

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.reserve",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para autorizar pagamento.
     * Chamado apos inventory.reserved ou por retry de timeout.
     */
    public void sendPaymentAuthorize(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreatePaymentAuthorizeCommandId();
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
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "payment.authorize");

        commandPublisher.publish(
                TopicNames.PAYMENT_COMMANDS_V1,
                saga.getOrderId(),
                "payment.authorize",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para concluir o pedido no servico de orders.
     */
    public void sendOrderComplete(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreateOrderCompleteCommandId();
        OrderCompleteCommandV1 cmd = new OrderCompleteCommandV1(
                commandId,
                now(),
                saga.getOrderId()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "order.complete");

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.complete",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para capturar pagamento autorizado.
     */
    public void sendPaymentCapture(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreatePaymentCaptureCommandId();
        PaymentCaptureCommandV1 cmd = new PaymentCaptureCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                null
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "payment.capture");

        commandPublisher.publish(
                TopicNames.PAYMENT_COMMANDS_V1,
                saga.getOrderId(),
                "payment.capture",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para cancelar o pedido (compensacao).
     */
    public void sendOrderCancel(CheckoutSaga saga, String causationId, String sagaStep, String reason) {
        String commandId = saga.getOrCreateOrderCancelCommandId();
        OrderCancelCommandV1 cmd = new OrderCancelCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                reason
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "order.cancel");

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para liberar estoque (compensacao).
     */
    public void sendInventoryRelease(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreateInventoryReleaseCommandId();
        InventoryReleaseCommandV1 cmd = new InventoryReleaseCommandV1(
                commandId,
                now(),
                saga.getOrderId(),
                null
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "inventory.release");

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.release",
                cmd,
                headers
        );
    }

    /**
     * Envia comando para efetivar o estoque apos captura do pagamento.
     */
    public void sendInventoryCommit(CheckoutSaga saga, String causationId, String sagaStep) {
        String commandId = saga.getOrCreateInventoryCommitCommandId();
        InventoryCommitCommandV1 cmd = new InventoryCommitCommandV1(
                commandId,
                now(),
                saga.getOrderId()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                newEventId(),
                saga.getSagaId(),
                saga.getCorrelationId(),
                causationId,
                SAGA_NAME,
                sagaStep,
                AGGREGATE_TYPE,
                saga.getOrderId()
        );
        headers.put(HeaderNames.COMMAND_ID, commandId);
        applyCommandType(headers, "inventory.commit");

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.commit",
                cmd,
                headers
        );
    }

    /**
     * Converte itens do dominio para o formato do comando de estoque.
     */
    private List<InventoryReserveCommandV1.Item> toItems(List<CheckoutSagaItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new InventoryReserveCommandV1.Item(item.productId(), item.quantity()))
                .toList();
    }

    /**
     * Timestamp padrao para eventos de comando.
     */
    private String now() {
        return Instant.now().toString();
    }

    /**
     * Ajusta headers de tipo de comando e evento para roteamento.
     */
    private void applyCommandType(Map<String, String> headers, String commandType) {
        headers.put(HeaderNames.COMMAND_TYPE, commandType);
        headers.put(HeaderNames.EVENT_TYPE, commandType);
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
