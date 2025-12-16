package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.util.Objects;

public class OrderItem {

    private final String productId;
    private final int quantity;
    private final Money price;

    public OrderItem(String productId, int quantity, Money price) {
        if (productId == null || productId.isBlank()) {
            throw new InvalidOrderException("Product ID cannot be blank");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getPrice() {
        return price;
    }

    public Money getSubTotal() {
        return price.multiply(quantity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem orderItem)) return false;
        return quantity == orderItem.quantity
                && Objects.equals(productId, orderItem.productId)
                && Objects.equals(price, orderItem.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, price);
    }
}






