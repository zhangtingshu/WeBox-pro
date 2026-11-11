package com.webox.repository;

import com.webox.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(Long orderId);

    List<OrderItemEntity> findByOrderIdIn(Collection<Long> orderIds);
}
