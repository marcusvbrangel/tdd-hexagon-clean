package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaCheckoutSagaRepositoryAdapter implements CheckoutSagaRepository {

    private final JpaCheckoutSagaSpringDataRepository repo;
    private final ObjectMapper objectMapper;

    public JpaCheckoutSagaRepositoryAdapter(JpaCheckoutSagaSpringDataRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
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
        entity.setPaymentMethod(saga.getPaymentMethod());
        entity.setItemsJson(writeItems(saga.getItems()));
        entity.setOrderCompleted(saga.isOrderCompleted());
        entity.setInventoryReleased(saga.isInventoryReleased());
        entity.setOrderCanceled(saga.isOrderCanceled());
        entity.setDeadlineAt(saga.getDeadlineAt());
        entity.setAttemptsInventory(saga.getAttemptsInventory());
        entity.setAttemptsPayment(saga.getAttemptsPayment());
        entity.setAttemptsOrderCompletion(saga.getAttemptsOrderCompletion());
        entity.setLastError(saga.getLastError());
        entity.setLastEventId(saga.getLastEventId());
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
                        SagaStatus.fromPersistence(entity.getStatus()),
                        SagaStep.fromPersistence(entity.getStep()),
                        entity.getCustomerId(),
                        entity.getAmount(),
                        entity.getCurrency(),
                        entity.getPaymentMethod(),
                        readItems(entity.getItemsJson()),
                        entity.getDeadlineAt(),
                        entity.getAttemptsInventory(),
                        entity.getAttemptsPayment(),
                        entity.getAttemptsOrderCompletion(),
                        entity.getLastError(),
                        entity.getLastEventId(),
                        entity.isOrderCompleted(),
                        entity.isInventoryReleased(),
                        entity.isOrderCanceled()
                )
        );
    }

    @Override
    public List<CheckoutSaga> findTimedOut(Instant now) {
        List<String> steps = List.of(
                SagaStep.WAIT_INVENTORY.name(),
                SagaStep.WAIT_PAYMENT.name(),
                SagaStep.WAIT_ORDER_COMPLETION.name()
        );
        return repo.findTop100ByStatusAndStepInAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
                SagaStatus.RUNNING.name(),
                steps,
                now
        ).stream().map(entity ->
                CheckoutSaga.restore(
                        entity.getOrderId(),
                        entity.getSagaId(),
                        entity.getCorrelationId(),
                        SagaStatus.fromPersistence(entity.getStatus()),
                        SagaStep.fromPersistence(entity.getStep()),
                        entity.getCustomerId(),
                        entity.getAmount(),
                        entity.getCurrency(),
                        entity.getPaymentMethod(),
                        readItems(entity.getItemsJson()),
                        entity.getDeadlineAt(),
                        entity.getAttemptsInventory(),
                        entity.getAttemptsPayment(),
                        entity.getAttemptsOrderCompletion(),
                        entity.getLastError(),
                        entity.getLastEventId(),
                        entity.isOrderCompleted(),
                        entity.isInventoryReleased(),
                        entity.isOrderCanceled()
                )
        ).toList();
    }

    private String writeItems(List<CheckoutSagaItem> items) {
        try {
            return (items == null || items.isEmpty())
                    ? null
                    : objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize saga items", e);
        }
    }

    private List<CheckoutSagaItem> readItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize saga items", e);
        }
    }
}
