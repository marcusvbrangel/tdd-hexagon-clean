package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade JPA para reservas de estoque.
 */
@Entity
@Table(
        name = "inventory_reservations",
        indexes = {
                @Index(name = "idx_inv_res_status_expires", columnList = "status, expires_at"),
                @Index(name = "uk_inv_res_order_id", columnList = "order_id", unique = true)
        }
)
public class JpaReservationEntity {

    @Id
    @Column(name = "reservation_id", length = 64)
    private String reservationId;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_command_id", length = 64)
    private String lastCommandId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JpaReservationItemEntity> items = new ArrayList<>();

    protected JpaReservationEntity() {
    }

    public JpaReservationEntity(String reservationId,
                                String orderId,
                                String status,
                                Instant createdAt,
                                Instant expiresAt) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getReservationId() { return reservationId; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getLastCommandId() { return lastCommandId; }
    public String getCorrelationId() { return correlationId; }
    public List<JpaReservationItemEntity> getItems() { return items; }

    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setLastCommandId(String lastCommandId) { this.lastCommandId = lastCommandId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    /**
     * Adiciona item vinculando com a reserva.
     */
    public void addItem(JpaReservationItemEntity item) {
        items.add(item);
        item.setReservation(this);
    }

    /**
     * Remove itens atuais antes de reconstruir a lista.
     */
    public void clearItems() {
        items.forEach(i -> i.setReservation(null));
        items.clear();
    }
}
