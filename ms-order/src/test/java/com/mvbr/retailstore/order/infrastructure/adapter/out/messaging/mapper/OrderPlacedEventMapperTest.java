package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;
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
                List.of(
                        new OrderPlacedEvent.Item("prod-1", 2, "10.00"),
                        new OrderPlacedEvent.Item("prod-2", 1, "5.00")
                ),
                "25.00",
                "BRL"
        );

        OrderPlacedEventV1 dto = OrderPlacedEventMapper.toDto(event);

        assertThat(dto.eventId()).isEqualTo("evt-123");
        assertThat(dto.occurredAt()).isEqualTo("2024-01-02T10:15:30Z");
        assertThat(dto.orderId()).isEqualTo("ord-123");
        assertThat(dto.customerId()).isEqualTo("cust-456");
        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items().getFirst().productId()).isEqualTo("prod-1");
        assertThat(dto.items().getFirst().quantity()).isEqualTo(2);
        assertThat(dto.items().getFirst().unitPrice()).isEqualTo("10.00");
        assertThat(dto.items().get(1).productId()).isEqualTo("prod-2");
        assertThat(dto.items().get(1).quantity()).isEqualTo(1);
        assertThat(dto.items().get(1).unitPrice()).isEqualTo("5.00");
        assertThat(dto.total()).isEqualTo("25.00");
        assertThat(dto.currency()).isEqualTo("BRL");
    }
}
