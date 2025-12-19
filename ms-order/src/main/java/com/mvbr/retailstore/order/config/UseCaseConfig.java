package com.mvbr.retailstore.order.config;

import com.mvbr.retailstore.order.application.port.out.EventPublisher;
import com.mvbr.retailstore.order.application.port.out.OrderIdGenerator;
import com.mvbr.retailstore.order.application.port.out.OrderRepository;
import com.mvbr.retailstore.order.application.query.ListOrderSummariesQuery;
import com.mvbr.retailstore.order.application.query.GetOrderItemQuery;
import com.mvbr.retailstore.order.application.query.GetOrderQuery;
import com.mvbr.retailstore.order.application.query.ListOrdersQuery;
import com.mvbr.retailstore.order.application.query.OrderReadRepository;
import com.mvbr.retailstore.order.application.service.OrderCommandService;
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
