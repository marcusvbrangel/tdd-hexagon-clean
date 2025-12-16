package com.mvbr.estudo.tdd.infrastructure.adapter.out.persistence;

import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.OrderItem;
import com.mvbr.estudo.tdd.domain.model.OrderStatus;

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
        entity.setDiscount(order.getDiscount().toBigDecimal());
        entity.setTotal(order.getTotal().toBigDecimal());

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
        entity.setPrice(item.getPrice().toBigDecimal());

        return entity;
    }

    // ============================
    // JPA Entity → Domain
    // ============================
    public Order toDomain(JpaOrderEntity entity) {

        Order order = new Order(
                new OrderId(entity.getOrderId()),
                new CustomerId(entity.getCustomerId())
        );

        // Reconstrói itens via comportamento do domínio
        entity.getItems().forEach(item ->
                order.addItem(
                        item.getProductId(),
                        item.getQuantity(),
                        new Money(item.getPrice())
                )
        );

        // Reconstrói desconto (ordem importa!)
        if (entity.getDiscount() != null) {
            order.applyDiscount(new Money(entity.getDiscount()));
        }

        // Reconstrói status (transições controladas)
        restoreStatus(order, entity.getStatus());

        return order;
    }

    private void restoreStatus(Order order, OrderStatus status) {

        switch (status) {
            case CONFIRMED -> order.confirm();
            case COMPLETED -> {
                order.confirm();
                order.complete();
            }
            case CANCELLED -> order.cancel();
            case DRAFT -> {
                // nada a fazer
            }
        }
    }
}
