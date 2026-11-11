package com.webox.controller;

import com.webox.common.ApiResponse;
import com.webox.common.RequestContext;
import com.webox.dto.Dtos.ActiveOrderView;
import com.webox.dto.Dtos.OrderView;
import com.webox.dto.Dtos.PlaceOrderRequest;
import com.webox.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** One Idempotency-Key per checkout attempt: repeated clicks never create a second order. */
    @PostMapping
    public ApiResponse<OrderView> place(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(orderService.place(RequestContext.userId(), request, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<List<OrderView>> orders() {
        return ApiResponse.ok(orderService.orders(RequestContext.userId()));
    }

    @GetMapping("/active")
    public ApiResponse<ActiveOrderView> active(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String mealPeriod) {
        return ApiResponse.ok(orderService.active(RequestContext.userId(), date, mealPeriod));
    }

    @GetMapping("/addresses")
    public ApiResponse<List<String>> addresses() {
        return ApiResponse.ok(orderService.historyAddresses(RequestContext.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(RequestContext.userId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(RequestContext.userId(), id));
    }
}
