package com.devsuperior.dscommerce.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** Complete purchase aggregate with immutable historical values. */
@Entity
@Table(name = "tb_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private UserAccount buyer;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.WAITING_PAYMENT;

    @NotEmpty
    @Valid
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC")
    private List<@NotNull OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(UserAccount buyer, Instant createdAt, List<OrderItem> items) {
        this.buyer = Objects.requireNonNull(buyer, "Buyer is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        if (items == null) {
            throw new IllegalArgumentException("Order items are required");
        }
        var supplied = new ArrayList<>(items);
        if (supplied.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        var distinctItems = Collections.newSetFromMap(new IdentityHashMap<OrderItem, Boolean>());
        // Validate the whole list before binding anything to avoid changing items on rejection.
        for (var item : supplied) {
            if (item == null) {
                throw new IllegalArgumentException("Order must not contain a null item");
            }
            if (!distinctItems.add(item) || item.getOrder() != null) {
                throw new IllegalArgumentException("Order items must be distinct and not already owned");
            }
        }
        this.items = supplied;
        for (var position = 0; position < this.items.size(); position++) {
            this.items.get(position).bindTo(this, position);
        }
    }

    public Long getId() {
        return id;
    }

    public UserAccount getBuyer() {
        return buyer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    @Transient
    public BigDecimal getTotal() {
        return items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
