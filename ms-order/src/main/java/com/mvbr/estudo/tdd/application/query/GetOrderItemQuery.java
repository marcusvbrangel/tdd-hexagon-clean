package com.mvbr.estudo.tdd.application.query;

import java.util.Optional;

public class GetOrderItemQuery {

    private final OrderReadRepository orderReadRepository;

    public GetOrderItemQuery(OrderReadRepository orderReadRepository) {
        this.orderReadRepository = orderReadRepository;
    }

    public Optional<OrderItemReadModel> execute(String orderId, Long itemId) {
        return orderReadRepository.findItemById(orderId, itemId);
    }
}
