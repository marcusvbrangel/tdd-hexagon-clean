package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Reservation {

    private final String reservationId;
    private final OrderId orderId;
    private ReservationStatus status;
    private String reason;
    private final Instant createdAt;
    private Instant expiresAt;
    private String lastCommandId;
    private String correlationId;
    private final List<ReservationItem> items = new ArrayList<>();

    public Reservation(String reservationId,
                       OrderId orderId,
                       ReservationStatus status,
                       String reason,
                       Instant createdAt,
                       Instant expiresAt,
                       String lastCommandId,
                       String correlationId) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastCommandId = lastCommandId;
        this.correlationId = correlationId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getLastCommandId() {
        return lastCommandId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public boolean isClosed() {
        return status == ReservationStatus.RELEASED
                || status == ReservationStatus.REJECTED
                || status == ReservationStatus.EXPIRED;
    }

    public void addItem(ProductId productId, Quantity quantity) {
        items.add(new ReservationItem(productId, quantity));
    }

    public void markReserved() {
        this.status = ReservationStatus.RESERVED;
        this.reason = null;
    }

    public void markRejected(String reason) {
        this.status = ReservationStatus.REJECTED;
        this.reason = reason;
    }

    public void markReleased(String reason) {
        this.status = ReservationStatus.RELEASED;
        this.reason = reason;
    }

    public void markExpired() {
        this.status = ReservationStatus.EXPIRED;
        this.reason = "EXPIRED";
    }

    public void updateExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void updateLastCommandId(String commandId) {
        this.lastCommandId = commandId;
    }
}
