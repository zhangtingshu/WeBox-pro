package com.webox.repository;

import com.webox.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);

    Optional<OrderEntity> findFirstByUserIdAndDeliveryDateAndMealPeriodAndStatusInOrderByCreatedAtDesc(
            Long userId, LocalDate deliveryDate, String mealPeriod, Collection<String> statuses);

    @Query("select distinct oi.dishId from OrderItemEntity oi, OrderEntity o "
            + "where oi.orderId = o.id and o.userId = :userId "
            + "and o.createdAt >= :since and o.status <> 'Cancelled'")
    List<Long> recentlyOrderedDishIds(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query(value = "SELECT COUNT(*), COALESCE(SUM(CASE WHEN status <> 'Cancelled' THEN total_amount ELSE 0 END), 0) "
            + "FROM orders WHERE DATE(created_at) = :date", nativeQuery = true)
    List<Object[]> todayTotals(@Param("date") LocalDate date);

    @Query(value = "SELECT status, COUNT(*) FROM orders WHERE DATE(created_at) = :date GROUP BY status",
            nativeQuery = true)
    List<Object[]> statusCounts(@Param("date") LocalDate date);

    @Query(value = "SELECT oi.dish_name, SUM(oi.quantity), COALESCE(SUM(oi.subtotal), 0) "
            + "FROM order_items oi JOIN orders o ON o.id = oi.order_id "
            + "WHERE DATE(o.created_at) BETWEEN :from AND :to AND o.status <> 'Cancelled' "
            + "GROUP BY oi.dish_name ORDER BY SUM(oi.quantity) DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topDishes(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT meal_period, COUNT(*) FROM orders "
            + "WHERE DATE(created_at) = :date AND status <> 'Cancelled' GROUP BY meal_period",
            nativeQuery = true)
    List<Object[]> mealDistribution(@Param("date") LocalDate date);

    @Query(value = "SELECT DATE(created_at), COUNT(*), COALESCE(SUM(total_amount), 0) FROM orders "
            + "WHERE DATE(created_at) BETWEEN :from AND :to AND status <> 'Cancelled' "
            + "GROUP BY DATE(created_at) ORDER BY DATE(created_at)", nativeQuery = true)
    List<Object[]> trend(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
