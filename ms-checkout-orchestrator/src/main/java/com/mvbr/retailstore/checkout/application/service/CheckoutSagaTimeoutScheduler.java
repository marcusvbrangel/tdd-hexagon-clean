package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.config.SagaProperties;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
/**
 * Scheduler que varre sagas expiradas e dispara retries ou compensacoes.
 * Chamado periodicamente pelo Spring Scheduling.
 */
public class CheckoutSagaTimeoutScheduler {

    private static final Logger log = Logger.getLogger(CheckoutSagaTimeoutScheduler.class.getName());

    private static final String REASON_INVENTORY_TIMEOUT = "INVENTORY_TIMEOUT";
    private static final String REASON_PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";
    private static final String REASON_ORDER_TIMEOUT = "ORDER_TIMEOUT";
    private static final String REASON_PAYMENT_CAPTURE_TIMEOUT = "PAYMENT_CAPTURE_TIMEOUT";
    private static final String REASON_INVENTORY_COMMIT_TIMEOUT = "INVENTORY_COMMIT_TIMEOUT";

    private final CheckoutSagaRepository sagaRepository;
    private final CheckoutSagaCommandSender commandSender;
    private final SagaProperties sagaProperties;

    public CheckoutSagaTimeoutScheduler(CheckoutSagaRepository sagaRepository,
                                        CheckoutSagaCommandSender commandSender,
                                        SagaProperties sagaProperties) {
        this.sagaRepository = sagaRepository;
        this.commandSender = commandSender;
        this.sagaProperties = sagaProperties;
    }

    /**
     * Ponto de entrada do scheduler: busca sagas vencidas e trata uma a uma.
     * Fluxo: Spring Scheduler -> tick() -> handleTimeout().
     */
    @Scheduled(fixedDelayString = "${saga.timeouts.scanFixedDelayMs:5000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        List<CheckoutSaga> timedOut = sagaRepository.findTimedOut(now);
        if (!timedOut.isEmpty()) {
            log.info("CheckoutSagaTimeoutScheduler tick - due sagas: " + timedOut.size());
        }
        for (CheckoutSaga saga : timedOut) {
            handleTimeout(saga);
        }
    }

    /**
     * Direciona o timeout para a acao correta conforme a etapa atual.
     */
    private void handleTimeout(CheckoutSaga saga) {
        String causationId = Optional.ofNullable(saga.getLastEventId()).orElse(saga.getSagaId());
        switch (saga.getStep()) {
            case WAIT_INVENTORY -> handleInventoryTimeout(saga, causationId);
            case WAIT_PAYMENT -> handlePaymentTimeout(saga, causationId);
            case WAIT_ORDER_COMPLETION -> handleOrderCompletionTimeout(saga, causationId);
            case WAIT_PAYMENT_CAPTURE -> handlePaymentCaptureTimeout(saga, causationId);
            case WAIT_INVENTORY_COMMIT -> handleInventoryCommitTimeout(saga, causationId);
            default -> { }
        }
    }

    /**
     * Trata timeout de estoque: retry ou compensacao (cancelar pedido).
     */
    private void handleInventoryTimeout(CheckoutSaga saga, String causationId) {
        int maxRetries = sagaProperties.getRetries().getInventoryMax();
        if (saga.getAttemptsInventory() < maxRetries) {
            saga.scheduleInventoryRetry(deadlineAfterSeconds(sagaProperties.getTimeouts().getInventorySeconds()));
            saga.getOrCreateInventoryReserveCommandId();
            sagaRepository.save(saga);
            commandSender.sendInventoryReserve(saga, causationId, SagaStep.WAIT_INVENTORY.name());
            return;
        }

        saga.onInventoryRejected(REASON_INVENTORY_TIMEOUT);
        saga.clearInventoryReserveCommandId();
        saga.getOrCreateOrderCancelCommandId();
        sagaRepository.save(saga);
        commandSender.sendOrderCancel(saga, causationId, SagaStep.COMPENSATING.name(), REASON_INVENTORY_TIMEOUT);
    }

