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

    private String lastError;
    private String lastEventId;

    private boolean orderCompleted;
    private boolean inventoryReleased;
    private boolean orderCanceled;

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
    public void markOrderCompleted() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_ORDER_COMPLETION);
        this.orderCompleted = true;
        this.status = SagaStatus.COMPLETED;
        this.step = SagaStep.DONE;
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
            String lastError,
            String lastEventId,
            boolean orderCompleted,
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
        saga.lastError = lastError;
        saga.lastEventId = lastEventId;
        saga.orderCompleted = orderCompleted;
        saga.inventoryReleased = inventoryReleased;
        saga.orderCanceled = orderCanceled;
        return saga;
    }
}
