package com.webox;

import com.webox.dto.Dtos.CartItemRequest;
import com.webox.dto.Dtos.OrderView;
import com.webox.dto.Dtos.PlaceOrderRequest;
import com.webox.dto.Dtos.StockEvent;
import com.webox.entity.OrderEntity;
import com.webox.repository.*;
import com.webox.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderIdempotencyTest {

    @Test
    void repeatedSubmitReturnsTheExistingOrderAndNeverOpensASecondTransaction() {
        UserRepository users = mock(UserRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        DishRepository dishes = mock(DishRepository.class);
        DailyMenuRepository dailyMenu = mock(DailyMenuRepository.class);
        PreferenceRepository preferences = mock(PreferenceRepository.class);
        DishAssembler assembler = mock(DishAssembler.class);
        MenuCache cache = mock(MenuCache.class);
        SseHub sse = mock(SseHub.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        OrderEntity existing = new OrderEntity();
        existing.setId(99L);
        existing.setOrderNo("WB20261111-ABC123");
        existing.setUserId(1L);
        existing.setDeliveryDate(LocalDate.of(2026, 11, 11));
        existing.setMealPeriod("Lunch");
        existing.setAddress("Floor 3, Building A");
        existing.setTotalAmount(new BigDecimal("22.00"));
        existing.setStatus("Pending");
        existing.setCreatedAt(LocalDateTime.of(2026, 11, 11, 9, 40));
        existing.setUpdatedAt(LocalDateTime.of(2026, 11, 11, 9, 40));

        when(orders.findByIdempotencyKey("KEY-1")).thenReturn(Optional.of(existing));
        when(orderItems.findByOrderId(99L)).thenReturn(List.of());

        OrderService service = new OrderService(users, orders, orderItems, dishes, dailyMenu,
                preferences, assembler, cache, sse, transactionManager);
        PlaceOrderRequest request = new PlaceOrderRequest("2026-11-11", "Lunch",
                "Floor 3, Building A", List.of(new CartItemRequest(1L, 1, List.of())));

        OrderView first = service.place(1L, request, "KEY-1");
        OrderView second = service.place(1L, request, "KEY-1");

        assertEquals("WB20261111-ABC123", first.orderNo());
        assertEquals(first.orderNo(), second.orderNo());
        assertEquals(2200L, first.totalCents());
        verify(orders, times(2)).findByIdempotencyKey("KEY-1");
        verify(transactionManager, never()).getTransaction(any());
        verify(sse, never()).broadcast(any(List.class));
        verify(cache, never()).invalidateAll();
    }
}
