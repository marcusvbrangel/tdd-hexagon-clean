package com.mvbr.estudo.tdd.config;

import com.mvbr.estudo.tdd.application.port.in.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderUseCase;
import com.mvbr.estudo.tdd.application.port.out.EventPublisher;
import com.mvbr.estudo.tdd.application.port.out.OrderIdGenerator;
import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.application.query.ListOrderSummariesQuery;
import com.mvbr.estudo.tdd.application.query.GetOrderItemQuery;
import com.mvbr.estudo.tdd.application.query.GetOrderQuery;
import com.mvbr.estudo.tdd.application.query.ListOrdersQuery;
import com.mvbr.estudo.tdd.application.query.OrderReadRepository;
import com.mvbr.estudo.tdd.application.service.OrderCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

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
    public OrderCommandService orderCommandService(
            OrderRepository orderRepository,
            OrderIdGenerator orderIdGenerator,
            EventPublisher eventPublisher
    ) {
        return new OrderCommandService(orderRepository, orderIdGenerator, eventPublisher);
    }

}
