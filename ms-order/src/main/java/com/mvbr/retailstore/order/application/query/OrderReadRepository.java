package com.mvbr.retailstore.order.application.query;

import java.util.List;
import java.util.Optional;

public interface OrderReadRepository {

    Optional<OrderReadModel> findById(String orderId);

    List<OrderReadModel> findAll(OrderQueryFilters filters, int page, int size);

    List<OrderSummaryReadModel> findAllSummaries(OrderQueryFilters filters, int page, int size);

    Optional<OrderItemReadModel> findItemById(String orderId, Long itemId);
}
