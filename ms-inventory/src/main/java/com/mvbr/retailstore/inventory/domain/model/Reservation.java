package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Entidade de dominio que representa uma reserva de estoque por pedido.
 * Centraliza status, itens e prazos de expiracao.
 */
public class Reservation {

    private final ReservationId reservationId;
    private final OrderId orderId;

    private ReservationStatus status;
    private String reason;

    private final Instant createdAt;
    private Instant expiresAt;

    private String lastCommandId;
    private String correlationId;

    private final List<ReservationItem> items = new ArrayList<>();

    public Reservation(ReservationId reservationId,
                       OrderId orderId,
                       ReservationStatus status,
                       String reason,
                       Instant createdAt,
                       Instant expiresAt,
                       String lastCommandId,
                       String correlationId) {

        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");

        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be >= createdAt");
        }

        this.reason = normalizeBlankToNull(reason);
        this.lastCommandId = normalizeBlankToNull(lastCommandId);
        this.correlationId = normalizeBlankToNull(correlationId);

        // reason coerente com status
        if (requiresReason(status) && this.reason == null) {
            throw new IllegalArgumentException("reason is required for status " + status);
        }
        if (!requiresReason(status)) {
            this.reason = null;
        }
    }

    /**
     * Reidratação (persistência -> domínio), incluindo itens.
     */
    public static Reservation restore(ReservationId reservationId,
                                      OrderId orderId,
                                      ReservationStatus status,
                                      String reason,
                                      Instant createdAt,
                                      Instant expiresAt,
                                      String lastCommandId,
                                      String correlationId,
                                      List<ReservationItem> items) {

        Reservation r = new Reservation(
                reservationId,
                orderId,
                status,
                reason,
                createdAt,
                expiresAt,
                lastCommandId,
                correlationId
        );

        if (items != null && !items.isEmpty()) {
            r.items.addAll(items);
        }
        return r;
    }

    public ReservationId getReservationId() { return reservationId; }
    public OrderId getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getLastCommandId() { return lastCommandId; }
    public String getCorrelationId() { return correlationId; }

    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    // ✅ Estes 2 métodos são os que resolvem seus erros nas linhas 164 / 228 / 232
    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public boolean isCommitted() {
        return status == ReservationStatus.COMMITTED;
    }

    public boolean isClosed() {
        return status == ReservationStatus.RELEASED
                || status == ReservationStatus.COMMITTED
                || status == ReservationStatus.REJECTED
                || status == ReservationStatus.EXPIRED;
    }

    public void addItem(ProductId productId, Quantity quantity) {
        requireOpen();
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(quantity, "quantity");
        items.add(new ReservationItem(productId, quantity));
    }

    public void markReserved() {
        requireTransition(ReservationStatus.PENDING, ReservationStatus.RESERVED);
        this.status = ReservationStatus.RESERVED;
        this.reason = null;
    }

    public void markCommitted() {
        requireTransition(ReservationStatus.RESERVED, ReservationStatus.COMMITTED);
        this.status = ReservationStatus.COMMITTED;
        this.reason = null;
    }

    public void markRejected(String reason) {
        requireTransition(ReservationStatus.PENDING, ReservationStatus.REJECTED);
        this.status = ReservationStatus.REJECTED;
        this.reason = requireNonBlank(reason, "reason");
    }

    public void markReleased(String reason) {
        requireTransition(ReservationStatus.RESERVED, ReservationStatus.RELEASED);
        this.status = ReservationStatus.RELEASED;
        this.reason = requireNonBlank(reason, "reason");
    }

    public void markExpired() {
        if (status == ReservationStatus.PENDING || status == ReservationStatus.RESERVED) {
            this.status = ReservationStatus.EXPIRED;
            this.reason = "EXPIRED";
            return;
        }
        throw new IllegalStateException("invalid transition: " + status + " -> EXPIRED");
    }

    public void extendExpiresAt(Instant newExpiresAt) {
        requireOpen();
        Objects.requireNonNull(newExpiresAt, "newExpiresAt");
        if (newExpiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be >= createdAt");
        }
        if (newExpiresAt.isBefore(this.expiresAt)) {
            throw new IllegalArgumentException("expiresAt cannot be reduced (only extend)");
        }
        this.expiresAt = newExpiresAt;
    }

    public void updateLastCommandId(String commandId) {
        this.lastCommandId = requireNonBlank(commandId, "commandId");
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException("reservation is closed: " + status);
        }
    }

    private void requireTransition(ReservationStatus from, ReservationStatus to) {
        if (this.status != from) {
            throw new IllegalStateException("invalid transition: " + this.status + " -> " + to);
        }
    }

    private static boolean requiresReason(ReservationStatus status) {
        return status == ReservationStatus.REJECTED
                || status == ReservationStatus.RELEASED
                || status == ReservationStatus.EXPIRED;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
