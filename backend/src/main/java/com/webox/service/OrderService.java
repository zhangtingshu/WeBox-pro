package com.webox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webox.common.ApiException;
import com.webox.common.AppClock;
import com.webox.common.Constants;
import com.webox.common.ErrorCode;
import com.webox.common.JsonLog;
import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.dto.Dtos.ActiveOrderView;
import com.webox.dto.Dtos.CartItemRequest;
import com.webox.dto.Dtos.OrderItemView;
import com.webox.dto.Dtos.OrderView;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.dto.Dtos.PlaceOrderRequest;
import com.webox.dto.Dtos.StockEvent;
import com.webox.entity.DailyMenuEntity;
import com.webox.entity.DishEntity;
import com.webox.entity.OrderEntity;
import com.webox.entity.OrderItemEntity;
import com.webox.entity.PreferenceEntity;
import com.webox.repository.DailyMenuRepository;
import com.webox.repository.DishRepository;
import com.webox.repository.OrderItemRepository;
import com.webox.repository.OrderRepository;
import com.webox.repository.PreferenceRepository;
import com.webox.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private record CreateResult(OrderView view, List<StockEvent> events) {}

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DishRepository dishRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final PreferenceRepository preferenceRepository;
    private final DishAssembler assembler;
    private final MenuCache menuCache;
    private final SseHub sseHub;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderService(UserRepository userRepository, OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository, DishRepository dishRepository,
                        DailyMenuRepository dailyMenuRepository,
                        PreferenceRepository preferenceRepository, DishAssembler assembler,
                        MenuCache menuCache, SseHub sseHub,
                        PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.dishRepository = dishRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.preferenceRepository = preferenceRepository;
        this.assembler = assembler;
        this.menuCache = menuCache;
        this.sseHub = sseHub;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Idempotent submit. The client sends a stable Idempotency-Key for one checkout attempt;
     * the DB unique index is the final guard for simultaneous duplicate requests.
     */
    public OrderView place(Long userId, PlaceOrderRequest request, String rawIdempotencyKey) {
        String suppliedKey = TextUtil.clean(rawIdempotencyKey, 100);
        final String key = suppliedKey.isBlank() ? UUID.randomUUID().toString() : suppliedKey;

        OrderEntity existing = orderRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            JsonLog.info("order_idempotent_hit", "orderNo", existing.getOrderNo());
            return toView(existing, "This order was already submitted. Showing the existing order.");
        }

        try {
            CreateResult result = transactionTemplate.execute(status -> create(userId, request, key));
            // Post-commit side effects: cache invalidation + real-time stock push.
            menuCache.invalidateAll();
            sseHub.broadcast(result.events());
            return result.view();
        } catch (DataIntegrityViolationException e) {
            // A concurrent request won the unique-key race: return THAT order instead of erroring.
            OrderEntity again = orderRepository.findByIdempotencyKey(key).orElse(null);
            if (again != null) {
                JsonLog.info("order_idempotent_race", "orderNo", again.getOrderNo());
                return toView(again, "This order was already submitted. Showing the existing order.");
            }
            throw e;
        }
    }

    private CreateResult create(Long userId, PlaceOrderRequest request, String key) {
        LocalDateTime now = AppClock.now();
        LocalDate today = AppClock.today();

        // Row lock on the employee serialises concurrent submissions from the SAME account.
        userRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account not found."));

        String address = TextUtil.clean(request.address(), Constants.MAX_ADDRESS_LENGTH);
        if (address.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please enter a delivery address.");
        }
        LocalDate requestedDate = parseDate(request.deliveryDate(), today);
        OrderRules.Resolved resolved = OrderRules.resolve(
                today, now.toLocalTime(), requestedDate, request.mealPeriod());

        orderRepository.findFirstByUserIdAndDeliveryDateAndMealPeriodAndStatusInOrderByCreatedAtDesc(
                        userId, resolved.deliveryDate(), resolved.mealPeriod(), Constants.ACTIVE_STATUSES)
                .ifPresent(order -> {
                    throw new ApiException(ErrorCode.DUPLICATE_ORDER,
                            "You already have an active " + resolved.mealPeriod() + " order for "
                                    + resolved.deliveryDate() + ". View Existing Order.");
                });

        List<CartItemRequest> lines = request.items();
        int totalPortions = 0;
        for (CartItemRequest line : lines) {
            if (line.quantity() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Item quantity must be positive.");
            }
            totalPortions = Math.addExact(totalPortions, line.quantity());
        }
        if (OrderRules.exceedsPortionLimit(totalPortions)) {
            throw new ApiException(ErrorCode.RULE_VIOLATION, OrderRules.portionLimitMessage());
        }

        Map<Long, DailyMenuEntity> menuRows = dailyMenuRepository
                .findByMenuDate(resolved.deliveryDate()).stream()
                .collect(Collectors.toMap(DailyMenuEntity::getDishId, Function.identity()));
        if (menuRows.isEmpty()) {
            throw new ApiException(ErrorCode.RULE_VIOLATION,
                    "No menu has been published for " + resolved.deliveryDate() + ".");
        }

        List<Long> dishIds = lines.stream().map(CartItemRequest::dishId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().toList();
        Map<Long, DishEntity> dishMap = dishRepository.findAllById(dishIds).stream()
                .collect(Collectors.toMap(DishEntity::getId, Function.identity()));
        for (Long dishId : dishIds) {
            DishEntity dish = dishMap.get(dishId);
            if (dish == null || !dish.isActive()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "A dish in your cart is no longer available. Please refresh the menu.");
            }
            if (!menuRows.containsKey(dishId)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        dish.getName() + " is not on the menu for " + resolved.deliveryDate() + ".");
            }
        }
        Map<Long, List<OptionGroupView>> groups = assembler.groupsFor(dishIds);

        List<OrderItemEntity> orderItems = new ArrayList<>();
        Map<Long, Integer> quantityByDish = new TreeMap<>();
        long totalCents = 0L;
        for (CartItemRequest line : lines) {
            DishEntity dish = dishMap.get(line.dishId());
            PricingService.PricedLine priced = PricingService.price(
                    Money.toCents(dish.getPrice()), line.optionItemIds(),
                    groups.getOrDefault(line.dishId(), List.of()));
            long subtotal = Money.price(priced.unitCents(), line.quantity());
            totalCents = Math.addExact(totalCents, subtotal);

            OrderItemEntity item = new OrderItemEntity();
            item.setDishId(dish.getId());
            item.setDishName(dish.getName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(Money.toDecimal(priced.unitCents()));
            item.setSubtotal(Money.toDecimal(subtotal));
            item.setOptionsJson(writeJson(priced.optionLabels()));
            orderItems.add(item);
            quantityByDish.merge(dish.getId(), line.quantity(), Integer::sum);
        }

        // Atomic conditional UPDATE per dish: no overselling under concurrency.
        List<String> insufficient = new ArrayList<>();
        List<StockEvent> events = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityByDish.entrySet()) {
            int available = menuRows.get(entry.getKey()).getRemainingQty();
            int affected = dailyMenuRepository.deductStock(
                    resolved.deliveryDate(), entry.getKey(), entry.getValue());
            if (affected == 0) {
                // Re-read with a locking current read: `available` may be a stale snapshot
                // when another employee's transaction just consumed the stock.
                Object fresh = dailyMenuRepository.currentRemainingForUpdate(
                        resolved.deliveryDate(), entry.getKey());
                int availableNow = fresh instanceof Number number ? number.intValue() : 0;
                insufficient.add(dishMap.get(entry.getKey()).getName()
                        + " (requested " + entry.getValue() + ", only " + availableNow
                        + " available now)");
            } else {
                Object fresh = dailyMenuRepository.currentRemainingForUpdate(
                        resolved.deliveryDate(), entry.getKey());
                int remaining = fresh instanceof Number number ? number.intValue()
                        : Math.max(0, available - entry.getValue());
                events.add(new StockEvent(entry.getKey(), remaining,
                        resolved.deliveryDate().toString()));
            }
        }
        if (!insufficient.isEmpty()) {
            throw new ApiException(ErrorCode.STOCK_INSUFFICIENT,
                    "Insufficient stock for: " + String.join(", ", insufficient)
                            + ". Please adjust your cart and submit again.");
        }

        OrderEntity order = new OrderEntity();
        order.setOrderNo("WB" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + random6());
        order.setUserId(userId);
        order.setDeliveryDate(resolved.deliveryDate());
        order.setMealPeriod(resolved.mealPeriod());
        order.setAddress(address);
        order.setTotalAmount(Money.toDecimal(totalCents));
        order.setStatus("Pending");
        order.setIdempotencyKey(key);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);
        for (OrderItemEntity item : orderItems) {
            item.setOrderId(order.getId());
            orderItemRepository.save(item);
        }

        StringBuilder notice = new StringBuilder();
        if (!resolved.notice().isBlank()) notice.append(resolved.notice()).append(" ");
        PreferenceEntity preference = preferenceRepository.findById(userId).orElse(null);
        if (preference != null) {
            long budgetMax = Money.toCents(preference.getBudgetMax());
            if (budgetMax > 0 && totalCents > budgetMax) {
                notice.append("Your order exceeds your per-meal budget of ")
                        .append(Money.format(budgetMax)).append(".");
            }
        }
        JsonLog.info("order_created", "orderNo", order.getOrderNo(), "userId", userId,
                "totalCents", totalCents, "portions", totalPortions,
                "mealPeriod", resolved.mealPeriod(), "deliveryDate", resolved.deliveryDate().toString());
        return new CreateResult(toView(order, orderItems, notice.toString().trim()), events);
    }

    public List<OrderView> orders(Long userId) {
        List<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (orders.isEmpty()) return List.of();
        Map<Long, List<OrderItemEntity>> itemsByOrder = new HashMap<>();
        for (OrderItemEntity item : orderItemRepository.findByOrderIdIn(
                orders.stream().map(OrderEntity::getId).toList())) {
            itemsByOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        return orders.stream()
                .map(o -> toView(o, itemsByOrder.getOrDefault(o.getId(), List.of()), ""))
                .toList();
    }

    public OrderView detail(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));
        return toView(order, "");
    }

    @Transactional
    public OrderView cancel(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));
        if (!"Pending".equals(order.getStatus())) {
            throw new ApiException(ErrorCode.ORDER_STATE_CONFLICT,
                    "Only Pending orders can be cancelled.");
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        Map<Long, Integer> quantityByDish = new TreeMap<>();
        for (OrderItemEntity item : items) {
            quantityByDish.merge(item.getDishId(), item.getQuantity(), Integer::sum);
        }
        List<StockEvent> events = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityByDish.entrySet()) {
            dailyMenuRepository.restoreStock(order.getDeliveryDate(), entry.getKey(), entry.getValue());
            int remaining = dailyMenuRepository
                    .findByMenuDateAndDishId(order.getDeliveryDate(), entry.getKey())
                    .map(DailyMenuEntity::getRemainingQty).orElse(0);
            events.add(new StockEvent(entry.getKey(), remaining,
                    order.getDeliveryDate().toString()));
        }
        order.setStatus("Cancelled");
        order.setUpdatedAt(AppClock.now());
        orderRepository.save(order);

        menuCache.invalidateAll();
        sseHub.broadcast(events);
        JsonLog.info("order_cancelled", "orderNo", order.getOrderNo(), "userId", userId);
        return toView(order, items, "Your order has been cancelled and stock has been restored.");
    }

    /** Pre-check used by the checkout page to swap "Place Order" for "View Existing Order". */
    public ActiveOrderView active(Long userId, String date, String mealPeriod) {
        LocalDate today = AppClock.today();
        LocalDate requested = parseDate(date, today);
        OrderRules.Resolved resolved =
                OrderRules.resolve(today, AppClock.time(), requested, mealPeriod);
        var found = orderRepository
                .findFirstByUserIdAndDeliveryDateAndMealPeriodAndStatusInOrderByCreatedAtDesc(
                        userId, resolved.deliveryDate(), resolved.mealPeriod(),
                        Constants.ACTIVE_STATUSES);
        return new ActiveOrderView(found.isPresent(),
                found.map(OrderEntity::getId).orElse(null),
                found.map(OrderEntity::getOrderNo).orElse(null),
                resolved.deliveryDate().toString(), resolved.mealPeriod(), resolved.notice());
    }

    public List<String> historyAddresses(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderEntity::getAddress)
                .distinct()
                .limit(5)
                .toList();
    }

    private OrderView toView(OrderEntity order, String notice) {
        return toView(order, orderItemRepository.findByOrderId(order.getId()), notice);
    }

    private OrderView toView(OrderEntity order, List<OrderItemEntity> items, String notice) {
        List<OrderItemView> views = items.stream()
                .map(item -> new OrderItemView(item.getDishId(), item.getDishName(),
                        item.getQuantity(), Money.toCents(item.getUnitPrice()),
                        Money.toCents(item.getSubtotal()), readLabels(item.getOptionsJson())))
                .toList();
        return new OrderView(order.getId(), order.getOrderNo(),
                order.getDeliveryDate().toString(), order.getMealPeriod(), order.getAddress(),
                Money.toCents(order.getTotalAmount()), order.getStatus(),
                order.getCreatedAt().toString(), views, notice == null ? "" : notice);
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readLabels(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Please select a valid delivery date.");
        }
    }

    private static String random6() {
        return UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
