package com.devsuperior.dscommerce.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.util.Objects;

/** Historical purchase values and ownership within the order aggregate. */
@Entity
@Table(name = "tb_order_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_item_order_product", columnNames = {"order_id", "product_id"}),
        @UniqueConstraint(name = "uk_order_item_order_position", columnNames = {"order_id", "position"})
})
@Check(name = "ck_order_item_quantity_positive", constraints = "quantity > 0")
@Check(name = "ck_order_item_position_nonnegative", constraints = "position >= 0")
@Check(name = "ck_order_item_unit_price_positive", constraints = "unit_price > 0")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer position;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 17, fraction = 2)
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    public OrderItem(Product product, Integer quantity, BigDecimal unitPrice) {
        this.product = Objects.requireNonNull(product, "Product is required");
        this.quantity = Objects.requireNonNull(quantity, "Purchase quantity is required");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Historical unit price is required");
    }

    void bindTo(Order order, int position) {
        if (this.order != null) {
            throw new IllegalArgumentException("Order item already belongs to an order");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Order item position must be nonnegative");
        }
        this.order = Objects.requireNonNull(order, "Owning order is required");
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Order getOrder() {
        return order;
    }

    public Integer getPosition() {
        return position;
    }

    @Transient
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity.longValue()));
    }
}
