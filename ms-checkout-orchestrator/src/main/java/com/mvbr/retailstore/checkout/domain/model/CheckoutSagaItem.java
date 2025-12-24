package com.mvbr.retailstore.checkout.domain.model;

/**
 * Item imutavel usado pela saga para representar um produto e sua quantidade.
 * Criado quando o evento order.placed e processado pelo CheckoutSagaEngine.
 */
public record CheckoutSagaItem(
        String productId,
        int quantity
) {}
