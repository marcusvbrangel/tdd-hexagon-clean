package com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence.repository;

import com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, String> {
}
