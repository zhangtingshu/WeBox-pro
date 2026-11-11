package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.ErrorCode;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.dto.Dtos.OptionItemView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-side option validation + price calculation. Client prices are never trusted. */
public final class PricingService {
    private PricingService() {}

    public record PricedLine(long unitCents, List<String> optionLabels) {}

    public static PricedLine price(long baseCents, List<Long> selectedIds,
                                   List<OptionGroupView> groups) {
        List<Long> ids = selectedIds == null ? List.of() : selectedIds;
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The same customization option cannot be selected twice.");
        }

        Map<Long, OptionItemView> itemById = new HashMap<>();
        for (OptionGroupView group : groups) {
            for (OptionItemView item : group.items()) itemById.put(item.id(), item);
        }
        for (Long id : ids) {
            if (!itemById.containsKey(id)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Invalid customization option for this dish.");
            }
        }

        Map<Long, Integer> countByGroup = new HashMap<>();
        for (OptionGroupView group : groups) {
            for (OptionItemView item : group.items()) {
                if (unique.contains(item.id())) {
                    countByGroup.merge(group.id(), 1, Integer::sum);
                }
            }
        }
        for (OptionGroupView group : groups) {
            int selected = countByGroup.getOrDefault(group.id(), 0);
            if (group.required() && selected == 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Please choose " + group.name() + " before adding this dish.");
            }
            int max = Math.max(1, group.maxSelect());
            if (selected > max) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "You can select at most " + max + " option(s) for " + group.name() + ".");
            }
        }

        long extra = 0L;
        List<String> labels = new ArrayList<>();
        for (OptionGroupView group : groups) {
            for (OptionItemView item : group.items()) {
                if (unique.contains(item.id())) {
                    extra = Math.addExact(extra, item.extraCents());
                    labels.add(group.name() + ": " + item.name());
                }
            }
        }
        return new PricedLine(Math.addExact(baseCents, extra), labels);
    }
}
