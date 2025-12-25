package com.mvbr.retailstore.inventory.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class InventoryCommandConsumer {

    private static final Logger log = Logger.getLogger(InventoryCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ReleaseInventoryUseCase releaseInventoryUseCase;

    public InventoryCommandConsumer(ObjectMapper objectMapper,
                                    ReserveInventoryUseCase reserveInventoryUseCase,
                                    ReleaseInventoryUseCase releaseInventoryUseCase) {
        this.objectMapper = objectMapper;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.releaseInventoryUseCase = releaseInventoryUseCase;
    }

    @KafkaListener(
            topics = TopicNames.INVENTORY_COMMANDS_V1,
            groupId = "${spring.kafka.consumer.group-id:ms-inventory}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String commandType = header(record, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> header(record, HeaderNames.EVENT_TYPE).orElse(""));

        if (commandType == null || commandType.isBlank()) {
            log.warning("InventoryCommandConsumer: missing command type header. Ignoring message.");
            return;
        }

        SagaContext sagaContext = buildSagaContext(record);

        try {
            switch (commandType) {
                case "inventory.reserve" -> handleReserve(record.value(), sagaContext);
                case "inventory.release" -> handleRelease(record.value(), sagaContext);
                default -> log.warning("InventoryCommandConsumer: unknown commandType=" + commandType);
            }
        } catch (Exception e) {
            log.severe("InventoryCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleReserve(String payload, SagaContext sagaContext) throws Exception {
        InventoryReserveCommandV1 dto = objectMapper.readValue(payload, InventoryReserveCommandV1.class);

        List<ReserveInventoryItemCommand> items = dto.items() == null ? List.of()
                : dto.items().stream()
                .map(i -> new ReserveInventoryItemCommand(i.productId(), i.quantity()))
                .toList();

        ReserveInventoryCommand cmd = new ReserveInventoryCommand(dto.commandId(), dto.orderId(), items);
        reserveInventoryUseCase.reserve(cmd, sagaContext);
    }

    private void handleRelease(String payload, SagaContext sagaContext) throws Exception {
        InventoryReleaseCommandV1 dto = objectMapper.readValue(payload, InventoryReleaseCommandV1.class);
        ReleaseInventoryCommand cmd = new ReleaseInventoryCommand(dto.commandId(), dto.orderId(), dto.reason());
        releaseInventoryUseCase.release(cmd, sagaContext);
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
}
