package com.mvbr.retailstore.inventory.infrastructure.adapter.out.scheduling;

import com.mvbr.retailstore.inventory.application.service.ReservationExpirationService;
import com.mvbr.retailstore.inventory.config.InventoryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationExpirationJob {

    private final ReservationExpirationService service;
    private final InventoryProperties properties;

    public ReservationExpirationJob(ReservationExpirationService service, InventoryProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${inventory.expiration.scanFixedDelayMs:5000}")
    @Transactional
    public void tick() {
        service.expireDue(properties.getExpiration().getBatchSize());
    }
}
