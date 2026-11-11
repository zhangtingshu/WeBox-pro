package com.webox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "dish_name", nullable = false, length = 120)
    private String dishName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** JSON array of human-readable option labels captured at purchase time. */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}
