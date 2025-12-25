package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data para reservas.
 */
public interface JpaReservationSpringDataRepository extends JpaRepository<JpaReservationEntity, String> {

    /**
     * Busca a reserva pelo orderId (chave unica).
     */
    Optional<JpaReservationEntity> findByOrderId(String orderId);

    /**
     * Lista reservas vencidas e ainda ativas.
     */
    @Query("""
           select r from JpaReservationEntity r
           where r.status = 'RESERVED'
             and r.expiresAt <= :now
           order by r.expiresAt asc
           """)
    List<JpaReservationEntity> findExpiredReserved(Instant now);
}
