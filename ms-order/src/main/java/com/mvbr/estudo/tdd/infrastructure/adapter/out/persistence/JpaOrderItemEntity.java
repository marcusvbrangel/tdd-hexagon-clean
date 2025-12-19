package com.mvbr.estudo.tdd.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "order_items")
public class JpaOrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    // ============================
    // Relacionamento com Order
    // ============================
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private JpaOrderEntity order;

    // ============================
    // Construtor padrão (JPA)
    // ============================
    protected JpaOrderItemEntity() {
    }

    // ============================
    // Getters e setters
    // ============================
    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public JpaOrderEntity getOrder() {
        return order;
    }

    public void setOrder(JpaOrderEntity order) {
        this.order = order;
    }

    // ============================
    // equals / hashCode
    // ============================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpaOrderItemEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}