package com.mvbr.retailstore.order.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.Money;
import com.mvbr.retailstore.order.domain.model.Order;
import com.mvbr.retailstore.order.domain.model.OrderId;
import com.mvbr.retailstore.order.domain.model.OrderItem;
import com.mvbr.retailstore.order.domain.model.OrderStatus;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderPersistenceMapper {

    // ============================
    // Domain → JPA Entity
    // ============================
    public JpaOrderEntity toEntity(Order order) {

        JpaOrderEntity entity = new JpaOrderEntity();

        entity.setOrderId(order.getOrderId().value());
        entity.setCustomerId(order.getCustomerId().value());
        entity.setStatus(order.getStatus());
        entity.setDiscount(order.getDiscount().amount());
        entity.setTotal(order.getTotal().amount());
        entity.setCurrency(order.getCurrency());
        entity.setCreatedAt(order.getCreatedAt());

        List<JpaOrderItemEntity> items = order.getItems()
                .stream()
                .map(this::toItemEntity)
                .collect(Collectors.toList());

        items.forEach(item -> item.setOrder(entity));
        entity.setItems(items);

        return entity;
    }

    private JpaOrderItemEntity toItemEntity(OrderItem item) {

        JpaOrderItemEntity entity = new JpaOrderItemEntity();

        entity.setProductId(item.getProductId());
        entity.setQuantity(item.getQuantity());
        entity.setPrice(item.getPrice().amount());

        return entity;
    }

    // ============================
    // JPA Entity → Domain
    // ============================
    public Order toDomain(JpaOrderEntity entity) {

        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(
                        i.getProductId(),
                        i.getQuantity(),
                        new Money(i.getPrice())
                ))
                .toList();

        Money discount = entity.getDiscount() == null
                ? Money.zero()
                : new Money(entity.getDiscount());

        return Order.restore(
                new OrderId(entity.getOrderId()),
                new CustomerId(entity.getCustomerId()),
                entity.getStatus(),
                items,
                discount,
                entity.getCurrency(),
                entity.getCreatedAt()
        );
    }



    private void restoreStatus(Order order, OrderStatus status) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        order.restoreStatusFromPersistence(status);
    }


}
