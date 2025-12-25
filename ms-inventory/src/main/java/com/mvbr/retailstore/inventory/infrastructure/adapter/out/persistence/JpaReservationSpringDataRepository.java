package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JpaReservationSpringDataRepository extends JpaRepository<JpaReservationEntity, String> {

    Optional<JpaReservationEntity> findByOrderId(String orderId);

    @Query("""
           select r from JpaReservationEntity r
           where r.status = 'RESERVED'
             and r.expiresAt <= :now
           order by r.expiresAt asc
           """)
    List<JpaReservationEntity> findExpiredReserved(Instant now);
}
