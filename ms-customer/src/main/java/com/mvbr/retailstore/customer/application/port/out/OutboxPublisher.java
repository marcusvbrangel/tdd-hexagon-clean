package com.mvbr.retailstore.customer.application.port.out;

import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import java.util.List;

public interface OutboxPublisher {
    void store(List<DomainEvent> events);
}
