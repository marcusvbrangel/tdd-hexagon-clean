package com.mvbr.retailstore.checkout.domain.model;

import com.mvbr.retailstore.checkout.domain.exception.SagaDomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutSagaTest {

    @Test
    void start_sets_defaults() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        assertThat(saga.getOrderId()).isEqualTo("order-1");
        assertThat(saga.getCorrelationId()).isEqualTo("corr-1");
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(saga.getStep()).isEqualTo(SagaStep.STARTED);
        assertThat(saga.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void orderPlaced_moves_to_wait_inventory_and_sets_fields() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced(
                "cust-1",
                "17.50",
                "USD",
                "pix",
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );

        assertThat(saga.getStep()).isEqualTo(SagaStep.WAIT_INVENTORY);
        assertThat(saga.getCustomerId()).isEqualTo("cust-1");
        assertThat(saga.getAmount()).isEqualTo("17.50");
        assertThat(saga.getCurrency()).isEqualTo("USD");
        assertThat(saga.getPaymentMethod()).isEqualTo("pix");
    }

    @Test
    void orderPlaced_ignored_when_not_started() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );
        saga.onOrderPlaced(
                "cust-2",
                "99.00",
                "USD",
                null,
                List.of(new CheckoutSagaItem("sku-2", 1)),
                Instant.now().plusSeconds(30)
        );

        assertThat(saga.getStep()).isEqualTo(SagaStep.WAIT_INVENTORY);
        assertThat(saga.getCustomerId()).isEqualTo("cust-1");
        assertThat(saga.getAmount()).isEqualTo("10.00");
        assertThat(saga.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void inventoryReserved_requires_expected_step() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        assertThatThrownBy(() -> saga.onInventoryReserved(Instant.now()))
                .isInstanceOf(SagaDomainException.class)
                .hasMessageContaining("Invalid step transition");
    }

    @Test
    void happyPath_completes() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );
        saga.onInventoryReserved(Instant.now().plusSeconds(120));
        saga.onPaymentAuthorized(Instant.now().plusSeconds(60));
        saga.onOrderCompleted(Instant.now().plusSeconds(60));
        saga.onPaymentCaptured(Instant.now().plusSeconds(30));
        saga.markInventoryCommitted();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        assertThat(saga.isOrderCompleted()).isTrue();
        assertThat(saga.isPaymentCaptured()).isTrue();
        assertThat(saga.isInventoryCommitted()).isTrue();
    }

    @Test
    void paymentDeclined_marks_canceled() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );
        saga.onInventoryReserved(Instant.now().plusSeconds(120));
        saga.onPaymentDeclined("PAYMENT_DECLINED");

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void inventoryRejected_marks_canceled() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced(
                "cust-1",
                "10.00",
                "BRL",
                null,
                List.of(new CheckoutSagaItem("sku-1", 1)),
                Instant.now().plusSeconds(30)
        );
        saga.onInventoryRejected("INVENTORY_REJECTED");

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void getOrCreate_commandId_is_stable_and_clear_resets() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        String first = saga.getOrCreatePaymentAuthorizeCommandId();
        String second = saga.getOrCreatePaymentAuthorizeCommandId();

        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);

        saga.clearPaymentAuthorizeCommandId();
        String third = saga.getOrCreatePaymentAuthorizeCommandId();

        assertThat(third).isNotBlank();
        assertThat(third).isNotEqualTo(first);
    }
}
