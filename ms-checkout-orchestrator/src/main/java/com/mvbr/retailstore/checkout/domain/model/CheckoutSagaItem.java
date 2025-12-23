package com.mvbr.retailstore.checkout.domain.model;

public record CheckoutSagaItem(
        String productId,
        int quantity
) {}
