package com.webox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** All API contracts. Amounts are integer cents; every user-facing string is English. */
public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(@NotBlank @Email @Size(max = 200) String email,
                                  @NotBlank @Size(min = 8, max = 64) String password,
                                  @Size(max = 100) String fullName) {}

    public record LoginRequest(@NotBlank @Email @Size(max = 200) String email,
                               @NotBlank @Size(max = 64) String password) {}

    public record UserView(Long id, String email, String fullName, String role) {}

    public record AuthView(String accessToken, long expiresInSeconds, UserView user) {}

    /** Internal service result: raw refresh token is only used to set the httpOnly cookie. */
    public record Session(String accessToken, String refreshToken, long expiresInSeconds, UserView user) {}

    // ---- preferences ----
    public record PreferenceView(List<String> allergens, List<String> cuisines, String spiceLevel,
                                 String taste, long budgetMinCents, long budgetMaxCents,
                                 boolean recommendEnabled) {}

    public record PreferenceRequest(List<String> allergens, List<String> cuisines, String spiceLevel,
                                    String taste, Long budgetMinCents, Long budgetMaxCents,
                                    Boolean recommendEnabled) {}

    // ---- menu ----
    public record OptionItemView(Long id, String name, long extraCents) {}

    public record OptionGroupView(Long id, String name, boolean required, int maxSelect,
                                  List<OptionItemView> items) {}

    public record DishView(Long id, String name, String description, long priceCents, String category,
                           String protein, List<String> allergens, String spiceLevel, String imageUrl,
                           boolean active, List<OptionGroupView> optionGroups) {}

    public record MenuItemView(DishView dish, int remaining, boolean lowStock, boolean soldOut,
                               boolean recommended, int matchScore) {}

    public record MenuPageView(List<MenuItemView> items, long totalElements, int totalPages,
                               int page, int size, String menuDate, String defaultMealPeriod,
                               String defaultDeliveryDate, String cutoffNotice,
                               boolean recommendEnabled) {}

    public record StockEvent(Long dishId, int remaining, String menuDate) {}

    public record MetaView(List<String> categories, List<String> allergens, List<String> spiceLevels,
                           List<String> tastes, List<String> mealPeriods, List<String> orderStatuses,
                           int maxPortions, int lowStockThreshold, String lunchCutoff,
                           String dinnerCutoff) {}

    // ---- orders ----
    public record CartItemRequest(@NotNull Long dishId, @Min(1) int quantity,
                                  List<Long> optionItemIds) {}

    public record PlaceOrderRequest(@NotBlank String deliveryDate, @NotBlank String mealPeriod,
                                    @NotBlank @Size(max = 200) String address,
                                    @NotEmpty @Valid List<CartItemRequest> items) {}

    public record OrderItemView(Long dishId, String dishName, int quantity, long unitCents,
                                long subtotalCents, List<String> optionLabels) {}

    public record OrderView(Long id, String orderNo, String deliveryDate, String mealPeriod,
                            String address, long totalCents, String status, String createdAt,
                            List<OrderItemView> items, String notice) {}

    public record ActiveOrderView(boolean exists, Long orderId, String orderNo,
                                  String resolvedDate, String resolvedMealPeriod, String notice) {}

    // ---- console ----
    public record ConsoleDishRequest(@NotBlank @Size(max = 120) String name,
                                     @Size(max = 500) String description,
                                     @NotNull @Min(0) Long priceCents,
                                     String category, String protein, List<String> allergens,
                                     String spiceLevel, String imageUrl, Boolean active,
                                     List<OptionGroupRequest> optionGroups) {}

    public record OptionGroupRequest(String name, boolean required, int maxSelect,
                                     List<OptionItemRequest> items) {}

    public record OptionItemRequest(String name, long extraCents) {}

    public record ActiveRequest(boolean active) {}

    public record ConsoleDishPage(List<DishView> items, long totalElements, int totalPages,
                                  int page, int size) {}

    public record DailyMenuEntryRequest(@NotNull Long dishId, @Min(0) int totalQty) {}

    public record DailyMenuRequest(@NotBlank String date, List<DailyMenuEntryRequest> entries) {}

    public record DailyMenuItemView(Long dishId, String name, String category, int totalQty,
                                    int remainingQty) {}

    public record DailyMenuView(String date, List<DailyMenuItemView> entries) {}

    public record DashboardView(Today today, List<TopDish> topDishes, MealSplit mealSplit,
                                List<TrendPoint> trend, List<LowStock> lowStock,
                                String generatedAt) {
        public record Today(long orders, long revenueCents, long pending, long confirmed,
                            long completed, long cancelled) {}
        public record TopDish(String name, long quantity, long revenueCents) {}
        public record MealSplit(long lunch, long dinner) {}
        public record TrendPoint(String date, long orders, long revenueCents) {}
        public record LowStock(Long dishId, String name, int remaining) {}
    }

    // ---- AI ----
    public record AiRequest(@NotBlank @Size(max = 500) String query) {}

    public record AiDishView(Long dishId, String name, String reason, long priceCents,
                             String imageUrl, String category, int remaining) {}

    public record UploadView(String url) {}
}
