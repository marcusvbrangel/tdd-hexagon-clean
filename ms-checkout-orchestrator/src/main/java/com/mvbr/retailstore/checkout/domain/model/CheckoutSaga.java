package com.mvbr.retailstore.checkout.domain.model;

import com.mvbr.retailstore.checkout.domain.exception.SagaDomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de dominio que representa a saga de checkout e suas transicoes.
 * Atualizado pelo CheckoutSagaEngine com eventos recebidos e pelo scheduler de timeout.
 */
public class CheckoutSaga {

    private final String orderId;
    private final String sagaId;
    private final String correlationId;

    private SagaStatus status;
    private SagaStep step;

    private String customerId;
    private String amount;
    private String currency;
    private String paymentMethod;

    private List<CheckoutSagaItem> items;

    private Instant deadlineAt;
    private int attemptsInventory;
    private int attemptsPayment;
    private int attemptsOrderCompletion;
    private int attemptsPaymentCapture;
    private int attemptsInventoryCommit;

    private String lastError;
    private String lastEventId;

    private boolean orderCompleted;
    private boolean paymentCaptured;
    private boolean inventoryCommitted;
    private boolean inventoryReleased;
    private boolean orderCanceled;

    private String inventoryReserveCommandId;
    private String paymentAuthorizeCommandId;
    private String orderCompleteCommandId;
    private String paymentCaptureCommandId;
    private String inventoryCommitCommandId;
    private String inventoryReleaseCommandId;
    private String orderCancelCommandId;

    /**
     * Construtor privado que valida invariantes basicas.
     * Usado pelos factories start() e restore().
     */
    private CheckoutSaga(String orderId, String sagaId, String correlationId) {
        if (orderId == null || orderId.isBlank()) {
            throw new SagaDomainException("orderId cannot be null/blank");
        }
        if (sagaId == null || sagaId.isBlank()) {
            throw new SagaDomainException("sagaId cannot be null/blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new SagaDomainException("correlationId cannot be null/blank");
        }

        this.orderId = orderId;
        this.sagaId = sagaId;
        this.correlationId = correlationId;

        this.status = SagaStatus.RUNNING;
        this.step = SagaStep.STARTED;
        this.currency = "BRL";
        this.items = List.of();
        this.deadlineAt = null;
    }

    /**
     * Inicia uma nova saga para um pedido.
     * Chamado pelo CheckoutSagaEngine quando recebe order.placed.
     */
    public static CheckoutSaga start(String orderId, String correlationId) {
        return new CheckoutSaga(orderId, UUID.randomUUID().toString(), correlationId);
    }

    /**
     * Registra os dados do pedido e avanca para a reserva de estoque.
     * Chamado pelo CheckoutSagaEngine apos processar order.placed.
     */
    public void onOrderPlaced(String customerId,
                              String amount,
                              String currency,
                              String paymentMethod,
                              List<CheckoutSagaItem> items,
                              Instant deadlineAt) {
        if (status != SagaStatus.RUNNING || this.step != SagaStep.STARTED) {
            return;
        }

        this.customerId = required(customerId, "customerId");
        this.amount = required(amount, "amount");
        if (currency != null && !currency.isBlank()) {
            this.currency = currency;
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            this.paymentMethod = paymentMethod;
        }

        this.items = sanitizeItems(items);

        this.step = SagaStep.WAIT_INVENTORY;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsInventory = 0;
        this.lastError = null;
    }

    /**
     * Avanca a saga quando o estoque foi reservado.
     * Chamado pelo CheckoutSagaEngine ao receber inventory.reserved.
     */
    public void onInventoryReserved(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.step = SagaStep.WAIT_PAYMENT;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsPayment = 0;
        this.lastError = null;
    }

    /**
     * Cancela a saga quando a reserva de estoque falha.
     * Chamado pelo CheckoutSagaEngine ao receber inventory.rejected.
     */
    public void onInventoryRejected(String reason) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.status = SagaStatus.CANCELED;
        this.step = SagaStep.DONE;
        this.orderCanceled = true;
        this.lastError = reason;
        this.deadlineAt = null;
    }

    /**
     * Avanca a saga quando o pagamento e autorizado.
     * Chamado pelo CheckoutSagaEngine ao receber payment.authorized.
     */
    public void onPaymentAuthorized(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.step = SagaStep.WAIT_ORDER_COMPLETION;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsOrderCompletion = 0;
        this.lastError = null;
    }

