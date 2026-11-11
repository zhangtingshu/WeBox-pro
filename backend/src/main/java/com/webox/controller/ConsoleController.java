package com.webox.controller;

import com.webox.common.ApiResponse;
import com.webox.dto.Dtos.ActiveRequest;
import com.webox.dto.Dtos.ConsoleDishPage;
import com.webox.dto.Dtos.ConsoleDishRequest;
import com.webox.dto.Dtos.DailyMenuRequest;
import com.webox.dto.Dtos.DailyMenuView;
import com.webox.dto.Dtos.DashboardView;
import com.webox.dto.Dtos.DishView;
import com.webox.dto.Dtos.UploadView;
import com.webox.security.RequireRole;
import com.webox.service.ConsoleService;
import com.webox.service.DashboardService;
import com.webox.service.MenuCache;
import com.webox.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Admin-only Console. Employees receive 403 from the RBAC interceptor. */
@RestController
@RequestMapping("/api/console")
@RequireRole("ADMIN")
public class ConsoleController {

    private final ConsoleService consoleService;
    private final DashboardService dashboardService;
    private final UploadService uploadService;
    private final MenuCache menuCache;

    public ConsoleController(ConsoleService consoleService, DashboardService dashboardService,
                             UploadService uploadService, MenuCache menuCache) {
        this.consoleService = consoleService;
        this.dashboardService = dashboardService;
        this.uploadService = uploadService;
        this.menuCache = menuCache;
    }

    @GetMapping("/dishes")
    public ApiResponse<ConsoleDishPage> dishes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(consoleService.list(keyword, category, page, size));
    }

    @PostMapping("/dishes")
    public ApiResponse<DishView> create(@Valid @RequestBody ConsoleDishRequest request) {
        return ApiResponse.ok(consoleService.create(request));
    }

    @PutMapping("/dishes/{id}")
    public ApiResponse<DishView> update(@PathVariable Long id,
                                        @Valid @RequestBody ConsoleDishRequest request) {
        return ApiResponse.ok(consoleService.update(id, request));
    }

    @PatchMapping("/dishes/{id}/active")
    public ApiResponse<DishView> setActive(@PathVariable Long id,
                                           @RequestBody ActiveRequest request) {
        return ApiResponse.ok(consoleService.setActive(id, request.active()));
    }

    @PostMapping("/uploads")
    public ApiResponse<UploadView> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new UploadView(uploadService.store(file)));
    }

    @GetMapping("/daily-menu")
    public ApiResponse<DailyMenuView> dailyMenu(@RequestParam(required = false) String date) {
        return ApiResponse.ok(consoleService.dailyMenu(date));
    }

    @PutMapping("/daily-menu")
    public ApiResponse<DailyMenuView> setDailyMenu(@Valid @RequestBody DailyMenuRequest request) {
        return ApiResponse.ok(consoleService.setDailyMenu(request));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }

    @GetMapping("/cache-stats")
    public ApiResponse<Map<String, Object>> cacheStats() {
        return ApiResponse.ok(Map.of(
                "type", "in-process TTL cache",
                "ttlSeconds", menuCache.ttlSeconds(),
                "hits", menuCache.hits(),
                "misses", menuCache.misses(),
                "entries", menuCache.size()));
    }
}
