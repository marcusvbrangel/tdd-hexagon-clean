package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.mapper;

import com.mvbr.estudo.tdd.application.port.in.CreateOrderCommand;
import com.mvbr.estudo.tdd.application.port.in.CreateOrderItemCommand;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderCommand;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderItemCommand;
import com.mvbr.estudo.tdd.application.query.OrderItemReadModel;
import com.mvbr.estudo.tdd.application.query.OrderReadModel;
import com.mvbr.estudo.tdd.application.query.OrderSummaryReadModel;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderItem;
import com.mvbr.estudo.tdd.domain.model.OrderStatus;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.CreateOrderItemRequest;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderResponse;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.OrderSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderWebMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        List<CreateOrderItemCommand> items = request.items()
                .stream()
                .map(this::toItemCommand)
                .toList();

        return new CreateOrderCommand(
                request.customerId(),
                items,
                request.discount()
        );
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getDiscount(),
                order.getTotal(),
                items
        );
    }

    public OrderSummaryResponse toSummaryResponse(OrderSummaryReadModel order) {
        return new OrderSummaryResponse(
                order.orderId(),
                order.customerId(),
                OrderStatus.valueOf(order.status()),
                order.discount(),
                order.total()
        );
    }

    public OrderResponse toResponse(OrderReadModel order) {
        List<OrderItemResponse> items = order.items()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.orderId(),
                order.customerId(),
                OrderStatus.valueOf(order.status()),
                order.discount(),
                order.total(),
                items
        );
    }

    public PlaceOrderCommand toPlaceOrderCommand(CreateOrderRequest request) {
        List<PlaceOrderItemCommand> items = request.items()
                .stream()
                .map(item -> new PlaceOrderItemCommand(
                        item.productId(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        return new PlaceOrderCommand(
                request.customerId(),
                items,
                request.discount()
        );
    }

    private CreateOrderItemCommand toItemCommand(CreateOrderItemRequest item) {
        return new CreateOrderItemCommand(
                item.productId(),
                item.quantity(),
                item.price()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getQuantity(),
                item.getPrice(),
                item.getSubTotal()
        );
    }

    public OrderItemResponse toItemResponse(OrderItemReadModel item) {
        return new OrderItemResponse(
                item.productId(),
                item.quantity(),
                item.price(),
                item.subTotal()
        );
    }
}
