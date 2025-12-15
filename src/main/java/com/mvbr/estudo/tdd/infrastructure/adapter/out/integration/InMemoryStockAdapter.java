package com.mvbr.estudo.tdd.infrastructure.adapter.out.integration;

import com.mvbr.estudo.tdd.application.port.out.StockGateway;
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
    public void reserve(String orderId, List<OrderItem> items) {
        reservedOrders.add(orderId);
        reservedItems.put(orderId, items);
        log.info("Estoque reservado (in-memory) para pedido {} com {} itens", orderId, items.size());
    }

    public boolean isReserved(String orderId) {
        return reservedOrders.contains(orderId);
    }

    public List<OrderItem> getReservedItems(String orderId) {
        return reservedItems.get(orderId);
    }
}
