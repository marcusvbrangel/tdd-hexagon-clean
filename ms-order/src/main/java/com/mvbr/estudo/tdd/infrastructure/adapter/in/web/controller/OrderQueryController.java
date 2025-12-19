package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.controller;

import com.mvbr.estudo.tdd.application.query.GetOrderItemQuery;
import com.mvbr.estudo.tdd.application.query.GetOrderQuery;
import com.mvbr.estudo.tdd.application.query.ListOrderSummariesQuery;
import com.mvbr.estudo.tdd.application.query.ListOrdersQuery;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/orders")
public class OrderQueryController {

    private final ListOrderSummariesQuery listOrderSummariesQuery;
    private final ListOrdersQuery listOrdersQuery;
    private final GetOrderQuery getOrderQuery;
    private final GetOrderItemQuery getOrderItemQuery;
    private final OrderWebMapper mapper;

    public OrderQueryController(ListOrderSummariesQuery listOrderSummariesQuery,
                                ListOrdersQuery listOrdersQuery,
                                GetOrderQuery getOrderQuery,
                                GetOrderItemQuery getOrderItemQuery,
                                OrderWebMapper orderWebMapper) {
        this.listOrderSummariesQuery = listOrderSummariesQuery;
        this.listOrdersQuery = listOrdersQuery;
        this.getOrderQuery = getOrderQuery;
        this.getOrderItemQuery = getOrderItemQuery;
        this.mapper = orderWebMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String customerId,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        var orders = listOrderSummariesQuery.execute(
                        Optional.ofNullable(status),
                        Optional.ofNullable(customerId),
                        Optional.ofNullable(page),
                        Optional.ofNullable(size))
                .stream()
                .map(mapper::toSummaryResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/details")
    public ResponseEntity<?> listWithItems(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String customerId,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        var orders = listOrdersQuery.execute(
                        Optional.ofNullable(status),
                        Optional.ofNullable(customerId),
                        Optional.ofNullable(page),
                        Optional.ofNullable(size))
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getById(@PathVariable String orderId) {
        return getOrderQuery.execute(orderId)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<?> getItem(@PathVariable String orderId, @PathVariable Long itemId) {
        return getOrderItemQuery.execute(orderId, itemId)
                .map(mapper::toItemResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
