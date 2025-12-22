package com.mvbr.retailstore.checkout.application.port.out;

import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;

import java.util.Optional;

public interface CheckoutSagaRepository {
    void save(CheckoutSaga saga);

    Optional<CheckoutSaga> findByOrderId(String orderId);

    default CheckoutSaga getByOrderId(String orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found orderId=" + orderId));
    }
}
