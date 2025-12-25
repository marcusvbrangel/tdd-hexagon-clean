package com.mvbr.retailstore.inventory.domain.model;

/**
 * Item de uma reserva, ligando produto e quantidade reservada.
 */
public record ReservationItem(ProductId productId, Quantity quantity) {
}
