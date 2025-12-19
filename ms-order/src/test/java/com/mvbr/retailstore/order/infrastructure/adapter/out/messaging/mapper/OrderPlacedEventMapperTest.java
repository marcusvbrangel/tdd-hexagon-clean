package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;
import com.mvbr.retailstore.order.domain.model.ProductId;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacedEventMapperTest {

    @Test
    @DisplayName("Should map domain OrderPlacedEvent into flat DTO v1")
    void mapsFlatDto() {
        Instant occurredAt = Instant.parse("2024-01-02T10:15:30Z");
        OrderPlacedEvent event = new OrderPlacedEvent(
                "evt-123",
                occurredAt,
                new OrderId("ord-123"),
                new CustomerId("cust-456"),
                List.of(new ProductId("prod-1"), new ProductId("prod-2"))
        );

        OrderPlacedEventV1 dto = OrderPlacedEventMapper.toDto(event);

        assertThat(dto.eventId()).isEqualTo("evt-123");
        assertThat(dto.occurredAt()).isEqualTo("2024-01-02T10:15:30Z");
        assertThat(dto.orderId()).isEqualTo("ord-123");
        assertThat(dto.customerId()).isEqualTo("cust-456");
        assertThat(dto.productIds()).containsExactly("prod-1", "prod-2");
    }
}
