package com.webox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "preferences")
public class PreferenceEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "allergens", nullable = false, length = 255)
    private String allergens = "";

    @Column(name = "cuisines", nullable = false, length = 255)
    private String cuisines = "";

    @Column(name = "spice_level", nullable = false, length = 20)
    private String spiceLevel = "None";

    @Column(name = "taste", nullable = false, length = 20)
    private String taste = "Balanced";

    @Column(name = "budget_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetMin = BigDecimal.ZERO;

    @Column(name = "budget_max", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetMax = BigDecimal.ZERO;

    @Column(name = "recommend_enabled", nullable = false)
    private boolean recommendEnabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
