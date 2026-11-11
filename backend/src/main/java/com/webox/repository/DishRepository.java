package com.webox.repository;

import com.webox.entity.DishEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Specifications keep keyword search fully parameter-bound (SQL-injection safe). */
public interface DishRepository extends JpaRepository<DishEntity, Long>,
        JpaSpecificationExecutor<DishEntity> {
}