    /**
     * Trata timeout de pagamento: retry ou compensacao (liberar estoque + cancelar).
     */
    private void handlePaymentTimeout(CheckoutSaga saga, String causationId) {
        int maxRetries = sagaProperties.getRetries().getPaymentMax();
        if (saga.getAttemptsPayment() < maxRetries) {
            saga.schedulePaymentRetry(deadlineAfterSeconds(sagaProperties.getTimeouts().getPaymentSeconds()));
            saga.getOrCreatePaymentAuthorizeCommandId();
            sagaRepository.save(saga);
            commandSender.sendPaymentAuthorize(saga, causationId, SagaStep.WAIT_PAYMENT.name());
            return;
        }

        saga.onPaymentDeclined(REASON_PAYMENT_TIMEOUT);
        saga.clearPaymentAuthorizeCommandId();
        saga.getOrCreateInventoryReleaseCommandId();
        saga.getOrCreateOrderCancelCommandId();
        sagaRepository.save(saga);
        commandSender.sendInventoryRelease(saga, causationId, SagaStep.COMPENSATING.name());
        commandSender.sendOrderCancel(saga, causationId, SagaStep.COMPENSATING.name(), REASON_PAYMENT_TIMEOUT);
    }

    /**
     * Trata timeout de conclusao do pedido: retry ou cancelamento local da saga.
     */
    private void handleOrderCompletionTimeout(CheckoutSaga saga, String causationId) {
        int maxRetries = sagaProperties.getRetries().getOrderCompleteMax();
        if (saga.getAttemptsOrderCompletion() < maxRetries) {
            saga.scheduleOrderCompletionRetry(deadlineAfterSeconds(
                    sagaProperties.getTimeouts().getOrderCompleteSeconds()));
            saga.getOrCreateOrderCompleteCommandId();
            sagaRepository.save(saga);
            commandSender.sendOrderComplete(saga, causationId, SagaStep.WAIT_ORDER_COMPLETION.name());
            return;
        }

        saga.markOrderCanceled(REASON_ORDER_TIMEOUT);
        saga.clearOrderCompleteCommandId();
        sagaRepository.save(saga);
    }

    /**
     * Trata timeout de captura: retry ou compensacao (liberar estoque).
     */
    private void handlePaymentCaptureTimeout(CheckoutSaga saga, String causationId) {
        int maxRetries = sagaProperties.getRetries().getPaymentCaptureMax();
        if (saga.getAttemptsPaymentCapture() < maxRetries) {
            saga.schedulePaymentCaptureRetry(deadlineAfterSeconds(
                    sagaProperties.getTimeouts().getPaymentCaptureSeconds()));
            saga.getOrCreatePaymentCaptureCommandId();
            sagaRepository.save(saga);
            commandSender.sendPaymentCapture(saga, causationId, SagaStep.WAIT_PAYMENT_CAPTURE.name());
            return;
        }

        saga.onPaymentCaptureFailed(REASON_PAYMENT_CAPTURE_TIMEOUT);
        saga.clearPaymentCaptureCommandId();
        saga.getOrCreateInventoryReleaseCommandId();
        sagaRepository.save(saga);
        commandSender.sendInventoryRelease(saga, causationId, SagaStep.COMPENSATING.name());
    }

    /**
     * Trata timeout de commit do estoque: retry ou encerramento com erro.
     */
    private void handleInventoryCommitTimeout(CheckoutSaga saga, String causationId) {
        int maxRetries = sagaProperties.getRetries().getInventoryCommitMax();
        if (saga.getAttemptsInventoryCommit() < maxRetries) {
            saga.scheduleInventoryCommitRetry(deadlineAfterSeconds(
                    sagaProperties.getTimeouts().getInventoryCommitSeconds()));
            saga.getOrCreateInventoryCommitCommandId();
            sagaRepository.save(saga);
            commandSender.sendInventoryCommit(saga, causationId, SagaStep.WAIT_INVENTORY_COMMIT.name());
            return;
        }

        saga.markInventoryCommitFailed(REASON_INVENTORY_COMMIT_TIMEOUT);
        saga.clearInventoryCommitCommandId();
        sagaRepository.save(saga);
    }

    /**
     * Calcula um novo deadline a partir de agora.
     */
    private Instant deadlineAfterSeconds(long seconds) {
        return Instant.now().plusSeconds(seconds);
    }
}
