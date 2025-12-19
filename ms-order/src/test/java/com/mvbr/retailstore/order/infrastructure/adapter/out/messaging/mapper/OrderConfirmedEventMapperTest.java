package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderConfirmedEventV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConfirmedEventMapperTest {

    @Test
    @DisplayName("Should map domain OrderConfirmedEvent into flat DTO v1")
    void mapsFlatDto() {
        Instant occurredAt = Instant.parse("2024-01-02T10:15:30Z");
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-456",
                occurredAt,
                new OrderId("ord-456"),
                new CustomerId("cust-999")
        );

        OrderConfirmedEventV1 dto = OrderConfirmedEventMapper.toDto(event);

        assertThat(dto.eventId()).isEqualTo("evt-456");
        assertThat(dto.occurredAt()).isEqualTo("2024-01-02T10:15:30Z");
        assertThat(dto.orderId()).isEqualTo("ord-456");
        assertThat(dto.customerId()).isEqualTo("cust-999");
    }
}
