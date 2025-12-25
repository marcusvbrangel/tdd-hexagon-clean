package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidade JPA para itens de uma reserva.
 */
@Entity
@Table(
        name = "inventory_reservation_items",
        indexes = @Index(name = "idx_inv_res_item_reservation", columnList = "reservation_id")
)
public class JpaReservationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private JpaReservationEntity reservation;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    protected JpaReservationItemEntity() {
    }

    public JpaReservationItemEntity(String productId, long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public JpaReservationEntity getReservation() { return reservation; }
    public String getProductId() { return productId; }
    public long getQuantity() { return quantity; }

    /**
     * Define a reserva pai (usado pelo agregador JPA).
     */
    public void setReservation(JpaReservationEntity reservation) { this.reservation = reservation; }
}
