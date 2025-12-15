package com.mvbr.estudo.tdd.config;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.application.usecase.CreateOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.GetOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ListOrdersUseCase;
import com.mvbr.estudo.tdd.application.service.PlaceOrderService;
import com.mvbr.estudo.tdd.application.usecase.ReserveStockUseCase;
import com.mvbr.estudo.tdd.application.usecase.StartPaymentUseCase;
import com.mvbr.estudo.tdd.application.usecase.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ReserveStockService;
import com.mvbr.estudo.tdd.application.usecase.StartPaymentService;
import com.mvbr.estudo.tdd.application.port.out.StockGateway;
import com.mvbr.estudo.tdd.application.port.out.PaymentGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository) {
        return new CreateOrderUseCase(orderRepository);
    }

    @Bean
    public ListOrdersUseCase listOrdersUseCase(OrderRepository orderRepository) {
        return new ListOrdersUseCase(orderRepository);
    }

    @Bean
    public GetOrderUseCase getOrderUseCase(OrderRepository orderRepository) {
        return new GetOrderUseCase(orderRepository);
    }

    @Bean
    public PlaceOrderService placeOrderService(CreateOrderUseCase createOrderUseCase,
                                               ReserveStockUseCase reserveStockUseCase,
                                               StartPaymentUseCase startPaymentUseCase) {
        return new PlaceOrderService(createOrderUseCase, reserveStockUseCase, startPaymentUseCase);
    }

    @Bean
    public ConfirmOrderUseCase confirmOrderUseCase(OrderRepository orderRepository) {
        return new ConfirmOrderUseCase(orderRepository);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(OrderRepository orderRepository) {
        return new CancelOrderUseCase(orderRepository);
    }

    @Bean
    public ReserveStockUseCase reserveStockUseCase(OrderRepository orderRepository, StockGateway stockGateway) {
        return new ReserveStockService(orderRepository, stockGateway);
    }

    @Bean
    public StartPaymentUseCase startPaymentUseCase(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        return new StartPaymentService(orderRepository, paymentGateway);
    }

}
