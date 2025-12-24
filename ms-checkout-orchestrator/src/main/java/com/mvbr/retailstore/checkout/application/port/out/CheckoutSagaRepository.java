package com.mvbr.retailstore.checkout.application.port.out;

import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saida para persistencia da saga.
 * Implementada pelo adapter JPA.
 */
public interface CheckoutSagaRepository {
    /**
     * Persiste a saga (create/update) apos cada transicao.
     */
    void save(CheckoutSaga saga);

    /**
     * Busca saga pelo orderId para processar eventos.
     */
    Optional<CheckoutSaga> findByOrderId(String orderId);

    /**
     * Busca sagas vencidas para o scheduler de timeout.
     */
    List<CheckoutSaga> findTimedOut(Instant now);

    /**
     * Versao conveniente que falha quando nao encontra a saga.
     */
    default CheckoutSaga getByOrderId(String orderId) {
        return findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found orderId=" + orderId));
    }
}
