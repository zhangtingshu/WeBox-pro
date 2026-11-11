package com.webox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dishes")
public class DishEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "protein", nullable = false, length = 80)
    private String protein;

    /** Comma-separated enum values, e.g. "Dairy,Egg". */
    @Column(name = "allergens", nullable = false, length = 255)
    private String allergens;

    @Column(name = "spice_level", nullable = false, length = 20)
    private String spiceLevel;

    @Column(name = "image_url", nullable = false, length = 300)
    private String imageUrl;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
