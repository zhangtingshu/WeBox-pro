package com.webox.controller;

import com.webox.common.ApiException;
import com.webox.common.ApiResponse;
import com.webox.common.ErrorCode;
import com.webox.common.RequestContext;
import com.webox.dto.Dtos.DishView;
import com.webox.dto.Dtos.MenuPageView;
import com.webox.dto.Dtos.MetaView;
import com.webox.service.MenuService;
import com.webox.service.SseHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;
    private final SseHub sseHub;

    public MenuController(MenuService menuService, SseHub sseHub) {
        this.menuService = menuService;
        this.sseHub = sseHub;
    }

    @GetMapping("/menu")
    public ApiResponse<MenuPageView> menu(
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean recommend,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String date) {
        return ApiResponse.ok(menuService.menu(categories, keyword, recommend,
                page, size, parseDate(date), RequestContext.userId()));
    }

    @GetMapping("/dishes/{id}")
    public ApiResponse<DishView> detail(@PathVariable Long id) {
        return ApiResponse.ok(menuService.detail(id));
    }

    /** SSE stock stream; the SPA reads it with fetch() so the Bearer token stays in the header. */
    @GetMapping(value = "/menu/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseHub.subscribe();
    }

    @GetMapping("/meta")
    public ApiResponse<MetaView> meta() {
        return ApiResponse.ok(MenuService.meta());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please select a valid menu date.");
        }
    }
}
