package com.mvbr.estudo.tdd.application.query;

import java.util.List;
import java.util.Optional;

public class ListOrderSummariesQuery {

    private final OrderReadRepository orderReadRepository;

    public ListOrderSummariesQuery(OrderReadRepository orderReadRepository) {
        this.orderReadRepository = orderReadRepository;
    }

    public List<OrderSummaryReadModel> execute(Optional<String> status,
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
        return orderReadRepository.findAllSummaries(filters, pageNumber, pageSize);
    }
}
