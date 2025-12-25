package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entidade de dominio que representa uma reserva de estoque por pedido.
 * Centraliza status, itens e prazos de expiracao.
 */
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

    /**
     * Identificador unico da reserva.
     */
    public String getReservationId() {
        return reservationId;
    }

    /**
     * Pedido associado a esta reserva.
     */
    public OrderId getOrderId() {
        return orderId;
    }

    /**
     * Status atual da reserva.
     */
    public ReservationStatus getStatus() {
        return status;
    }

    /**
     * Motivo de rejeicao/liberacao, quando aplicavel.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Data/hora de criacao da reserva.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Data/hora de expiracao da reserva.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Ultimo commandId associado a esta reserva (idempotencia/observabilidade).
     */
    public String getLastCommandId() {
        return lastCommandId;
    }

    /**
     * CorrelationId da saga para rastreamento.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Lista de itens reservados (imutavel externamente).
     */
    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Conveniencia: indica se a reserva esta efetivamente ativa.
     */
    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    /**
     * Indica se a reserva ja foi encerrada (nao pode mais ser alterada).
     */
    public boolean isClosed() {
        return status == ReservationStatus.RELEASED
                || status == ReservationStatus.REJECTED
                || status == ReservationStatus.EXPIRED;
    }

    /**
     * Adiciona um item a reserva (usado no fluxo de reserva bem sucedida).
     */
    public void addItem(ProductId productId, Quantity quantity) {
        items.add(new ReservationItem(productId, quantity));
    }

    /**
     * Marca a reserva como efetivada.
     */
    public void markReserved() {
        this.status = ReservationStatus.RESERVED;
        this.reason = null;
    }

    /**
     * Marca a reserva como rejeitada com motivo.
     */
    public void markRejected(String reason) {
        this.status = ReservationStatus.REJECTED;
        this.reason = reason;
    }

    /**
     * Marca a reserva como liberada (compensacao).
     */
    public void markReleased(String reason) {
        this.status = ReservationStatus.RELEASED;
        this.reason = reason;
    }

    /**
     * Marca a reserva como expirada por timeout interno.
     */
    public void markExpired() {
        this.status = ReservationStatus.EXPIRED;
        this.reason = "EXPIRED";
    }

    /**
     * Atualiza o prazo de expiracao, quando necessario.
     */
    public void updateExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Atualiza o ultimo commandId processado.
     */
    public void updateLastCommandId(String commandId) {
        this.lastCommandId = commandId;
    }
}
