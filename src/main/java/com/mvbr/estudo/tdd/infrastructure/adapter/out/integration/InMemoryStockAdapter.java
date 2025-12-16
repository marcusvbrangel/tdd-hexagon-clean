package com.mvbr.estudo.tdd.infrastructure.adapter.out.integration;

import com.mvbr.estudo.tdd.application.port.out.StockGateway;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStockAdapter implements StockGateway {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStockAdapter.class);
    private final Set<String> reservedOrders = ConcurrentHashMap.newKeySet();
    private final Map<String, List<OrderItem>> reservedItems = new ConcurrentHashMap<>();

    @Override
    public void reserve(OrderId orderId, List<OrderItem> items) {
        reservedOrders.add(orderId.value());
        reservedItems.put(orderId.value(), items);
        log.info("Estoque reservado (in-memory) para pedido {} com {} itens", orderId.value(), items.size());
    }

    public boolean isReserved(String orderIdValue) {
        return reservedOrders.contains(orderIdValue);
    }

    public List<OrderItem> getReservedItems(String orderIdValue) {
        return reservedItems.get(orderIdValue);
    }
}
