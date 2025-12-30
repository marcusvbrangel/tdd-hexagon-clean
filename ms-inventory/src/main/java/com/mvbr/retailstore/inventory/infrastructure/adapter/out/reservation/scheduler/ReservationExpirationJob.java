package com.mvbr.retailstore.inventory.infrastructure.adapter.out.reservation.scheduler;

import com.mvbr.retailstore.inventory.application.service.ReservationExpirationService;
import com.mvbr.retailstore.inventory.config.InventoryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job agendado que dispara expiracao de reservas.
 */
@Component
public class ReservationExpirationJob {

    private final ReservationExpirationService service;
    private final InventoryProperties properties;

    public ReservationExpirationJob(ReservationExpirationService service, InventoryProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * Executa a varredura periodica de reservas vencidas.
     *
     * Observação:
     * - fixedDelay está tipado via InventoryProperties, evitando duplicidade de config (string + properties).
     */
    @Scheduled(fixedDelayString = "#{@inventoryProperties.expiration.scanFixedDelayMs}")
    @Transactional
    public void tick() {
        service.expireDue(properties.getExpiration().getBatchSize());
    }
}
