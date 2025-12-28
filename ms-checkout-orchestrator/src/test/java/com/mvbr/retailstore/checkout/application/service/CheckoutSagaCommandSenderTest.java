package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSagaCommandSenderTest {

    @Test
    void sendPaymentAuthorize_uses_same_command_id_on_repeat() {
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        CheckoutSagaCommandSender sender = new CheckoutSagaCommandSender(publisher);
        CheckoutSaga saga = sagaWaitingPayment();

        sender.sendPaymentAuthorize(saga, "evt-1", SagaStep.WAIT_PAYMENT.name());
        sender.sendPaymentAuthorize(saga, "evt-1", SagaStep.WAIT_PAYMENT.name());

        assertThat(publisher.published()).hasSize(2);
        PaymentAuthorizeCommandV1 first = (PaymentAuthorizeCommandV1) publisher.published().get(0).payload();
        PaymentAuthorizeCommandV1 second = (PaymentAuthorizeCommandV1) publisher.published().get(1).payload();

        assertThat(first.commandId()).isEqualTo(second.commandId());
        assertThat(publisher.published().get(0).headers().get(HeaderNames.COMMAND_ID))
                .isEqualTo(first.commandId());
        assertThat(publisher.published().get(1).headers().get(HeaderNames.COMMAND_ID))
                .isEqualTo(second.commandId());
        String firstEventId = publisher.published().get(0).headers().get(HeaderNames.EVENT_ID);
        String secondEventId = publisher.published().get(1).headers().get(HeaderNames.EVENT_ID);
        assertThat(firstEventId).isNotBlank().isNotEqualTo(first.commandId());
        assertThat(secondEventId).isNotBlank().isNotEqualTo(second.commandId());
        assertThat(firstEventId).isNotEqualTo(secondEventId);
    }

    @Test
    void sendInventoryReserve_uses_same_command_id_on_repeat() {
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        CheckoutSagaCommandSender sender = new CheckoutSagaCommandSender(publisher);
        CheckoutSaga saga = sagaWaitingInventory();

        sender.sendInventoryReserve(saga, "evt-1", SagaStep.WAIT_INVENTORY.name());
        sender.sendInventoryReserve(saga, "evt-1", SagaStep.WAIT_INVENTORY.name());

        assertThat(publisher.published()).hasSize(2);
        InventoryReserveCommandV1 first = (InventoryReserveCommandV1) publisher.published().get(0).payload();
        InventoryReserveCommandV1 second = (InventoryReserveCommandV1) publisher.published().get(1).payload();

        assertThat(first.commandId()).isEqualTo(second.commandId());
        assertThat(publisher.published().get(0).headers().get(HeaderNames.COMMAND_ID))
                .isEqualTo(first.commandId());
        assertThat(publisher.published().get(1).headers().get(HeaderNames.COMMAND_ID))
                .isEqualTo(second.commandId());
        String firstEventId = publisher.published().get(0).headers().get(HeaderNames.EVENT_ID);
        String secondEventId = publisher.published().get(1).headers().get(HeaderNames.EVENT_ID);
        assertThat(firstEventId).isNotBlank().isNotEqualTo(first.commandId());
        assertThat(secondEventId).isNotBlank().isNotEqualTo(second.commandId());
        assertThat(firstEventId).isNotEqualTo(secondEventId);
    }

    @Test
    void sendPaymentCapture_uses_same_command_id_on_repeat() {
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        CheckoutSagaCommandSender sender = new CheckoutSagaCommandSender(publisher);
        CheckoutSaga saga = sagaWaitingPaymentCapture();

        sender.sendPaymentCapture(saga, "evt-1", SagaStep.WAIT_PAYMENT_CAPTURE.name());
        sender.sendPaymentCapture(saga, "evt-1", SagaStep.WAIT_PAYMENT_CAPTURE.name());

        assertThat(publisher.published()).hasSize(2);
        var first = (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCaptureCommandV1)
                publisher.published().get(0).payload();
        var second = (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentCaptureCommandV1)
                publisher.published().get(1).payload();

        assertThat(first.commandId()).isEqualTo(second.commandId());
        String firstEventId = publisher.published().get(0).headers().get(HeaderNames.EVENT_ID);
        String secondEventId = publisher.published().get(1).headers().get(HeaderNames.EVENT_ID);
        assertThat(firstEventId).isNotBlank().isNotEqualTo(first.commandId());
        assertThat(secondEventId).isNotBlank().isNotEqualTo(second.commandId());
        assertThat(firstEventId).isNotEqualTo(secondEventId);
    }

    @Test
    void sendInventoryCommit_uses_same_command_id_on_repeat() {
        CapturingCommandPublisher publisher = new CapturingCommandPublisher();
        CheckoutSagaCommandSender sender = new CheckoutSagaCommandSender(publisher);
        CheckoutSaga saga = sagaWaitingInventoryCommit();

        sender.sendInventoryCommit(saga, "evt-1", SagaStep.WAIT_INVENTORY_COMMIT.name());
        sender.sendInventoryCommit(saga, "evt-1", SagaStep.WAIT_INVENTORY_COMMIT.name());

        assertThat(publisher.published()).hasSize(2);
        var first = (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryCommitCommandV1)
                publisher.published().get(0).payload();
        var second = (com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryCommitCommandV1)
                publisher.published().get(1).payload();

        assertThat(first.commandId()).isEqualTo(second.commandId());
        String firstEventId = publisher.published().get(0).headers().get(HeaderNames.EVENT_ID);
        String secondEventId = publisher.published().get(1).headers().get(HeaderNames.EVENT_ID);
        assertThat(firstEventId).isNotBlank().isNotEqualTo(first.commandId());
        assertThat(secondEventId).isNotBlank().isNotEqualTo(second.commandId());
        assertThat(firstEventId).isNotEqualTo(secondEventId);
    }

    private CheckoutSaga sagaWaitingInventory() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");
        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                "card",
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );
        return saga;
    }

    private CheckoutSaga sagaWaitingPayment() {
        CheckoutSaga saga = sagaWaitingInventory();
        saga.onInventoryReserved(Instant.now().plusSeconds(30));
        return saga;
    }

    private CheckoutSaga sagaWaitingPaymentCapture() {
        CheckoutSaga saga = sagaWaitingPayment();
        saga.onPaymentAuthorized(Instant.now().plusSeconds(30));
        saga.onOrderCompleted(Instant.now().plusSeconds(30));
        return saga;
    }

    private CheckoutSaga sagaWaitingInventoryCommit() {
        CheckoutSaga saga = sagaWaitingPaymentCapture();
        saga.onPaymentCaptured(Instant.now().plusSeconds(30));
        return saga;
    }

    private static class CapturingCommandPublisher implements CommandPublisher {
        private final List<PublishedCommand> published = new ArrayList<>();

        @Override
        public void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers) {
            published.add(new PublishedCommand(topic, key, commandType, payload, headers));
        }

        List<PublishedCommand> published() {
            return published;
        }
    }

    private record PublishedCommand(
            String topic,
            String key,
            String commandType,
            Object payload,
            Map<String, String> headers
    ) {}
}