    /**
     * Cancela a saga quando o pagamento e recusado.
     * Chamado pelo CheckoutSagaEngine ao receber payment.declined.
     */
    public void onPaymentDeclined(String reason) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.status = SagaStatus.CANCELED;
        this.step = SagaStep.DONE;
        this.inventoryReleased = true;
        this.orderCanceled = true;
        this.lastError = reason;
        this.deadlineAt = null;
    }

    /**
     * Finaliza a saga quando o pedido foi concluido no servico de orders.
     * Chamado pelo CheckoutSagaEngine ao receber order.completed.
     */
    public void onOrderCompleted(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_ORDER_COMPLETION);
        this.orderCompleted = true;
        this.step = SagaStep.WAIT_PAYMENT_CAPTURE;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsPaymentCapture = 0;
        this.lastError = null;
    }

    /**
     * Avanca a saga quando o pagamento foi capturado.
     * Chamado pelo CheckoutSagaEngine ao receber payment.captured.
     */
    public void onPaymentCaptured(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT_CAPTURE);
        this.paymentCaptured = true;
        this.step = SagaStep.WAIT_INVENTORY_COMMIT;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsInventoryCommit = 0;
        this.lastError = null;
    }

    /**
     * Finaliza a saga quando o estoque foi efetivado.
     * Chamado pelo CheckoutSagaEngine ao receber inventory.committed.
     */
    public void markInventoryCommitted() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_INVENTORY_COMMIT);
        this.inventoryCommitted = true;
        this.status = SagaStatus.COMPLETED;
        this.step = SagaStep.DONE;
        this.deadlineAt = null;
    }

    /**
     * Cancela a saga quando a captura do pagamento falha definitivamente.
     */
    public void onPaymentCaptureFailed(String reason) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT_CAPTURE);
        this.status = SagaStatus.CANCELED;
        this.step = SagaStep.DONE;
        this.lastError = reason;
        this.deadlineAt = null;
    }

    /**
     * Marca a compensacao de estoque como concluida.
     * Chamado pelo CheckoutSagaEngine ao receber inventory.released.
     */
    public void markInventoryReleased() {
        this.inventoryReleased = true;
    }

    /**
     * Marca o pedido como cancelado e encerra a saga se ainda estiver rodando.
     * Chamado pelo CheckoutSagaEngine ao receber order.canceled ou por timeout.
     */
    public void markOrderCanceled(String reason) {
        this.orderCanceled = true;
        if (reason != null && !reason.isBlank()) {
            this.lastError = reason;
        }
        if (status == SagaStatus.RUNNING) {
            this.status = SagaStatus.CANCELED;
            this.step = SagaStep.DONE;
            this.deadlineAt = null;
        }
    }

    /**
     * Marca falha de commit de estoque sem alterar o estado do pedido.
     */
    public void markInventoryCommitFailed(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.lastError = reason;
        }
        if (status == SagaStatus.RUNNING) {
            this.status = SagaStatus.CANCELED;
            this.step = SagaStep.DONE;
            this.deadlineAt = null;
        }
    }

    /**
     * Agenda nova tentativa de reserva de estoque.
     * Chamado pelo CheckoutSagaTimeoutScheduler quando o timeout expira.
     */
    public void scheduleInventoryRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.attemptsInventory += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    /**
     * Agenda nova tentativa de autorizacao de pagamento.
     * Chamado pelo CheckoutSagaTimeoutScheduler quando o timeout expira.
     */
    public void schedulePaymentRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.attemptsPayment += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    /**
     * Agenda nova tentativa de concluir o pedido.
     * Chamado pelo CheckoutSagaTimeoutScheduler quando o timeout expira.
     */
    public void scheduleOrderCompletionRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_ORDER_COMPLETION);
        this.attemptsOrderCompletion += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    /**
     * Agenda nova tentativa de captura do pagamento.
     * Chamado pelo CheckoutSagaTimeoutScheduler quando o timeout expira.
     */
    public void schedulePaymentCaptureRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_PAYMENT_CAPTURE);
        this.attemptsPaymentCapture += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    /**
     * Agenda nova tentativa de commit do estoque.
     * Chamado pelo CheckoutSagaTimeoutScheduler quando o timeout expira.
     */
    public void scheduleInventoryCommitRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_INVENTORY_COMMIT);
        this.attemptsInventoryCommit += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    /**
     * Guarda o ultimo eventId processado para correlacionar reenvios e timeouts.
     */
    public void recordLastEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        this.lastEventId = eventId;
    }

    /**
     * Garante que a transicao esta na etapa esperada.
     */
    private void ensureStep(SagaStep expected) {
        if (this.step != expected) {
            throw new SagaDomainException("Invalid step transition: expected " + expected + " but was " + step);
        }
    }

    /**
     * Valida campos obrigatorios do evento de entrada.
     */
    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new SagaDomainException(name + " cannot be null/blank");
        }
        return value;
    }

    /**
     * Normaliza a lista de itens para evitar mutacoes externas.
     */
    private List<CheckoutSagaItem> sanitizeItems(List<CheckoutSagaItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(items));
    }

    /**
     * Identificador do pedido, usado como chave de persistencia e roteamento.
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Identificador unico da saga, propagado em headers e persistencia.
     */
    public String getSagaId() {
        return sagaId;
    }

    /**
     * Correlacao entre eventos do mesmo pedido.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Status geral da saga para controle de fluxo.
     */
    public SagaStatus getStatus() {
        return status;
    }

    /**
     * Etapa atual da saga usada para validar transicoes e timeouts.
     */
    public SagaStep getStep() {
        return step;
    }

    /**
     * Identificador do cliente associado ao pedido.
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Valor total do pedido no formato string.
     */
    public String getAmount() {
        return amount;
    }

    /**
     * Moeda usada no pagamento.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Metodo de pagamento selecionado.
     */
    public String getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Itens do pedido usados para reserva de estoque.
     */
    public List<CheckoutSagaItem> getItems() {
        return items;
    }

    /**
     * Momento limite para a etapa atual.
     */
    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    /**
     * Numero de tentativas de reserva de estoque.
     */
    public int getAttemptsInventory() {
        return attemptsInventory;
    }

    /**
     * Numero de tentativas de autorizacao de pagamento.
     */
    public int getAttemptsPayment() {
        return attemptsPayment;
    }

    /**
     * Numero de tentativas de conclusao do pedido.
     */
    public int getAttemptsOrderCompletion() {
        return attemptsOrderCompletion;
    }

    /**
     * Numero de tentativas de captura de pagamento.
     */
    public int getAttemptsPaymentCapture() {
        return attemptsPaymentCapture;
    }

    /**
     * Numero de tentativas de commit do estoque.
     */
    public int getAttemptsInventoryCommit() {
        return attemptsInventoryCommit;
    }

    /**
     * Ultimo erro registrado durante a saga.
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Ultimo eventId processado pela saga.
     */
    public String getLastEventId() {
        return lastEventId;
    }

    /**
     * Indica se o pedido ja foi concluido com sucesso.
     */
    public boolean isOrderCompleted() {
        return orderCompleted;
    }

    /**
     * Indica se o pagamento ja foi capturado.
     */
    public boolean isPaymentCaptured() {
        return paymentCaptured;
    }

    /**
     * Indica se o estoque ja foi efetivado.
     */
    public boolean isInventoryCommitted() {
        return inventoryCommitted;
    }

    /**
     * Indica se o estoque ja foi liberado na compensacao.
     */
    public boolean isInventoryReleased() {
        return inventoryReleased;
    }

    /**
     * Indica se o pedido foi cancelado.
     */
    public boolean isOrderCanceled() {
        return orderCanceled;
    }

    /**
     * CommandId persistido para reserva de estoque.
     */
    public String getInventoryReserveCommandId() {
        return inventoryReserveCommandId;
    }

    /**
     * CommandId persistido para autorizacao de pagamento.
     */
    public String getPaymentAuthorizeCommandId() {
        return paymentAuthorizeCommandId;
    }

    /**
     * CommandId persistido para conclusao do pedido.
     */
    public String getOrderCompleteCommandId() {
        return orderCompleteCommandId;
    }

    /**
     * CommandId persistido para captura de pagamento.
     */
    public String getPaymentCaptureCommandId() {
        return paymentCaptureCommandId;
    }

    /**
     * CommandId persistido para commit do estoque.
     */
    public String getInventoryCommitCommandId() {
        return inventoryCommitCommandId;
    }

    /**
     * CommandId persistido para liberacao de estoque.
     */
    public String getInventoryReleaseCommandId() {
        return inventoryReleaseCommandId;
    }

    /**
     * CommandId persistido para cancelamento do pedido.
     */
    public String getOrderCancelCommandId() {
        return orderCancelCommandId;
    }

    /**
     * Gera commandId estavel para reserva de estoque.
     */
    public String getOrCreateInventoryReserveCommandId() {
        if (inventoryReserveCommandId == null || inventoryReserveCommandId.isBlank()) {
            inventoryReserveCommandId = UUID.randomUUID().toString();
        }
        return inventoryReserveCommandId;
    }

    /**
     * Gera commandId estavel para autorizacao de pagamento.
     */
    public String getOrCreatePaymentAuthorizeCommandId() {
        if (paymentAuthorizeCommandId == null || paymentAuthorizeCommandId.isBlank()) {
            paymentAuthorizeCommandId = UUID.randomUUID().toString();
        }
        return paymentAuthorizeCommandId;
    }

    /**
     * Gera commandId estavel para conclusao do pedido.
     */
    public String getOrCreateOrderCompleteCommandId() {
        if (orderCompleteCommandId == null || orderCompleteCommandId.isBlank()) {
            orderCompleteCommandId = UUID.randomUUID().toString();
        }
        return orderCompleteCommandId;
    }

    /**
     * Gera commandId estavel para captura do pagamento.
     */
    public String getOrCreatePaymentCaptureCommandId() {
        if (paymentCaptureCommandId == null || paymentCaptureCommandId.isBlank()) {
            paymentCaptureCommandId = UUID.randomUUID().toString();
        }
        return paymentCaptureCommandId;
    }

    /**
     * Gera commandId estavel para commit do estoque.
     */
    public String getOrCreateInventoryCommitCommandId() {
        if (inventoryCommitCommandId == null || inventoryCommitCommandId.isBlank()) {
            inventoryCommitCommandId = UUID.randomUUID().toString();
        }
        return inventoryCommitCommandId;
    }

    /**
     * Gera commandId estavel para liberacao de estoque.
     */
    public String getOrCreateInventoryReleaseCommandId() {
        if (inventoryReleaseCommandId == null || inventoryReleaseCommandId.isBlank()) {
            inventoryReleaseCommandId = UUID.randomUUID().toString();
        }
        return inventoryReleaseCommandId;
    }

    /**
     * Gera commandId estavel para cancelamento do pedido.
     */
    public String getOrCreateOrderCancelCommandId() {
        if (orderCancelCommandId == null || orderCancelCommandId.isBlank()) {
            orderCancelCommandId = UUID.randomUUID().toString();
        }
        return orderCancelCommandId;
    }

    public void clearInventoryReserveCommandId() {
        this.inventoryReserveCommandId = null;
    }

    public void clearPaymentAuthorizeCommandId() {
        this.paymentAuthorizeCommandId = null;
    }

    public void clearOrderCompleteCommandId() {
        this.orderCompleteCommandId = null;
    }

    public void clearPaymentCaptureCommandId() {
        this.paymentCaptureCommandId = null;
    }

    public void clearInventoryCommitCommandId() {
        this.inventoryCommitCommandId = null;
    }

    public void clearInventoryReleaseCommandId() {
        this.inventoryReleaseCommandId = null;
    }

    public void clearOrderCancelCommandId() {
        this.orderCancelCommandId = null;
    }

    /**
     * Restaura a saga a partir de dados persistidos.
     * Chamado pelo adapter JPA ao ler do banco.
     */
    public static CheckoutSaga restore(
            String orderId,
            String sagaId,
            String correlationId,
            SagaStatus status,
            SagaStep step,
            String customerId,
            String amount,
            String currency,
            String paymentMethod,
            List<CheckoutSagaItem> items,
            Instant deadlineAt,
            int attemptsInventory,
            int attemptsPayment,
            int attemptsOrderCompletion,
            int attemptsPaymentCapture,
            int attemptsInventoryCommit,
            String lastError,
            String lastEventId,
            String inventoryReserveCommandId,
            String paymentAuthorizeCommandId,
            String orderCompleteCommandId,
            String paymentCaptureCommandId,
            String inventoryCommitCommandId,
            String inventoryReleaseCommandId,
            String orderCancelCommandId,
            boolean orderCompleted,
            boolean paymentCaptured,
            boolean inventoryCommitted,
            boolean inventoryReleased,
            boolean orderCanceled
    ) {
        CheckoutSaga saga = new CheckoutSaga(orderId, sagaId, correlationId);
        saga.status = Objects.requireNonNull(status, "status");
        saga.step = Objects.requireNonNull(step, "step");
        saga.customerId = customerId;
        saga.amount = amount;
        saga.currency = (currency == null || currency.isBlank()) ? "BRL" : currency;
        saga.paymentMethod = paymentMethod;
        saga.items = (items == null) ? List.of() : List.copyOf(items);
        saga.deadlineAt = deadlineAt;
        saga.attemptsInventory = attemptsInventory;
        saga.attemptsPayment = attemptsPayment;
        saga.attemptsOrderCompletion = attemptsOrderCompletion;
        saga.attemptsPaymentCapture = attemptsPaymentCapture;
        saga.attemptsInventoryCommit = attemptsInventoryCommit;
        saga.lastError = lastError;
        saga.lastEventId = lastEventId;
        saga.inventoryReserveCommandId = inventoryReserveCommandId;
        saga.paymentAuthorizeCommandId = paymentAuthorizeCommandId;
        saga.orderCompleteCommandId = orderCompleteCommandId;
        saga.paymentCaptureCommandId = paymentCaptureCommandId;
        saga.inventoryCommitCommandId = inventoryCommitCommandId;
        saga.inventoryReleaseCommandId = inventoryReleaseCommandId;
        saga.orderCancelCommandId = orderCancelCommandId;
        saga.orderCompleted = orderCompleted;
        saga.paymentCaptured = paymentCaptured;
        saga.inventoryCommitted = inventoryCommitted;
        saga.inventoryReleased = inventoryReleased;
        saga.orderCanceled = orderCanceled;
        return saga;
    }
}
