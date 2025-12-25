package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    Optional<Reservation> findByOrderId(String orderId);

    Reservation save(Reservation reservation);

    List<Reservation> findExpiredReserved(Instant now, int limit);
}
