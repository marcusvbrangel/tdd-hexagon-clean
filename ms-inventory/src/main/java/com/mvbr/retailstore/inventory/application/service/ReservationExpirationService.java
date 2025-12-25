package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.domain.model.Reservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Varre reservas expiradas e aciona liberacao automatica.
 */
@Component
public class ReservationExpirationService {

    private final ReservationRepository reservationRepo;
    private final InventoryCommandService inventoryService;

    public ReservationExpirationService(ReservationRepository reservationRepo,
                                        InventoryCommandService inventoryService) {
        this.reservationRepo = reservationRepo;
        this.inventoryService = inventoryService;
    }

    /**
     * Processa um lote de reservas vencidas.
     */
    public void expireDue(int batchSize) {
        List<Reservation> expired = reservationRepo.findExpiredReserved(Instant.now(), batchSize);
        for (Reservation r : expired) {
            ReleaseInventoryCommand cmd = new ReleaseInventoryCommand(
                    UUID.randomUUID().toString(),
                    r.getOrderId().value(),
                    "EXPIRED"
            );
            inventoryService.release(
                    cmd,
                    new SagaContext(
                            null,
                            r.getCorrelationId(),
                            null,
                            "inventory",
                            "EXPIRE",
                            "Order",
                            r.getOrderId().value()
                    )
            );
        }
    }
}
