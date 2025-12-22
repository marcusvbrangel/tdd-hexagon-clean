package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaCheckoutSagaRepositoryAdapter implements CheckoutSagaRepository {

    private final JpaCheckoutSagaSpringDataRepository repo;

    public JpaCheckoutSagaRepositoryAdapter(JpaCheckoutSagaSpringDataRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(CheckoutSaga saga) {
        CheckoutSagaJpaEntity entity = repo.findById(saga.getOrderId())
                .orElseGet(CheckoutSagaJpaEntity::new);

        entity.setOrderId(saga.getOrderId());
        entity.setSagaId(saga.getSagaId());
        entity.setCorrelationId(saga.getCorrelationId());
        entity.setStatus(saga.getStatus().name());
        entity.setStep(saga.getStep().name());
        entity.setCustomerId(saga.getCustomerId());
        entity.setAmount(saga.getAmount());
        entity.setCurrency(saga.getCurrency());
        entity.setOrderCompleted(saga.isOrderCompleted());
        entity.setInventoryReleased(saga.isInventoryReleased());
        entity.setOrderCanceled(saga.isOrderCanceled());
        entity.setUpdatedAt(Instant.now());

        repo.save(entity);
    }

    @Override
    public Optional<CheckoutSaga> findByOrderId(String orderId) {
        return repo.findById(orderId).map(entity ->
                CheckoutSaga.restore(
                        entity.getOrderId(),
                        entity.getSagaId(),
                        entity.getCorrelationId(),
                        SagaStatus.valueOf(entity.getStatus()),
                        SagaStep.valueOf(entity.getStep()),
                        entity.getCustomerId(),
                        entity.getAmount(),
                        entity.getCurrency(),
                        entity.isOrderCompleted(),
                        entity.isInventoryReleased(),
                        entity.isOrderCanceled()
                )
        );
    }
}
