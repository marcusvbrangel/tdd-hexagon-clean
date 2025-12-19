package com.mvbr.retailstore.order.infrastructure.adapter.in.web.mapper;

import com.mvbr.retailstore.order.application.command.PlaceOrderCommand;
import com.mvbr.retailstore.order.application.command.PlaceOrderItemCommand;
import com.mvbr.retailstore.order.application.query.OrderItemReadModel;
import com.mvbr.retailstore.order.application.query.OrderReadModel;
import com.mvbr.retailstore.order.application.query.OrderSummaryReadModel;
import com.mvbr.retailstore.order.domain.model.Order;
import com.mvbr.retailstore.order.domain.model.OrderItem;
import com.mvbr.retailstore.order.domain.model.OrderStatus;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.OrderResponse;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.OrderSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderWebMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getOrderId().value(),
                order.getCustomerId().value(),
                order.getStatus(),
                order.getDiscount().amount(),
                order.getTotal().amount(),
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
                Optional.of(request.discount())
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getQuantity(),
                item.getPrice().amount(),
                item.getSubTotal().amount()
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
