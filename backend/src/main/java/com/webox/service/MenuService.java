package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.AppClock;
import com.webox.common.Constants;
import com.webox.common.ErrorCode;
import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.dto.Dtos.DishView;
import com.webox.dto.Dtos.MenuItemView;
import com.webox.dto.Dtos.MenuPageView;
import com.webox.dto.Dtos.MetaView;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.entity.DailyMenuEntity;
import com.webox.entity.DishEntity;
import com.webox.entity.PreferenceEntity;
import com.webox.repository.DailyMenuRepository;
import com.webox.repository.DishRepository;
import com.webox.repository.PreferenceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MenuService {

    private final DishRepository dishRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final PreferenceRepository preferenceRepository;
    private final DishAssembler assembler;
    private final MenuCache cache;

    public MenuService(DishRepository dishRepository, DailyMenuRepository dailyMenuRepository,
                       PreferenceRepository preferenceRepository, DishAssembler assembler,
                       MenuCache cache) {
        this.dishRepository = dishRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.preferenceRepository = preferenceRepository;
        this.assembler = assembler;
        this.cache = cache;
    }

    public MenuPageView menu(String categoriesCsv, String keyword, Boolean recommendParam,
                             int page, int size, LocalDate date, Long userId) {
        int safeSize = Math.min(Math.max(size, 1), Constants.MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        LocalDate menuDate = date == null ? AppClock.today() : date;
        String kw = TextUtil.clean(keyword, Constants.MAX_SEARCH_LENGTH);
        List<String> categories =
                TextUtil.whitelist(TextUtil.splitCsv(categoriesCsv), Constants.CATEGORY_SET);
        PreferenceEntity preference = preferenceRepository.findById(userId).orElse(null);
        boolean recommend = recommendParam != null
                ? recommendParam
                : preference != null && preference.isRecommendEnabled();

        String cacheKey = String.join("|", menuDate.toString(), String.join(",", categories),
                kw, String.valueOf(recommend), userId.toString(),
                String.valueOf(safePage), String.valueOf(safeSize));
        Object cached = cache.get(cacheKey);
        if (cached instanceof MenuPageView view) return view;

        List<DailyMenuEntity> rows = dailyMenuRepository.findByMenuDate(menuDate);
        Map<Long, Integer> stock = new HashMap<>();
        for (DailyMenuEntity row : rows) stock.put(row.getDishId(), row.getRemainingQty());

        LocalTime now = AppClock.time();
        OrderRules.Resolved defaults =
                OrderRules.resolve(AppClock.today(), now, AppClock.today(), "Lunch");

        if (stock.isEmpty()) {
            MenuPageView empty = new MenuPageView(List.of(), 0, 0, safePage, safeSize,
                    menuDate.toString(), defaults.mealPeriod(), defaults.deliveryDate().toString(),
                    "No menu has been published for this date yet.", recommend);
            cache.put(cacheKey, empty);
            return empty;
        }

        Specification<DishEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(root.get("id").in(stock.keySet()));
            if (!kw.isBlank()) {
                String pattern = "%" + TextUtil.escapeLike(kw.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (!categories.isEmpty()) {
                predicates.add(root.get("category").in(categories));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<DishEntity> dishes;
        long total;
        if (recommend) {
            // Rank globally against preferences before paging, so preferred cuisines really lead.
            List<DishEntity> ranked = new ArrayList<>(dishRepository.findAll(spec, Sort.by("id")));
            Map<Long, Integer> scores = new HashMap<>();
            for (DishEntity d : ranked) {
                scores.put(d.getId(), score(d, preference));
            }
            ranked.sort(Comparator.<DishEntity>comparingInt(d -> scores.getOrDefault(d.getId(), 0))
                    .reversed().thenComparing(DishEntity::getId));
            total = ranked.size();
            int from = (int) Math.min((long) safePage * safeSize, ranked.size());
            int to = Math.min(from + safeSize, ranked.size());
            dishes = new ArrayList<>(ranked.subList(from, to));
        } else {
            Page<DishEntity> result = dishRepository.findAll(spec,
                    PageRequest.of(safePage, safeSize, Sort.by("id")));
            dishes = result.getContent();
            total = result.getTotalElements();
        }

        Map<Long, List<OptionGroupView>> groups =
                assembler.groupsFor(dishes.stream().map(DishEntity::getId).toList());
        List<MenuItemView> items = new ArrayList<>();
        for (DishEntity dish : dishes) {
            int remaining = stock.getOrDefault(dish.getId(), 0);
            int matchScore = recommend ? score(dish, preference) : 0;
            DishView view = assembler.toView(dish, groups.getOrDefault(dish.getId(), List.of()));
            items.add(new MenuItemView(view, remaining,
                    remaining > 0 && remaining <= Constants.LOW_STOCK_THRESHOLD,
                    remaining <= 0,
                    recommend && matchScore >= 5,
                    matchScore));
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        MenuPageView result = new MenuPageView(items, total, totalPages, safePage, safeSize,
                menuDate.toString(), defaults.mealPeriod(), defaults.deliveryDate().toString(),
                defaults.notice(), recommend);
        cache.put(cacheKey, result);
        return result;
    }

    private int score(DishEntity dish, PreferenceEntity preference) {
        if (preference == null) return 0;
        int value = 0;
        List<String> cuisines = TextUtil.splitCsv(preference.getCuisines());
        List<String> flagged = TextUtil.splitCsv(preference.getAllergens());
        if (cuisines.contains(dish.getCategory())) value += 5;
        if (preference.getSpiceLevel().equals(dish.getSpiceLevel())) value += 3;
        if (!flagged.isEmpty() && AllergenMatcher.conflicts(
                TextUtil.splitCsv(dish.getAllergens()), flagged).isEmpty()) value += 2;
        long budgetMax = Money.toCents(preference.getBudgetMax());
        if (budgetMax > 0 && Money.toCents(dish.getPrice()) <= budgetMax) value += 1;
        return value;
    }

    public DishView detail(Long dishId) {
        DishEntity dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Dish not found."));
        if (!dish.isActive()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "This dish is currently unavailable.");
        }
        return assembler.toView(dish);
    }

    public static MetaView meta() {
        return new MetaView(Constants.CATEGORIES, Constants.ALLERGENS, Constants.SPICE_LEVELS,
                Constants.TASTE_LEVELS, Constants.MEAL_PERIODS, Constants.ORDER_STATUSES,
                Constants.MAX_PORTIONS_PER_ORDER, Constants.LOW_STOCK_THRESHOLD,
                Constants.LUNCH_CUTOFF.toString(), Constants.DINNER_CUTOFF.toString());
    }
}
