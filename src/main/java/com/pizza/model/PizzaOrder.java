package com.pizza.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pizza_orders")
public class PizzaOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NOT_STARTED;

    private String promoCode;

    @Enumerated(EnumType.STRING)
    private TipMode tipMode;

    @Column(precision = 10, scale = 2)
    private BigDecimal tipValue;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LineItem> lineItems = new ArrayList<>();

    protected PizzaOrder() {}

    public PizzaOrder(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // --- Getters ---

    public Long getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public String getPromoCode() { return promoCode; }
    public TipMode getTipMode() { return tipMode; }
    public BigDecimal getTipValue() { return tipValue; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public List<LineItem> getLineItems() { return lineItems; }

    // --- Setters ---

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
    public void setTipMode(TipMode tipMode) { this.tipMode = tipMode; }
    public void setTipValue(BigDecimal tipValue) { this.tipValue = tipValue; }

    // --- Line item management ---

    public void addLineItem(LineItem item) {
        lineItems.add(item);
        item.setOrder(this);
    }

    public void removeLineItem(LineItem item) {
        lineItems.remove(item);
        item.setOrder(null);
    }

    public void clearLineItems() {
        lineItems.forEach(item -> item.setOrder(null));
        lineItems.clear();
    }
}

