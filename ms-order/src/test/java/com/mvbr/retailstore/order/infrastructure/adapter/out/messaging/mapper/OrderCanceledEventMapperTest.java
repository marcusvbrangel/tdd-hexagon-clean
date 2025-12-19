package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCanceledEventMapperTest {

    @Test
    @DisplayName("Should map domain OrderCanceledEvent into flat DTO v1")
    void mapsFlatDto() {
        Instant occurredAt = Instant.parse("2024-01-02T10:15:30Z");
        OrderCanceledEvent event = new OrderCanceledEvent(
                "evt-789",
                occurredAt,
                new OrderId("ord-789"),
                new CustomerId("cust-abc")
        );

        OrderCanceledEventV1 dto = OrderCanceledEventMapper.toDto(event);

        assertThat(dto.eventId()).isEqualTo("evt-789");
        assertThat(dto.occurredAt()).isEqualTo("2024-01-02T10:15:30Z");
        assertThat(dto.orderId()).isEqualTo("ord-789");
        assertThat(dto.customerId()).isEqualTo("cust-abc");
    }
}
