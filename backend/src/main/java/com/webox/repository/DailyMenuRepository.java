package com.webox.repository;

import com.webox.entity.DailyMenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyMenuRepository extends JpaRepository<DailyMenuEntity, Long> {

    Optional<DailyMenuEntity> findByMenuDateAndDishId(LocalDate menuDate, Long dishId);

    List<DailyMenuEntity> findByMenuDate(LocalDate menuDate);

    List<DailyMenuEntity> findByMenuDateAndDishIdIn(LocalDate menuDate, Collection<Long> dishIds);

    /**
     * Atomic conditional decrement: an InnoDB row lock + the remaining_qty >= qty predicate
     * make overselling impossible even when many employees order the same dish concurrently.
     * Returns the number of affected rows (0 = insufficient stock).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE daily_menu SET remaining_qty = remaining_qty - :qty, version = version + 1 "
            + "WHERE menu_date = :date AND dish_id = :dishId AND remaining_qty >= :qty", nativeQuery = true)
    int deductStock(@Param("date") LocalDate date, @Param("dishId") Long dishId, @Param("qty") int qty);

    /** On cancellation, restore stock but never above the day's declared supply. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE daily_menu SET remaining_qty = LEAST(total_qty, remaining_qty + :qty), "
            + "version = version + 1 WHERE menu_date = :date AND dish_id = :dishId", nativeQuery = true)
    int restoreStock(@Param("date") LocalDate date, @Param("dishId") Long dishId, @Param("qty") int qty);

    /** Locking "current read": sees the latest committed stock, not the transaction snapshot. */
    @Query(value = "SELECT remaining_qty FROM daily_menu "
            + "WHERE menu_date = :date AND dish_id = :dishId FOR UPDATE", nativeQuery = true)
    Object currentRemainingForUpdate(@Param("date") LocalDate date, @Param("dishId") Long dishId);

    @Query(value = "SELECT m.dish_id, d.name, m.remaining_qty FROM daily_menu m "
            + "JOIN dishes d ON d.id = m.dish_id "
            + "WHERE m.menu_date = :date AND m.remaining_qty BETWEEN 1 AND :threshold "
            + "ORDER BY m.remaining_qty ASC", nativeQuery = true)
    List<Object[]> lowStock(@Param("date") LocalDate date, @Param("threshold") int threshold);
}
