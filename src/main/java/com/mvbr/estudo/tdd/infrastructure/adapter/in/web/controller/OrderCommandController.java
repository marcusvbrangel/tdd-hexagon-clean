package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.controller;

import com.mvbr.estudo.tdd.application.service.PlaceOrderService;
import com.mvbr.estudo.tdd.application.usecase.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.CreateOrderUseCase;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderCreatedResponse;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final PlaceOrderService placeOrderService;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderWebMapper mapper;

    public OrderCommandController(CreateOrderUseCase createOrderUseCase,
                                  PlaceOrderService placeOrderService,
                                  ConfirmOrderUseCase confirmOrderUseCase,
                                  CancelOrderUseCase cancelOrderUseCase,
                                  OrderWebMapper orderWebMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.placeOrderService = placeOrderService;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.mapper = orderWebMapper;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateOrderRequest request) {
        var orderId = createOrderUseCase.execute(mapper.toCommand(request));
        return ResponseEntity
                .created(URI.create("/orders/" + orderId.value()))
                .body(new OrderCreatedResponse(orderId.value()));
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
}
