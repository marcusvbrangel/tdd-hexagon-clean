package com.mvbr.estudo.tdd.application.service;

import org.springframework.transaction.annotation.Transactional;
import com.mvbr.estudo.tdd.application.usecase.CreateOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ReserveStockUseCase;
import com.mvbr.estudo.tdd.application.usecase.StartPaymentUseCase;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderCommand;

public class PlaceOrderService {

    private final CreateOrderUseCase createOrder;
    private final ReserveStockUseCase reserveStock;
    private final StartPaymentUseCase startPayment;

    public PlaceOrderService(CreateOrderUseCase createOrder,
                             ReserveStockUseCase reserveStock,
                             StartPaymentUseCase startPayment) {
        this.createOrder = createOrder;
        this.reserveStock = reserveStock;
        this.startPayment = startPayment;
    }

    @Transactional
    public String placeOrder(PlaceOrderCommand command) {

        String orderId = createOrder.execute(command.toCreateOrder());

        reserveStock.execute(orderId);

        startPayment.execute(orderId);

        return orderId;
    }
}
