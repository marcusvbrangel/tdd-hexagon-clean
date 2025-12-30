package com.mvbr.retailstore.inventory.domain.model;

import java.util.Objects;

/**
 * Item de uma reserva, ligando produto e quantidade reservada.
 */
public record ReservationItem(ProductId productId, Quantity quantity) {
    public ReservationItem {
        java.util.Objects.requireNonNull(productId, "productId");
        java.util.Objects.requireNonNull(quantity, "quantity");
    }
}