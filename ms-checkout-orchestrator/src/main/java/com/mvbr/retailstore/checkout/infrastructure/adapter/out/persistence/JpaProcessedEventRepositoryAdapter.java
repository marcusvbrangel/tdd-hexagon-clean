package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.checkout.application.port.out.ProcessedEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventSpringDataRepository repo;

    public JpaProcessedEventRepositoryAdapter(JpaProcessedEventSpringDataRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        return repo.existsById(eventId);
    }

    @Override
    public void markProcessed(String eventId, String eventType, String orderId) {
        repo.save(new ProcessedEventJpaEntity(eventId, eventType, orderId));
    }
}
