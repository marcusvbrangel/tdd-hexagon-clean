package com.mvbr.retailstore.payment.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.payment.application.command.AuthorizePaymentCommand;
import com.mvbr.retailstore.payment.application.command.CapturePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;
import com.mvbr.retailstore.payment.application.port.in.AuthorizePaymentUseCase;
import com.mvbr.retailstore.payment.application.port.in.CapturePaymentUseCase;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto.PaymentCaptureCommandV1;
import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Adaptador de entrada Kafka: recebe comandos de payment e roteia para os use cases.
 */
@Component
public class PaymentCommandConsumer {

    private static final Logger log = Logger.getLogger(PaymentCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final AuthorizePaymentUseCase authorizePaymentUseCase;
    private final CapturePaymentUseCase capturePaymentUseCase;

    public PaymentCommandConsumer(ObjectMapper objectMapper,
                                  AuthorizePaymentUseCase authorizePaymentUseCase,
                                  CapturePaymentUseCase capturePaymentUseCase) {
        this.objectMapper = objectMapper;
        this.authorizePaymentUseCase = authorizePaymentUseCase;
        this.capturePaymentUseCase = capturePaymentUseCase;
    }

    @KafkaListener(
            topics = TopicNames.PAYMENT_COMMANDS_V1,
            groupId = "${spring.kafka.consumer.group-id:ms-payment}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String commandType = header(record, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> header(record, HeaderNames.EVENT_TYPE).orElse(""));

        if (commandType == null || commandType.isBlank()) {
            log.warning("PaymentCommandConsumer: missing command type header. Ignoring message.");
            return;
        }

        SagaContext sagaContext = buildSagaContext(record);

        try {
            switch (commandType) {
                case "payment.authorize" -> handleAuthorize(record.value(), sagaContext);
                case "payment.capture" -> handleCapture(record.value(), sagaContext);
                default -> log.warning("PaymentCommandConsumer: unknown commandType=" + commandType);
            }
        } catch (Exception e) {
            log.severe("PaymentCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleAuthorize(String payload, SagaContext sagaContext) throws Exception {
        PaymentAuthorizeCommandV1 dto = objectMapper.readValue(payload, PaymentAuthorizeCommandV1.class);
        AuthorizePaymentCommand cmd = new AuthorizePaymentCommand(
                dto.commandId(),
                dto.orderId(),
                dto.customerId(),
                dto.amount(),
                dto.currency(),
                dto.paymentMethod()
        );
        authorizePaymentUseCase.authorize(cmd, sagaContext);
    }

    private void handleCapture(String payload, SagaContext sagaContext) throws Exception {
        PaymentCaptureCommandV1 dto = objectMapper.readValue(payload, PaymentCaptureCommandV1.class);
        CapturePaymentCommand cmd = new CapturePaymentCommand(
                dto.commandId(),
                dto.orderId(),
                dto.paymentId()
        );
        capturePaymentUseCase.capture(cmd, sagaContext);
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
