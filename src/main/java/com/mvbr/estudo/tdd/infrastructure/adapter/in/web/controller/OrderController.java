package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.controller;

import com.mvbr.estudo.tdd.application.usecase.CreateOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.GetOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ListOrdersUseCase;
import com.mvbr.estudo.tdd.application.service.PlaceOrderService;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderCreatedResponse;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final OrderWebMapper mapper;
    private final PlaceOrderService placeOrderService;
    private final com.mvbr.estudo.tdd.application.usecase.ConfirmOrderUseCase confirmOrderUseCase;
    private final com.mvbr.estudo.tdd.application.usecase.CancelOrderUseCase cancelOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           ListOrdersUseCase listOrdersUseCase,
                           GetOrderUseCase getOrderUseCase,
                           OrderWebMapper orderWebMapper,
                           PlaceOrderService placeOrderService,
                           com.mvbr.estudo.tdd.application.usecase.ConfirmOrderUseCase confirmOrderUseCase,
                           com.mvbr.estudo.tdd.application.usecase.CancelOrderUseCase cancelOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.mapper = orderWebMapper;
        this.placeOrderService = placeOrderService;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateOrderRequest request) {
        String orderId = createOrderUseCase.execute(mapper.toCommand(request));
        return ResponseEntity
                .created(URI.create("/orders/" + orderId))
                .body(new OrderCreatedResponse(orderId));
    }

    @PostMapping("/place")
    public ResponseEntity<?> place(@Valid @RequestBody CreateOrderRequest request) {
        String orderId = placeOrderService.placeOrder(mapper.toPlaceOrderCommand(request));
        return ResponseEntity
                .created(URI.create("/orders/" + orderId))
                .body(new OrderCreatedResponse(orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable String orderId) {
        var order = confirmOrderUseCase.execute(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String orderId) {
        var order = cancelOrderUseCase.execute(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        var orders = listOrdersUseCase.execute()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getById(@PathVariable String orderId) {
        return getOrderUseCase.execute(orderId)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}



