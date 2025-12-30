package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.inventory.application.port.out.InventoryItemRepository;
import com.mvbr.retailstore.inventory.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.domain.model.InventoryItem;
import com.mvbr.retailstore.inventory.domain.model.OrderId;
import com.mvbr.retailstore.inventory.domain.model.ProductId;
import com.mvbr.retailstore.inventory.domain.model.Quantity;
import com.mvbr.retailstore.inventory.domain.model.Reservation;
import com.mvbr.retailstore.inventory.domain.model.ReservationId;
import com.mvbr.retailstore.inventory.domain.model.ReservationItem;
import com.mvbr.retailstore.inventory.domain.model.ReservationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA que implementa as tres portas de persistencia do inventory.
 * Agrupa repositorios para reduzir boilerplate no MVP.
 */
@Component
public class JpaInventoryRepositoryAdapter implements InventoryItemRepository, ReservationRepository, ProcessedMessageRepository {

    private final JpaInventorySpringDataRepository inventoryRepo;
    private final JpaReservationSpringDataRepository reservationRepo;
    private final JpaProcessedMessageSpringDataRepository processedRepo;

    public JpaInventoryRepositoryAdapter(JpaInventorySpringDataRepository inventoryRepo,
                                         JpaReservationSpringDataRepository reservationRepo,
                                         JpaProcessedMessageSpringDataRepository processedRepo) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.processedRepo = processedRepo;
    }

    /**
     * Carrega itens com lock pessimista e cria itens "virtuais" quando faltam.
     */
    @Override
    public List<InventoryItem> lockByProductIds(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<JpaInventoryItemEntity> entities = inventoryRepo.lockByProductIds(productIds);

        Map<String, JpaInventoryItemEntity> map = entities.stream()
                .collect(Collectors.toMap(JpaInventoryItemEntity::getProductId, e -> e));

        List<InventoryItem> result = new ArrayList<>();
        for (String pid : productIds) {
            JpaInventoryItemEntity e = map.get(pid);
            if (e == null) {
                // item "virtual" para o MVP
                result.add(new InventoryItem(new ProductId(pid), 0, 0, Instant.now()));
            } else {
                result.add(toDomain(e));
            }
        }
        return result;
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        return inventoryRepo.findById(productId).map(this::toDomain);
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        JpaInventoryItemEntity e = new JpaInventoryItemEntity(
                item.getProductId().value(),
                item.getOnHand(),
                item.getReserved(),
                item.getUpdatedAt()
        );
        inventoryRepo.save(e);
        return item;
    }

    @Override
    public Optional<Reservation> findByOrderId(String orderId) {
        return reservationRepo.findByOrderId(orderId).map(this::toDomain);
    }

    /**
     * Persiste reserva e seus itens (reconstrucao completa).
     *
     * Ajustes para "tanque":
     * - reservationId agora é ReservationId no dominio, mas é String no JPA => .value()
     * - status agora é Enum no JPA => setStatus(reservation.getStatus())
     */
    @Override
    public Reservation save(Reservation reservation) {

        String reservationId = reservation.getReservationId().value();

        JpaReservationEntity e = reservationRepo.findById(reservationId)
                .orElseGet(() -> new JpaReservationEntity(
                        reservationId,
                        reservation.getOrderId().value(),
                        reservation.getStatus(),           // Enum direto
                        reservation.getCreatedAt(),
                        reservation.getExpiresAt()
                ));

        e.setStatus(reservation.getStatus());              // Enum direto
        e.setReason(reservation.getReason());
        e.setExpiresAt(reservation.getExpiresAt());
        e.setLastCommandId(reservation.getLastCommandId());
        e.setCorrelationId(reservation.getCorrelationId());

        e.clearItems();
        for (ReservationItem item : reservation.getItems()) {
            e.addItem(new JpaReservationItemEntity(item.productId().value(), item.quantity().value()));
        }

        reservationRepo.save(e);
        return reservation;
    }

    @Override
    public List<Reservation> findExpiredReserved(Instant now, int limit) {
        List<JpaReservationEntity> list = reservationRepo.findExpiredReserved(now);
        if (limit > 0 && list.size() > limit) {
            list = list.subList(0, limit);
        }
        return list.stream().map(this::toDomain).toList();
    }

    @Override
    public boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt) {
        try {
            processedRepo.save(new JpaProcessedMessageEntity(messageId, messageType, aggregateId, processedAt));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private InventoryItem toDomain(JpaInventoryItemEntity e) {
        return new InventoryItem(new ProductId(e.getProductId()), e.getOnHand(), e.getReserved(), e.getUpdatedAt());
    }

    /**
     * Converte entidade JPA de reserva em objeto de dominio.
     *
     * Ajustes para "tanque":
     * - status no JPA já é ReservationStatus (Enum) => sem valueOf
     * - reidratação deve usar Reservation.restore(...) para poder carregar itens
     *   mesmo se reserva estiver fechada (evita chamar addItem() e estourar regra).
     */
    private Reservation toDomain(JpaReservationEntity e) {

        List<ReservationItem> items = e.getItems().stream()
                .map(it -> new ReservationItem(
                        new ProductId(it.getProductId()),
                        new Quantity(it.getQuantity())
                ))
                .toList();

        return Reservation.restore(
                new ReservationId(e.getReservationId()),
                new OrderId(e.getOrderId()),
                e.getStatus(),              // Enum direto
                e.getReason(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getLastCommandId(),
                e.getCorrelationId(),
                items
        );
    }
}
