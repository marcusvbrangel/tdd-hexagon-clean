package com.mvbr.estudo.tdd.application.query;

import java.util.List;
import java.util.Optional;

public class ListOrdersQuery {

    private final OrderReadRepository orderReadRepository;

    public ListOrdersQuery(OrderReadRepository orderReadRepository) {
        this.orderReadRepository = orderReadRepository;
    }

    public List<OrderReadModel> execute(Optional<String> status,
                                        Optional<String> customerId,
                                        Optional<Integer> page,
                                        Optional<Integer> size) {
        OrderQueryFilters filters = new OrderQueryFilters(
                status,
                customerId,
                Optional.empty(),
                Optional.empty()
        );
        int pageNumber = page.orElse(0);
        int pageSize = size.orElse(20);
        return orderReadRepository.findAll(filters, pageNumber, pageSize);
    }
}
