package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia para reservas de estoque.
 */
public interface ReservationRepository {

    /**
     * Busca uma reserva pelo orderId (chave unica).
     */
    Optional<Reservation> findByOrderId(String orderId);

    /**
     * Persiste uma reserva (incluindo itens e status).
     */
    Reservation save(Reservation reservation);

    /**
     * Lista reservas vencidas e ainda ativas, limitando o lote.
     */
    List<Reservation> findExpiredReserved(Instant now, int limit);
}
