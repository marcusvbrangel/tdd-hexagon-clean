package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.controller;

import com.mvbr.estudo.tdd.application.port.in.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderCreatedResponse;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderCommandController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderWebMapper mapper;

    public OrderCommandController(PlaceOrderUseCase placeOrderUseCase,
                                  ConfirmOrderUseCase confirmOrderUseCase,
                                  CancelOrderUseCase cancelOrderUseCase,
                                  OrderWebMapper mapper) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.mapper = mapper;
    }


    @PostMapping
    public ResponseEntity<?> place(@Valid @RequestBody CreateOrderRequest request) {
        var orderId = placeOrderUseCase.execute(mapper.toPlaceOrderCommand(request));
        return ResponseEntity
                .created(URI.create("/orders/" + orderId.value()))
                .body(new OrderCreatedResponse(orderId.value()));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable String orderId) {
        var order = confirmOrderUseCase.confirm(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String orderId) {
        var order = cancelOrderUseCase.cancel(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }
}
