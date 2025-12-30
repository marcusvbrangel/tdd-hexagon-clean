package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.inventory.domain.model.*;

import java.util.List;
import java.util.Objects;

public final class JpaReservationMapper {

    private JpaReservationMapper() {
    }

    public static Reservation toDomain(JpaReservationEntity e) {
        Objects.requireNonNull(e, "entity");

        ReservationStatus status = Objects.requireNonNull(e.getStatus(), "reservation status is null in DB");

        List<ReservationItem> items = e.getItems().stream()
                .map(it -> new ReservationItem(
                        new ProductId(it.getProductId()),
                        new Quantity(it.getQuantity())
                ))
                .toList();

        return Reservation.restore(
                new ReservationId(e.getReservationId()),   // <-- String -> ReservationId
                new OrderId(e.getOrderId()),
                status,
                e.getReason(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getLastCommandId(),
                e.getCorrelationId(),
                items
        );
    }

    public static JpaReservationEntity toJpaNew(Reservation d) {
        Objects.requireNonNull(d, "domain");

        JpaReservationEntity e = new JpaReservationEntity(
                d.getReservationId().value(),              // <-- ReservationId -> String
                d.getOrderId().value(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getExpiresAt()
        );

        e.setReason(d.getReason());
        e.setLastCommandId(d.getLastCommandId());
        e.setCorrelationId(d.getCorrelationId());

        d.getItems().forEach(item ->
                e.addItem(new JpaReservationItemEntity(
                        item.productId().value(),
                        item.quantity().value()
                ))
        );

        return e;
    }

    public static void copyToJpa(Reservation d, JpaReservationEntity e) {
        Objects.requireNonNull(d, "domain");
        Objects.requireNonNull(e, "entity");

        e.setStatus(d.getStatus());
        e.setReason(d.getReason());
        e.setExpiresAt(d.getExpiresAt());
        e.setLastCommandId(d.getLastCommandId());
        e.setCorrelationId(d.getCorrelationId());

        e.clearItems();
        d.getItems().forEach(item ->
                e.addItem(new JpaReservationItemEntity(
                        item.productId().value(),
                        item.quantity().value()
                ))
        );
    }
}
