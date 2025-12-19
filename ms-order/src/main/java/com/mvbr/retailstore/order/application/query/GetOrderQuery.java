package com.mvbr.retailstore.order.application.query;

import java.util.Optional;

public class GetOrderQuery {

    private final OrderReadRepository orderReadRepository;

    public GetOrderQuery(OrderReadRepository orderReadRepository) {
        this.orderReadRepository = orderReadRepository;
    }

    public Optional<OrderReadModel> execute(String orderId) {
        return orderReadRepository.findById(orderId);
    }
}
