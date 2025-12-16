package com.mvbr.estudo.tdd.config;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.application.port.out.PaymentGateway;
import com.mvbr.estudo.tdd.application.port.out.StockGateway;
import com.mvbr.estudo.tdd.application.query.ListOrderSummariesQuery;
import com.mvbr.estudo.tdd.application.query.GetOrderItemQuery;
import com.mvbr.estudo.tdd.application.query.GetOrderQuery;
import com.mvbr.estudo.tdd.application.query.ListOrdersQuery;
import com.mvbr.estudo.tdd.application.query.OrderReadRepository;
import com.mvbr.estudo.tdd.application.service.PlaceOrderService;
import com.mvbr.estudo.tdd.application.usecase.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.CreateOrderUseCase;
import com.mvbr.estudo.tdd.application.usecase.ReserveStockService;
import com.mvbr.estudo.tdd.application.usecase.ReserveStockUseCase;
import com.mvbr.estudo.tdd.application.usecase.StartPaymentService;
import com.mvbr.estudo.tdd.application.usecase.StartPaymentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository) {
        return new CreateOrderUseCase(orderRepository);
    }

    @Bean
    public ListOrdersQuery listOrdersQuery(OrderReadRepository orderReadRepository) {
        return new ListOrdersQuery(orderReadRepository);
    }

    @Bean
    public GetOrderQuery getOrderQuery(OrderReadRepository orderReadRepository) {
        return new GetOrderQuery(orderReadRepository);
    }

    @Bean
    public GetOrderItemQuery getOrderItemQuery(OrderReadRepository orderReadRepository) {
        return new GetOrderItemQuery(orderReadRepository);
    }

    @Bean
    public ListOrderSummariesQuery listOrderSummariesQuery(OrderReadRepository orderReadRepository) {
        return new ListOrderSummariesQuery(orderReadRepository);
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
