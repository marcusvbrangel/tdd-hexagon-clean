package com.mvbr.estudo.tdd.infrastructure.adapter.out.query;

import com.mvbr.estudo.tdd.application.query.OrderItemReadModel;
import com.mvbr.estudo.tdd.application.query.OrderReadModel;
import com.mvbr.estudo.tdd.application.query.OrderReadRepository;
import com.mvbr.estudo.tdd.application.query.OrderQueryFilters;
import com.mvbr.estudo.tdd.application.query.OrderSummaryReadModel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcOrderReadRepository implements OrderReadRepository {

    private static final RowMapper<OrderRow> ORDER_ROW_MAPPER = JdbcOrderReadRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOrderReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OrderReadModel> findById(String orderId) {
        String sql = """
                SELECT o.order_id,
                       o.customer_id,
                       o.status,
                       o.discount,
                       o.total,
                       i.id AS item_id,
                       i.product_id,
                       i.quantity,
                       i.price
                FROM orders o
                LEFT JOIN order_items i ON i.order_id = o.order_id
                WHERE o.order_id = :orderId
                """;

        List<OrderRow> rows = jdbcTemplate.query(sql, Map.of("orderId", orderId), ORDER_ROW_MAPPER);
        return toOrders(rows).stream().findFirst();
    }

    @Override
    public List<OrderReadModel> findAll(OrderQueryFilters filters, int page, int size) {
        String sql = """
                SELECT o.order_id,
                       o.customer_id,
                       o.status,
                       o.discount,
                       o.total,
                       i.id AS item_id,
                       i.product_id,
                       i.quantity,
                       i.price
                FROM orders o
                LEFT JOIN order_items i ON i.order_id = o.order_id
                WHERE 1=1
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        sql = applyFilters(sql, params, filters);
        sql = sql + " ORDER BY o.order_id, i.id LIMIT :limit OFFSET :offset";
        params.put("limit", size);
        params.put("offset", page * size);

        return toOrders(jdbcTemplate.query(sql, params, ORDER_ROW_MAPPER));
    }

    @Override
    public List<OrderSummaryReadModel> findAllSummaries(OrderQueryFilters filters, int page, int size) {
        String sql = """
                SELECT o.order_id,
                       o.customer_id,
                       o.status,
                       o.discount,
                       o.total
                FROM orders o
                WHERE 1=1
                """;
        Map<String, Object> params = new LinkedHashMap<>();
        sql = applyFilters(sql, params, filters);
        sql = sql + " ORDER BY o.order_id LIMIT :limit OFFSET :offset";
        params.put("limit", size);
        params.put("offset", page * size);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new OrderSummaryReadModel(
                rs.getString("order_id"),
                rs.getString("customer_id"),
                rs.getString("status"),
                rs.getBigDecimal("discount"),
                rs.getBigDecimal("total")
        ));
    }

    @Override
    public Optional<OrderItemReadModel> findItemById(String orderId, Long itemId) {
        String sql = """
                SELECT i.id,
                       i.product_id,
                       i.quantity,
                       i.price
                FROM order_items i
                WHERE i.order_id = :orderId AND i.id = :itemId
                """;
        Map<String, Object> params = Map.of(
                "orderId", orderId,
                "itemId", itemId
        );
        return jdbcTemplate.query(sql, params, rs -> rs.next()
                ? Optional.of(mapItem(rs))
                : Optional.empty());
    }

    private static OrderRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long itemId = rs.getObject("item_id", Long.class);
        return new OrderRow(
                rs.getString("order_id"),
                rs.getString("customer_id"),
                rs.getString("status"),
                rs.getBigDecimal("discount"),
                rs.getBigDecimal("total"),
                itemId,
                rs.getString("product_id"),
                rs.getObject("quantity", Integer.class),
                rs.getBigDecimal("price")
        );
    }

    private static List<OrderReadModel> toOrders(List<OrderRow> rows) {
        Map<String, OrderAccumulator> orders = new LinkedHashMap<>();
        for (OrderRow row : rows) {
            orders.computeIfAbsent(row.orderId(), key -> new OrderAccumulator(row))
                    .addItem(row);
        }
        return orders.values()
                .stream()
                .map(OrderAccumulator::build)
                .toList();
    }

    private record OrderRow(
            String orderId,
            String customerId,
            String status,
            BigDecimal discount,
            BigDecimal total,
            Long itemId,
            String productId,
            Integer quantity,
            BigDecimal price
    ) { }

    private static class OrderAccumulator {
        private final String orderId;
        private final String customerId;
        private final String status;
        private final BigDecimal discount;
        private final BigDecimal total;
        private final List<OrderItemReadModel> items = new ArrayList<>();

        OrderAccumulator(OrderRow row) {
            this.orderId = row.orderId();
            this.customerId = row.customerId();
            this.status = row.status();
            this.discount = row.discount();
            this.total = row.total();
        }

        void addItem(OrderRow row) {
            if (row.itemId() == null) {
                return;
            }
            BigDecimal subTotal = row.price().multiply(BigDecimal.valueOf(row.quantity()));
            items.add(new OrderItemReadModel(
                    row.productId(),
                    row.quantity(),
                    row.price(),
                    subTotal
            ));
        }

        OrderReadModel build() {
            return new OrderReadModel(
                    orderId,
                    customerId,
                    status,
                    discount,
                    total,
                    List.copyOf(items)
            );
        }
    }

    private static OrderItemReadModel mapItem(ResultSet rs) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        Integer quantity = rs.getObject("quantity", Integer.class);
        return new OrderItemReadModel(
                rs.getString("product_id"),
                quantity,
                price,
                price.multiply(BigDecimal.valueOf(quantity))
        );
    }

    private static String applyFilters(String baseSql, Map<String, Object> params, OrderQueryFilters filters) {
        StringBuilder sql = new StringBuilder(baseSql);
        filters.status().ifPresent(status -> {
            sql.append(" AND o.status = :status");
            params.put("status", status);
        });
        filters.customerId().ifPresent(customerId -> {
            sql.append(" AND o.customer_id = :customerId");
            params.put("customerId", customerId);
        });
        filters.minTotal().ifPresent(minTotal -> {
            sql.append(" AND o.total >= :minTotal");
            params.put("minTotal", minTotal);
        });
        filters.maxTotal().ifPresent(maxTotal -> {
            sql.append(" AND o.total <= :maxTotal");
            params.put("maxTotal", maxTotal);
        });
        return sql.toString();
    }
}
