package com.webox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "option_groups")
public class OptionGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "max_select", nullable = false)
    private int maxSelect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
