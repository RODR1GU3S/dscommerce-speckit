package com.devsuperior.dscommerce.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.FractionalSeconds;

import java.time.Instant;
import java.util.Objects;

/** One recorded settlement, identified exclusively by its order. */
@Entity
@Table(name = "tb_payment")
public class Payment {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_order"))
    private Order order;

    @NotNull
    @FractionalSeconds(3)
    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMP(3) WITH TIME ZONE")
    private Instant occurredAt;

    protected Payment() {
    }

    public Payment(Order order, Instant occurredAt) {
        this.order = Objects.requireNonNull(order, "Order is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "Payment occurrence is required");
    }

    public Long getOrderId() {
        return orderId;
    }

    public Order getOrder() {
        return order;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
