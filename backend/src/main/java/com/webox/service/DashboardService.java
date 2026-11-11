package com.webox.service;

import com.webox.common.AppClock;
import com.webox.common.Constants;
import com.webox.common.Money;
import com.webox.dto.Dtos.DashboardView;
import com.webox.repository.DailyMenuRepository;
import com.webox.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All figures are aggregated from real orders/order_items, never hard-coded. */
@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final DailyMenuRepository dailyMenuRepository;

    public DashboardService(OrderRepository orderRepository,
                            DailyMenuRepository dailyMenuRepository) {
        this.orderRepository = orderRepository;
        this.dailyMenuRepository = dailyMenuRepository;
    }

    public DashboardView dashboard() {
        LocalDate today = AppClock.today();
        LocalDate from = today.minusDays(6);

        long orders = 0L;
        long revenueCents = 0L;
        List<Object[]> totals = orderRepository.todayTotals(today);
        if (!totals.isEmpty()) {
            orders = number(totals.get(0)[0]);
            revenueCents = cents(totals.get(0)[1]);
        }

        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : orderRepository.statusCounts(today)) {
            statusCounts.put(String.valueOf(row[0]), number(row[1]));
        }
        DashboardView.Today todayView = new DashboardView.Today(orders, revenueCents,
                statusCounts.getOrDefault("Pending", 0L),
                statusCounts.getOrDefault("Confirmed", 0L),
                statusCounts.getOrDefault("Completed", 0L),
                statusCounts.getOrDefault("Cancelled", 0L));

        List<DashboardView.TopDish> topDishes = new ArrayList<>();
        for (Object[] row : orderRepository.topDishes(from, today)) {
            topDishes.add(new DashboardView.TopDish(String.valueOf(row[0]),
                    number(row[1]), cents(row[2])));
        }

        long lunch = 0L, dinner = 0L;
        for (Object[] row : orderRepository.mealDistribution(today)) {
            if ("Lunch".equals(String.valueOf(row[0]))) lunch = number(row[1]);
            if ("Dinner".equals(String.valueOf(row[0]))) dinner = number(row[1]);
        }

        Map<String, long[]> byDay = new HashMap<>();
        for (Object[] row : orderRepository.trend(from, today)) {
            String date = dateString(row[0]);
            byDay.put(date, new long[]{number(row[1]), cents(row[2])});
        }
        List<DashboardView.TrendPoint> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            long[] values = byDay.getOrDefault(date, new long[]{0L, 0L});
            trend.add(new DashboardView.TrendPoint(date, values[0], values[1]));
        }

        List<DashboardView.LowStock> lowStock = new ArrayList<>();
        for (Object[] row : dailyMenuRepository.lowStock(today, Constants.LOW_STOCK_THRESHOLD)) {
            lowStock.add(new DashboardView.LowStock(number(row[0]),
                    String.valueOf(row[1]), (int) number(row[2])));
        }

        return new DashboardView(todayView, topDishes,
                new DashboardView.MealSplit(lunch, dinner), trend, lowStock,
                AppClock.now().toString());
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static long cents(Object value) {
        if (value == null) return 0L;
        return Money.toCents(new BigDecimal(value.toString()));
    }

    private static String dateString(Object value) {
        if (value instanceof Date date) return date.toLocalDate().toString();
        if (value instanceof LocalDate localDate) return localDate.toString();
        return String.valueOf(value).substring(0, Math.min(10, String.valueOf(value).length()));
    }
}
