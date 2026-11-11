package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.AppClock;
import com.webox.common.Constants;
import com.webox.common.ErrorCode;
import com.webox.common.JsonLog;
import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.dto.Dtos.ConsoleDishPage;
import com.webox.dto.Dtos.ConsoleDishRequest;
import com.webox.dto.Dtos.DailyMenuItemView;
import com.webox.dto.Dtos.DailyMenuRequest;
import com.webox.dto.Dtos.DailyMenuView;
import com.webox.dto.Dtos.DishView;
import com.webox.dto.Dtos.OptionGroupRequest;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.dto.Dtos.OptionItemRequest;
import com.webox.entity.DailyMenuEntity;
import com.webox.entity.DishEntity;
import com.webox.entity.OptionGroupEntity;
import com.webox.entity.OptionItemEntity;
import com.webox.repository.DailyMenuRepository;
import com.webox.repository.DishRepository;
import com.webox.repository.OptionGroupRepository;
import com.webox.repository.OptionItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConsoleService {

    private final DishRepository dishRepository;
    private final OptionGroupRepository groupRepository;
    private final OptionItemRepository itemRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final DishAssembler assembler;
    private final MenuCache menuCache;
    private final SseHub sseHub;

    public ConsoleService(DishRepository dishRepository, OptionGroupRepository groupRepository,
                          OptionItemRepository itemRepository,
                          DailyMenuRepository dailyMenuRepository, DishAssembler assembler,
                          MenuCache menuCache, SseHub sseHub) {
        this.dishRepository = dishRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.assembler = assembler;
        this.menuCache = menuCache;
        this.sseHub = sseHub;
    }

    public ConsoleDishPage list(String keyword, String category, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), Constants.MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        String kw = TextUtil.clean(keyword, Constants.MAX_SEARCH_LENGTH);
        Specification<DishEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!kw.isBlank()) {
                String pattern = "%" + TextUtil.escapeLike(kw.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }
            if (category != null && Constants.CATEGORY_SET.contains(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<DishEntity> result = dishRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by("id")));
        Map<Long, List<OptionGroupView>> groups =
                assembler.groupsFor(result.getContent().stream().map(DishEntity::getId).toList());
        List<DishView> views = result.getContent().stream()
                .map(d -> assembler.toView(d, groups.getOrDefault(d.getId(), List.of())))
                .toList();
        return new ConsoleDishPage(views, result.getTotalElements(), result.getTotalPages(),
                safePage, safeSize);
    }

    @Transactional
    public DishView create(ConsoleDishRequest request) {
        DishEntity dish = new DishEntity();
        apply(dish, request);
        dish.setCreatedAt(AppClock.now());
        dish.setUpdatedAt(AppClock.now());
        dish = dishRepository.saveAndFlush(dish);
        replaceOptions(dish.getId(), request.optionGroups());
        afterWrite("dish_created", dish.getId());
        return assembler.toView(dish);
    }

    @Transactional
    public DishView update(Long dishId, ConsoleDishRequest request) {
        DishEntity dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Dish not found."));
        apply(dish, request);
        if (request.active() != null) dish.setActive(request.active());
        dish.setUpdatedAt(AppClock.now());
        dishRepository.save(dish);
        replaceOptions(dishId, request.optionGroups());
        afterWrite("dish_updated", dishId);
        return assembler.toView(dish);
    }

    @Transactional
    public DishView setActive(Long dishId, boolean active) {
        DishEntity dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Dish not found."));
        dish.setActive(active);
        dish.setUpdatedAt(AppClock.now());
        dishRepository.save(dish);
        afterWrite("dish_availability_changed", dishId);
        return assembler.toView(dish);
    }

    public DailyMenuView dailyMenu(String date) {
        LocalDate menuDate;
        try {
            menuDate = date == null || date.isBlank()
                    ? AppClock.today().plusDays(1) : LocalDate.parse(date);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please select a valid menu date.");
        }
        return buildMenuView(menuDate);
    }

    @Transactional
    public DailyMenuView setDailyMenu(DailyMenuRequest request) {
        LocalDate date;
        try {
            date = LocalDate.parse(request.date());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please select a valid menu date.");
        }
        List<DailyMenuEntity> existing = dailyMenuRepository.findByMenuDate(date);
        Map<Long, DailyMenuEntity> byDish = existing.stream()
                .collect(Collectors.toMap(DailyMenuEntity::getDishId, Function.identity()));
        Set<Long> selected = new HashSet<>();

        for (var entry : request.entries() == null ? List.<com.webox.dto.Dtos.DailyMenuEntryRequest>of()
                : request.entries()) {
            DishEntity dish = dishRepository.findById(entry.dishId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Dish not found."));
            selected.add(entry.dishId());
            int newTotal = Math.min(Math.max(entry.totalQty(), 0), 9999);
            DailyMenuEntity row = byDish.get(entry.dishId());
            if (row == null) {
                row = new DailyMenuEntity();
                row.setMenuDate(date);
                row.setDishId(entry.dishId());
                row.setTotalQty(newTotal);
                row.setRemainingQty(newTotal);
                row.setVersion(0);
            } else {
                int sold = Math.max(0, row.getTotalQty() - row.getRemainingQty());
                row.setTotalQty(newTotal);
                row.setRemainingQty(Math.max(0, newTotal - sold));
                row.setVersion(row.getVersion() + 1);
            }
            dailyMenuRepository.save(row);
        }
        // Remove untouched dishes from this day's menu (rows with sales are preserved).
        for (DailyMenuEntity row : existing) {
            if (!selected.contains(row.getDishId())
                    && row.getRemainingQty() == row.getTotalQty()) {
                dailyMenuRepository.delete(row);
            }
        }
        afterWrite("daily_menu_updated", null);
        JsonLog.info("daily_menu_updated", "date", date.toString(), "dishes", selected.size());
        return buildMenuView(date);
    }

    private DailyMenuView buildMenuView(LocalDate date) {
        List<DailyMenuEntity> rows = dailyMenuRepository.findByMenuDate(date);
        Map<Long, DishEntity> dishes = dishRepository.findAllById(
                        rows.stream().map(DailyMenuEntity::getDishId).toList()).stream()
                .collect(Collectors.toMap(DishEntity::getId, Function.identity()));
        List<DailyMenuItemView> entries = rows.stream()
                .map(row -> {
                    DishEntity dish = dishes.get(row.getDishId());
                    return new DailyMenuItemView(row.getDishId(),
                            dish == null ? "Unknown dish" : dish.getName(),
                            dish == null ? "" : dish.getCategory(),
                            row.getTotalQty(), row.getRemainingQty());
                })
                .sorted(Comparator.comparing(DailyMenuItemView::name))
                .toList();
        return new DailyMenuView(date.toString(), entries);
    }

    private void apply(DishEntity dish, ConsoleDishRequest request) {
        String name = TextUtil.clean(request.name(), 120);
        if (name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Dish name is required.");
        }
        long priceCents = request.priceCents() == null ? 0L : Math.max(0L, request.priceCents());
        String category = Constants.CATEGORY_SET.contains(request.category())
                ? request.category() : "Chinese";
        String spice = Constants.SPICE_SET.contains(request.spiceLevel())
                ? request.spiceLevel() : "None";
        List<String> allergens = TextUtil.whitelist(request.allergens(), Constants.ALLERGEN_SET);

        dish.setName(name);
        dish.setDescription(TextUtil.clean(request.description(), 500));
        dish.setPrice(Money.toDecimal(priceCents));
        dish.setCategory(category);
        dish.setProtein(TextUtil.clean(request.protein(), 80));
        dish.setAllergens(TextUtil.joinCsv(allergens));
        dish.setSpiceLevel(spice);
        dish.setImageUrl(TextUtil.clean(request.imageUrl(), 300));
        dish.setActive(request.active() == null || request.active());
    }

    private void replaceOptions(Long dishId, List<OptionGroupRequest> requests) {
        List<OptionGroupEntity> oldGroups = groupRepository.findByDishIdOrderBySortOrderAsc(dishId);
        if (!oldGroups.isEmpty()) {
            itemRepository.deleteByGroupIdIn(oldGroups.stream().map(OptionGroupEntity::getId).toList());
            groupRepository.deleteByDishId(dishId);
        }
        if (requests == null || requests.isEmpty()) return;
        int groupOrder = 1;
        for (OptionGroupRequest request : requests) {
            OptionGroupEntity group = new OptionGroupEntity();
            group.setDishId(dishId);
            group.setName(TextUtil.clean(request.name(), 80));
            group.setRequired(request.required());
            group.setMaxSelect(Math.max(1, Math.min(request.maxSelect(), 10)));
            group.setSortOrder(groupOrder++);
            group = groupRepository.saveAndFlush(group);
            int itemOrder = 1;
            if (request.items() != null) {
                for (OptionItemRequest item : request.items()) {
                    OptionItemEntity entity = new OptionItemEntity();
                    entity.setGroupId(group.getId());
                    entity.setName(TextUtil.clean(item.name(), 80));
                    entity.setExtraPrice(Money.toDecimal(Math.max(0L, item.extraCents())));
                    entity.setSortOrder(itemOrder++);
                    itemRepository.save(entity);
                }
            }
        }
    }

    private void afterWrite(String event, Long dishId) {
        menuCache.invalidateAll();
        sseHub.broadcastRefresh();
        if (dishId != null) JsonLog.info(event, "dishId", dishId);
    }
}
