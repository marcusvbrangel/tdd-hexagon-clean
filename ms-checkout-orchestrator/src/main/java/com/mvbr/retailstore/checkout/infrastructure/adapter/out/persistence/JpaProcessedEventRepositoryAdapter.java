package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.checkout.application.port.out.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
/**
 * Adapter JPA para idempotencia: registra eventos ja processados.
 */
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventSpringDataRepository repo;

    public JpaProcessedEventRepositoryAdapter(JpaProcessedEventSpringDataRepository repo) {
        this.repo = repo;
    }

    /**
     * Tenta inserir o evento; se ja existir, retorna false.
     * Chamado pelo CheckoutSagaEngine antes de processar cada evento.
     */
    @Override
    public boolean markProcessedIfFirst(String eventId, String eventType, String orderId) {
        try {
            repo.save(new ProcessedEventJpaEntity(eventId, eventType, orderId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
