package com.mvbr.retailstore.inventory.domain.model;

/**
 * Estados possiveis de uma reserva de estoque.
 */
public enum ReservationStatus {
    PENDING, RESERVED, COMMITTED, REJECTED, RELEASED, EXPIRED
}