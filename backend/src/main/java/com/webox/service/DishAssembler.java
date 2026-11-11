package com.webox.service;

import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.dto.Dtos.DishView;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.dto.Dtos.OptionItemView;
import com.webox.entity.DishEntity;
import com.webox.entity.OptionGroupEntity;
import com.webox.entity.OptionItemEntity;
import com.webox.repository.OptionGroupRepository;
import com.webox.repository.OptionItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Assembles dish cards with two batched queries (no N+1 on option groups/items). */
@Service
public class DishAssembler {

    private final OptionGroupRepository groupRepository;
    private final OptionItemRepository itemRepository;

    public DishAssembler(OptionGroupRepository groupRepository, OptionItemRepository itemRepository) {
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
    }

    public Map<Long, List<OptionGroupView>> groupsFor(Collection<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) return Map.of();
        List<OptionGroupEntity> groups =
                groupRepository.findByDishIdInOrderByDishIdAscSortOrderAsc(dishIds);
        List<Long> groupIds = groups.stream().map(OptionGroupEntity::getId).toList();
        List<OptionItemEntity> items = groupIds.isEmpty() ? List.of()
                : itemRepository.findByGroupIdInOrderByGroupIdAscSortOrderAsc(groupIds);

        Map<Long, List<OptionItemView>> itemsByGroup = new HashMap<>();
        for (OptionItemEntity item : items) {
            itemsByGroup.computeIfAbsent(item.getGroupId(), k -> new ArrayList<>())
                    .add(new OptionItemView(item.getId(), item.getName(),
                            Money.toCents(item.getExtraPrice())));
        }
        Map<Long, List<OptionGroupView>> result = new HashMap<>();
        for (OptionGroupEntity group : groups) {
            result.computeIfAbsent(group.getDishId(), k -> new ArrayList<>())
                    .add(new OptionGroupView(group.getId(), group.getName(), group.isRequired(),
                            group.getMaxSelect(),
                            itemsByGroup.getOrDefault(group.getId(), List.of())));
        }
        return result;
    }

    public DishView toView(DishEntity dish, List<OptionGroupView> groups) {
        return new DishView(
                dish.getId(), dish.getName(), dish.getDescription(),
                Money.toCents(dish.getPrice()), dish.getCategory(), dish.getProtein(),
                TextUtil.splitCsv(dish.getAllergens()), dish.getSpiceLevel(), dish.getImageUrl(),
                dish.isActive(), groups == null ? List.of() : groups);
    }

    public DishView toView(DishEntity dish) {
        return toView(dish,
                groupsFor(List.of(dish.getId())).getOrDefault(dish.getId(), List.of()));
    }
}
