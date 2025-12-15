package com.mvbr.estudo.tdd.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaOrderSpringDataRepository extends JpaRepository<JpaOrderEntity, String> {

    @Override
    @EntityGraph(attributePaths = "items")
    List<JpaOrderEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<JpaOrderEntity> findById(String orderId);

}
