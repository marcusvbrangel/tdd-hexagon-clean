package com.mvbr.retailstore.inventory.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.command.CommitInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.CommitInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InboxService;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryCommitCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class InventoryCommandConsumer {

    private static final Logger log = Logger.getLogger(InventoryCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final InboxService inboxService;

    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ReleaseInventoryUseCase releaseInventoryUseCase;
    private final CommitInventoryUseCase commitInventoryUseCase;

    public InventoryCommandConsumer(ObjectMapper objectMapper,
                                    InboxService inboxService,
                                    ReserveInventoryUseCase reserveInventoryUseCase,
                                    ReleaseInventoryUseCase releaseInventoryUseCase,
                                    CommitInventoryUseCase commitInventoryUseCase) {
        this.objectMapper = objectMapper;
        this.inboxService = inboxService;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.releaseInventoryUseCase = releaseInventoryUseCase;
        this.commitInventoryUseCase = commitInventoryUseCase;
    }

    @KafkaListener(
            topics = TopicNames.INVENTORY_COMMANDS_V1,
            groupId = "${spring.kafka.consumer.group-id:ms-inventory}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {

        String commandType = header(record, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> header(record, HeaderNames.EVENT_TYPE).orElse(""));

        if (commandType == null || commandType.isBlank()) {
            throw new IllegalArgumentException("Missing command type headers (COMMAND_TYPE/EVENT_TYPE)");
        }

        SagaContext sagaContext = buildSagaContext(record);

        try {
            switch (commandType) {
                case "inventory.reserve" -> handleReserve(record, sagaContext, commandType);
                case "inventory.release" -> handleRelease(record, sagaContext, commandType);
                case "inventory.commit"  -> handleCommit(record, sagaContext, commandType);
                default -> throw new IllegalArgumentException("Unknown commandType=" + commandType);
            }

            ackAfterCommit(ack);

        } catch (RuntimeException e) {
            log.severe("InventoryCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.severe("InventoryCommandConsumer failed (checked). commandType=" + commandType + " error=" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleReserve(ConsumerRecord<String, String> record, SagaContext sagaContext, String commandType) throws Exception {
        InventoryReserveCommandV1 dto = parseJson(record.value(), InventoryReserveCommandV1.class);

        InboxService.StartResult start = inboxService.tryStart(
                dto.commandId(),
                commandType,
                record.key(),
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                sagaContext
        );

        if (start == InboxService.StartResult.ALREADY_PROCESSED) {
            log.info("inventory.reserve: duplicate commandId=" + dto.commandId() + " (already processed)");
            return;
        }

        if (start == InboxService.StartResult.IN_PROGRESS) {
            // UPGRADE: não trava partição -> ACK e deixa o recovery retomar se necessário
            log.info("inventory.reserve: commandId=" + dto.commandId() + " IN_PROGRESS -> ack + defer");
            return;
        }

        try {
            List<ReserveInventoryItemCommand> items = dto.items() == null ? List.of()
                    : dto.items().stream()
                    .map(i -> new ReserveInventoryItemCommand(i.productId(), i.quantity()))
                    .toList();

            ReserveInventoryCommand cmd = new ReserveInventoryCommand(dto.commandId(), dto.orderId(), items);
            reserveInventoryUseCase.reserve(cmd, sagaContext);

            inboxService.markProcessed(dto.commandId());

        } catch (RuntimeException e) {
            inboxService.markFailed(dto.commandId(), "TECHNICAL", e.getMessage());
            throw e;
        }
    }

    private void handleRelease(ConsumerRecord<String, String> record, SagaContext sagaContext, String commandType) throws Exception {
        InventoryReleaseCommandV1 dto = parseJson(record.value(), InventoryReleaseCommandV1.class);

        InboxService.StartResult start = inboxService.tryStart(
                dto.commandId(),
                commandType,
                record.key(),
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                sagaContext
        );

        if (start == InboxService.StartResult.ALREADY_PROCESSED) {
            log.info("inventory.release: duplicate commandId=" + dto.commandId() + " (already processed)");
            return;
        }

        if (start == InboxService.StartResult.IN_PROGRESS) {
            log.info("inventory.release: commandId=" + dto.commandId() + " IN_PROGRESS -> ack + defer");
            return;
        }

        try {
            ReleaseInventoryCommand cmd = new ReleaseInventoryCommand(dto.commandId(), dto.orderId(), dto.reason());
            releaseInventoryUseCase.release(cmd, sagaContext);

            inboxService.markProcessed(dto.commandId());

        } catch (RuntimeException e) {
            inboxService.markFailed(dto.commandId(), "TECHNICAL", e.getMessage());
            throw e;
        }
    }

    private void handleCommit(ConsumerRecord<String, String> record, SagaContext sagaContext, String commandType) throws Exception {
        InventoryCommitCommandV1 dto = parseJson(record.value(), InventoryCommitCommandV1.class);

        InboxService.StartResult start = inboxService.tryStart(
                dto.commandId(),
                commandType,
                record.key(),
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                sagaContext
        );

        if (start == InboxService.StartResult.ALREADY_PROCESSED) {
            log.info("inventory.commit: duplicate commandId=" + dto.commandId() + " (already processed)");
            return;
        }

        if (start == InboxService.StartResult.IN_PROGRESS) {
            log.info("inventory.commit: commandId=" + dto.commandId() + " IN_PROGRESS -> ack + defer");
            return;
        }

        try {
            CommitInventoryCommand cmd = new CommitInventoryCommand(dto.commandId(), dto.orderId());
            commitInventoryUseCase.commit(cmd, sagaContext);

            inboxService.markProcessed(dto.commandId());

        } catch (RuntimeException e) {
            inboxService.markFailed(dto.commandId(), "TECHNICAL", e.getMessage());
            throw e;
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

    private SagaContext buildSagaContext(ConsumerRecord<String, String> record) {
        String sagaId = header(record, HeaderNames.SAGA_ID).orElse(null);
        String correlationId = header(record, HeaderNames.CORRELATION_ID).orElse(null);
        String causationId = header(record, HeaderNames.CAUSATION_ID).orElse(null);
        String sagaName = header(record, HeaderNames.SAGA_NAME).orElse(null);
        String sagaStep = header(record, HeaderNames.SAGA_STEP).orElse(null);
        String aggregateType = header(record, HeaderNames.AGGREGATE_TYPE).orElse(null);
        String aggregateId = header(record, HeaderNames.AGGREGATE_ID).orElse(record.key());

        return new SagaContext(sagaId, correlationId, causationId, sagaName, sagaStep, aggregateType, aggregateId);
    }

    private Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    private void ackAfterCommit(Acknowledgment ack) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
            }
        });
    }
}
