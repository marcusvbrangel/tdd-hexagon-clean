package com.mvbr.retailstore.notification.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.notification.application.command.SendNotificationCommand;
import com.mvbr.retailstore.notification.application.command.SagaContext;
import com.mvbr.retailstore.notification.application.port.in.SendNotificationUseCase;
import com.mvbr.retailstore.notification.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.notification.infrastructure.adapter.out.messaging.dto.NotificationSendCommandV1;
import com.mvbr.retailstore.notification.infrastructure.observability.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class NotificationCommandConsumer {

    private static final Logger log = Logger.getLogger(NotificationCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final SendNotificationUseCase sendNotificationUseCase;

    public NotificationCommandConsumer(ObjectMapper objectMapper,
                                       SendNotificationUseCase sendNotificationUseCase) {
        this.objectMapper = objectMapper;
        this.sendNotificationUseCase = sendNotificationUseCase;
    }

    @KafkaListener(
            topics = TopicNames.NOTIFICATION_COMMANDS_V1,
            groupId = "${spring.kafka.consumer.group-id:ms-notification}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String commandType = header(record, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> header(record, HeaderNames.EVENT_TYPE).orElse(""));

        if (commandType == null || commandType.isBlank()) {
            log.warning("NotificationCommandConsumer: missing command type headers. Ignoring message.");
            return;
        }

        if (!"notification.send".equals(commandType)) {
            log.warning("NotificationCommandConsumer: unknown commandType=" + commandType);
            return;
        }
        if (record.value() == null || record.value().isBlank()) {
            log.warning("NotificationCommandConsumer: empty payload. commandType=" + commandType);
            return;
        }

        SagaContext sagaContext = buildSagaContext(record);

        try {
            NotificationSendCommandV1 dto = objectMapper.readValue(record.value(), NotificationSendCommandV1.class);
            SendNotificationCommand cmd = new SendNotificationCommand(
                    dto.commandId(),
                    dto.occurredAt(),
                    dto.orderId(),
                    dto.customerId(),
                    dto.recipient(),
                    dto.template(),
                    dto.channels(),
                    dto.data()
            );
            sendNotificationUseCase.send(cmd, sagaContext);
        } catch (Exception e) {
            log.severe("NotificationCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
            throw new RuntimeException(e);
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
}
