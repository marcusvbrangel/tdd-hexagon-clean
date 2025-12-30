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
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryCommittedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCapturedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCaptureFailedEventV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentDeclinedEventV1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
/**
 * Motor central da saga: processa eventos de dominio e comanda as proximas etapas.
 * Entrada principal: CheckoutEventsConsumer chama handle().
 */
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

    /**
     * Processa um evento recebido do Kafka e despacha para o handler correto.
     * Fluxo: CheckoutEventsConsumer -> handle() -> on<Evento>() -> CommandSender.
     */
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
            case "payment.captured" -> handleIfFirst(env, orderId, this::onPaymentCaptured);
            case "payment.capture_failed" -> handleIfFirst(env, orderId, this::onPaymentCaptureFailed);
            case "inventory.committed" -> handleIfFirst(env, orderId, this::onInventoryCommitted);
            default -> false;
        };

        if (!handled) {
            log.fine("Ignoring unsupported eventType=" + eventType + " key=" + env.key());
        }
    }

    /**
     * Garante idempotencia: so processa se o evento ainda nao foi marcado.
     */
    private boolean handleIfFirst(EventEnvelope env, String orderId, EventHandler handler) {
        if (!processedEventRepository.markProcessedIfFirst(env.eventId(), env.eventType(), orderId)) {
            return true;
        }
        handler.handle(env);
        return true;
    }

    /**
     * Handler para order.placed: cria ou carrega saga, avanca para reserva de estoque.
     */
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
        saga.getOrCreateInventoryReserveCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryReserve(saga, env.eventId(), SagaStep.WAIT_INVENTORY.name());
    }

    /**
     * Handler para inventory.reserved: avanca para autorizacao de pagamento.
     */
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
        saga.clearInventoryReserveCommandId();
        saga.getOrCreatePaymentAuthorizeCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendPaymentAuthorize(saga, env.eventId(), SagaStep.WAIT_PAYMENT.name());
    }

    /**
     * Handler para inventory.rejected: registra erro e aciona cancelamento do pedido.
     */
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
        saga.clearInventoryReserveCommandId();
        saga.getOrCreateOrderCancelCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendOrderCancel(saga, env.eventId(), SagaStep.COMPENSATING.name(), REASON_INVENTORY_REJECTED);
    }

    /**
     * Handler para payment.authorized: avanca para conclusao do pedido.
     */
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
        saga.clearPaymentAuthorizeCommandId();
        saga.getOrCreateOrderCompleteCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendOrderComplete(saga, env.eventId(), SagaStep.WAIT_ORDER_COMPLETION.name());
    }

    /**
     * Handler para payment.declined: registra erro e dispara compensacoes.
     */
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
        saga.clearPaymentAuthorizeCommandId();
        saga.getOrCreateInventoryReleaseCommandId();
        saga.getOrCreateOrderCancelCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryRelease(saga, env.eventId(), SagaStep.COMPENSATING.name());
        commandSender.sendOrderCancel(saga, env.eventId(), SagaStep.COMPENSATING.name(), REASON_PAYMENT_DECLINED);
    }

    /**
     * Handler para order.completed: finaliza a saga.
     */
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

        saga.onOrderCompleted(deadlineAfterSeconds(sagaProperties.getTimeouts().getPaymentCaptureSeconds()));
        saga.clearOrderCompleteCommandId();
        saga.getOrCreatePaymentCaptureCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendPaymentCapture(saga, env.eventId(), SagaStep.WAIT_PAYMENT_CAPTURE.name());
    }

    /**
     * Handler para order.canceled: encerra a saga se ainda estava em execucao.
     */
    private void onOrderCanceled(EventEnvelope env) {
        OrderCanceledEventV1 event = env.readPayload(objectMapper, OrderCanceledEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (saga.getStatus() == SagaStatus.RUNNING) {
            saga.markOrderCanceled("ORDER_CANCELED");
            saga.clearOrderCancelCommandId();
            saga.recordLastEvent(env.eventId());
            sagaRepository.save(saga);
        }
    }

    /**
     * Handler para inventory.released: marca compensacao concluida.
     */
    private void onInventoryReleased(EventEnvelope env) {
        CheckoutSaga saga = sagaRepository.findByOrderId(env.aggregateIdOrKey()).orElse(null);
        if (saga == null) {
            log.warning("inventory.released for unknown orderId=" + env.aggregateIdOrKey());
            return;
        }
        boolean expected = saga.getInventoryReleaseCommandId() != null
                && !saga.getInventoryReleaseCommandId().isBlank();
        boolean fromCheckout = "checkout".equalsIgnoreCase(env.sagaName());
        boolean compensatingStep = saga.getStep() == SagaStep.COMPENSATING;
        if (!expected && !fromCheckout && !compensatingStep) {
            log.warning("Ignoring inventory.released outside compensation (possible expiration). orderId="
                    + saga.getOrderId()
                    + " sagaId=" + saga.getSagaId()
                    + " status=" + saga.getStatus()
                    + " step=" + saga.getStep()
                    + " eventId=" + env.eventId()
                    + " sagaName=" + env.sagaName()
                    + " sagaStep=" + env.sagaStep());
            return;
        }
        saga.markInventoryReleased();
        saga.clearInventoryReleaseCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);
    }

    /**
     * Handler para payment.captured: envia commit de estoque.
     */
    private void onPaymentCaptured(EventEnvelope env) {
        PaymentCapturedEventV1 event = env.readPayload(objectMapper, PaymentCapturedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_PAYMENT_CAPTURE, env)) {
            return;
        }

        saga.onPaymentCaptured(deadlineAfterSeconds(sagaProperties.getTimeouts().getInventoryCommitSeconds()));
        saga.clearPaymentCaptureCommandId();
        saga.getOrCreateInventoryCommitCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryCommit(saga, env.eventId(), SagaStep.WAIT_INVENTORY_COMMIT.name());
    }

    /**
     * Handler para payment.capture_failed: inicia compensacao.
     */
    private void onPaymentCaptureFailed(EventEnvelope env) {
        PaymentCaptureFailedEventV1 event = env.readPayload(objectMapper, PaymentCaptureFailedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_PAYMENT_CAPTURE, env)) {
            return;
        }

        String lastError = errorWithDetail("PAYMENT_CAPTURE_FAILED", event.reason());
        saga.onPaymentCaptureFailed(lastError);
        saga.clearPaymentCaptureCommandId();
        saga.getOrCreateInventoryReleaseCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);

        commandSender.sendInventoryRelease(saga, env.eventId(), SagaStep.COMPENSATING.name());
    }

    /**
     * Handler para inventory.committed: encerra a saga.
     */
    private void onInventoryCommitted(EventEnvelope env) {
        InventoryCommittedEventV1 event = env.readPayload(objectMapper, InventoryCommittedEventV1.class);
        String orderId = resolveOrderId(event.orderId(), env);
        CheckoutSaga saga = findSaga(orderId, env.eventType(), env.eventId());
        if (saga == null) {
            return;
        }
        if (!isExpectedStep(saga, SagaStep.WAIT_INVENTORY_COMMIT, env)) {
            return;
        }

        saga.markInventoryCommitted();
        saga.clearInventoryCommitCommandId();
        saga.recordLastEvent(env.eventId());
        sagaRepository.save(saga);
    }

    /**
     * Verifica se o evento chegou na etapa esperada antes de aplicar transicao.
     */
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

    /**
     * Localiza a saga por orderId e registra logs quando nao encontrada.
     */
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

    /**
     * Resolve o orderId priorizando o payload e usando o headers como fallback.
     */
    private String resolveOrderId(String eventOrderId, EventEnvelope env) {
        if (eventOrderId != null && !eventOrderId.isBlank()) {
            return eventOrderId;
        }
        return env.aggregateIdOrKey();
    }

    /**
     * Calcula total e moeda a partir do evento de pedido, com fallback para itens.
     */
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

    /**
     * Recalcula o total a partir dos itens e desconto do pedido.
     */
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

    /**
     * Faz parse de string em BigDecimal, retornando null quando invalido.
     */
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

    /**
     * Faz parse com fallback para valor padrao quando invalido.
     */
    private BigDecimal parseBigDecimal(String value, BigDecimal fallback) {
        BigDecimal parsed = parseBigDecimal(value);
        return parsed == null ? fallback : parsed;
    }

    /**
     * Converte itens do evento para itens do dominio.
     */
    private List<CheckoutSagaItem> toSagaItems(List<OrderPlacedEventV1.Item> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new CheckoutSagaItem(item.productId(), item.quantity()))
                .toList();
    }

    /**
     * Calcula deadline relativo para a proxima etapa.
     */
    private Instant deadlineAfterSeconds(long seconds) {
        return Instant.now().plusSeconds(seconds);
    }

    /**
     * Monta mensagem de erro base + detalhe opcional.
     */
    private String errorWithDetail(String base, String detail) {
        if (detail == null || detail.isBlank()) {
            return base;
        }
        return base + ":" + detail;
    }

    /**
     * Resumo de total e moeda usado internamente no processamento.
     */
    private record AmountSummary(String total, String currency) {}

    /**
     * Interface funcional para handlers de eventos tipados.
     */
    private interface EventHandler {
        void handle(EventEnvelope env);
    }
}
