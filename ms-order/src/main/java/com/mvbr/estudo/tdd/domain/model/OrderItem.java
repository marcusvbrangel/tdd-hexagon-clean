package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.util.Objects;

public final class OrderItem {

    private final String productId;
    private final int quantity;
    private final Money price;

    public OrderItem(String productId, int quantity, Money price) {
        if (productId == null || productId.isBlank()) {
            throw new InvalidOrderException("Product ID cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }
        if (price == null) {
            throw new InvalidOrderException("Price cannot be null");
        }
        // Money já impede valor negativo; aqui garantimos que não seja zero
        if (price.isZero()) {
            throw new InvalidOrderException("Price must be greater than zero");
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
        if (!(o instanceof OrderItem other)) return false;
        return quantity == other.quantity
                && productId.equals(other.productId)
                && price.equals(other.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, price);
    }

    @Override
    public String toString() {
        return "OrderItem{productId='" + productId + "', quantity=" + quantity + ", price=" + price + "}";
    }
}






