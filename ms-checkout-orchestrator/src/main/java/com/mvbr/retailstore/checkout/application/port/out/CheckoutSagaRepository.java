package com.mvbr.retailstore.checkout.application.port.out;

import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CheckoutSagaRepository {
    void save(CheckoutSaga saga);

    Optional<CheckoutSaga> findByOrderId(String orderId);

    List<CheckoutSaga> findTimedOut(Instant now);

    default CheckoutSaga getByOrderId(String orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found orderId=" + orderId));
    }
}
