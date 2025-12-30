package com.mvbr.retailstore.inventory.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.command.CommitInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.CommitInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox.InboxCommandJpaEntity;
import com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox.InboxCommandJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryCommitCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReserveCommandV1;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

@Component
public class InboxRecoveryScheduler {

    private static final Logger log = Logger.getLogger(InboxRecoveryScheduler.class.getName());

    private final InboxCommandJpaRepository inboxRepo;
    private final InboxService inboxService;
    private final ClockService clock;
    private final ObjectMapper objectMapper;

    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ReleaseInventoryUseCase releaseInventoryUseCase;
    private final CommitInventoryUseCase commitInventoryUseCase;

    public InboxRecoveryScheduler(InboxCommandJpaRepository inboxRepo,
                                  InboxService inboxService,
                                  ClockService clock,
                                  ObjectMapper objectMapper,
                                  ReserveInventoryUseCase reserveInventoryUseCase,
                                  ReleaseInventoryUseCase releaseInventoryUseCase,
                                  CommitInventoryUseCase commitInventoryUseCase) {
        this.inboxRepo = inboxRepo;
        this.inboxService = inboxService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.releaseInventoryUseCase = releaseInventoryUseCase;
        this.commitInventoryUseCase = commitInventoryUseCase;
    }

    @Scheduled(fixedDelayString = "${inventory.inbox.recovery.delay-ms:5000}")
    public void recover() {
        Instant now = clock.now();

        int batchSize = inboxService.recoveryBatchSize();

        List<InboxCommandJpaEntity> due = inboxRepo.findByStatusInAndLockedUntilBeforeOrderByLockedUntilAsc(
                List.of(InboxCommandJpaEntity.Status.IN_PROGRESS, InboxCommandJpaEntity.Status.FAILED),
                now,
                PageRequest.of(0, batchSize)
        );

        if (due.isEmpty()) return;

        for (InboxCommandJpaEntity cmd : due) {
            try {
                recoverOne(cmd.getCommandId());
            } catch (Exception e) {
                log.severe("InboxRecoveryScheduler: recoverOne failed commandId=" + cmd.getCommandId() + " err=" + e.getMessage());
            }
        }
    }

    @Transactional
    protected void recoverOne(String commandId) throws Exception {
        InboxCommandJpaEntity entity = inboxRepo.findById(commandId).orElse(null);
        if (entity == null) return;

        Instant now = clock.now();

        if (entity.isProcessed()) return;
        if (entity.isLocked(now)) return;

        // BUSINESS/POISON => não retryar
        if ("BUSINESS".equalsIgnoreCase(entity.getFailureType()) || "POISON".equalsIgnoreCase(entity.getFailureType())) {
            return;
        }

        // retoma com backoff exponencial baseado na próxima tentativa
        int nextAttempt = safeNextAttempt(entity.getAttempts());
        Duration lease = inboxService.leaseForAttempt(nextAttempt);

        entity.start(now, lease);

        SagaContext saga = new SagaContext(
                entity.getSagaId(),
                entity.getCorrelationId(),
                entity.getCausationId(),
                entity.getSagaName(),
                entity.getSagaStep(),
                entity.getAggregateType(),
                entity.getAggregateId()
        );

        try {
            dispatch(entity.getCommandType(), entity.getPayloadJson(), saga);
            entity.markProcessed(now);

            log.info("InboxRecoveryScheduler: recovered commandId=" + commandId + " type=" + entity.getCommandType());

        } catch (IllegalArgumentException poison) {
            // Contrato/payload ruim => não retryar
            entity.markFailed("POISON", poison.getMessage(), now, inboxService.noRetryLease());
            log.warning("InboxRecoveryScheduler: POISON commandId=" + commandId + " msg=" + poison.getMessage());

        } catch (RuntimeException tec) {
            // TECHNICAL => aplica backoff exponencial via InboxService
            inboxService.markFailed(commandId, "TECHNICAL", tec.getMessage());
            throw tec;
        }
    }

    private void dispatch(String commandType, String payloadJson, SagaContext sagaContext) throws Exception {
        switch (commandType) {
            case "inventory.reserve" -> {
                InventoryReserveCommandV1 dto = parseJson(payloadJson, InventoryReserveCommandV1.class);

                List<ReserveInventoryItemCommand> items = dto.items() == null ? List.of()
                        : dto.items().stream()
                        .map(i -> new ReserveInventoryItemCommand(i.productId(), i.quantity()))
                        .toList();

                reserveInventoryUseCase.reserve(new ReserveInventoryCommand(dto.commandId(), dto.orderId(), items), sagaContext);
            }
            case "inventory.release" -> {
                InventoryReleaseCommandV1 dto = parseJson(payloadJson, InventoryReleaseCommandV1.class);
                releaseInventoryUseCase.release(new ReleaseInventoryCommand(dto.commandId(), dto.orderId(), dto.reason()), sagaContext);
            }
            case "inventory.commit" -> {
                InventoryCommitCommandV1 dto = parseJson(payloadJson, InventoryCommitCommandV1.class);
                commitInventoryUseCase.commit(new CommitInventoryCommand(dto.commandId(), dto.orderId()), sagaContext);
            }
            default -> throw new IllegalArgumentException("Unknown commandType=" + commandType);
        }
    }

    private <T> T parseJson(String payload, Class<T> clazz) throws Exception {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Empty payload for " + clazz.getSimpleName());
        }
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON for " + clazz.getSimpleName() + ": " + ex.getMessage(), ex);
        }
    }

    private int safeNextAttempt(int currentAttempts) {
        if (currentAttempts <= 0) return 1;
        if (currentAttempts >= Integer.MAX_VALUE - 1) return Integer.MAX_VALUE;
        return currentAttempts + 1;
    }
}
