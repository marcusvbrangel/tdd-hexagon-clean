package com.mvbr.estudo.tdd.infrastructure.adapter.out.persistence;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {

    private final JpaOrderSpringDataRepository repository;
    private final OrderPersistenceMapper mapper;

    public JpaOrderRepositoryAdapter(JpaOrderSpringDataRepository repository,
                                     OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        repository.save(mapper.toEntity(order));
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return repository.findById(orderId.value())
                .map(mapper::toDomain);
    }

}
