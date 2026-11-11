package com.webox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@Entity
@Table(name = "daily_menu")
public class DailyMenuEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "remaining_qty", nullable = false)
    private int remainingQty;

    /** Incremented by the atomic stock UPDATE; documents the optimistic intent. */
    @Column(name = "version", nullable = false)
    private int version;
}
