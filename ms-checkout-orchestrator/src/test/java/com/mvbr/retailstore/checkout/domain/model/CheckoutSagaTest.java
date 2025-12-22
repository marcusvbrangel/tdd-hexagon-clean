package com.mvbr.retailstore.checkout.domain.model;

import com.mvbr.retailstore.checkout.domain.exception.SagaDomainException;
import org.junit.jupiter.api.Test;

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
    void orderPlaced_moves_to_inventory_reserve_and_sets_fields() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced("cust-1", "17.50", "USD");

        assertThat(saga.getStep()).isEqualTo(SagaStep.INVENTORY_RESERVE_PENDING);
        assertThat(saga.getCustomerId()).isEqualTo("cust-1");
        assertThat(saga.getAmount()).isEqualTo("17.50");
        assertThat(saga.getCurrency()).isEqualTo("USD");
    }

    @Test
    void orderPlaced_ignored_when_not_started() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced("cust-1", "10.00", "BRL");
        saga.onOrderPlaced("cust-2", "99.00", "USD");

        assertThat(saga.getStep()).isEqualTo(SagaStep.INVENTORY_RESERVE_PENDING);
        assertThat(saga.getCustomerId()).isEqualTo("cust-1");
        assertThat(saga.getAmount()).isEqualTo("10.00");
        assertThat(saga.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void inventoryReserved_requires_expected_step() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        assertThatThrownBy(saga::onInventoryReserved)
                .isInstanceOf(SagaDomainException.class)
                .hasMessageContaining("Invalid step transition");
    }

    @Test
    void happyPath_completes() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced("cust-1", "10.00", "BRL");
        saga.onInventoryReserved();
        saga.onPaymentAuthorized();
        saga.markOrderCompleted();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
        assertThat(saga.isOrderCompleted()).isTrue();
    }

    @Test
    void paymentDeclined_compensates_and_completes_when_release_and_cancel_arrive() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced("cust-1", "10.00", "BRL");
        saga.onInventoryReserved();
        saga.onPaymentDeclined();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(saga.getStep()).isEqualTo(SagaStep.COMPENSATE_INVENTORY_RELEASE_PENDING);

        saga.markOrderCanceled();
        assertThat(saga.getStep()).isEqualTo(SagaStep.WAITING_COMPENSATIONS);

        saga.markInventoryReleased();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELLED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void inventoryRejected_only_needs_order_cancel_to_finish() {
        CheckoutSaga saga = CheckoutSaga.start("order-1", "corr-1");

        saga.onOrderPlaced("cust-1", "10.00", "BRL");
        saga.onInventoryRejected();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(saga.getStep()).isEqualTo(SagaStep.COMPENSATE_ORDER_CANCEL_PENDING);

        saga.markOrderCanceled();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELLED);
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }
}
