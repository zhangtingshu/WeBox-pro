package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.AppClock;
import com.webox.common.Constants;
import com.webox.common.ErrorCode;
import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.dto.Dtos.PreferenceRequest;
import com.webox.dto.Dtos.PreferenceView;
import com.webox.entity.PreferenceEntity;
import com.webox.repository.PreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PreferenceService {

    private final PreferenceRepository repository;
    private final MenuCache menuCache;

    public PreferenceService(PreferenceRepository repository, MenuCache menuCache) {
        this.repository = repository;
        this.menuCache = menuCache;
    }

    public PreferenceEntity getOrCreate(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            PreferenceEntity p = new PreferenceEntity();
            p.setUserId(userId);
            p.setUpdatedAt(AppClock.now());
            return repository.save(p);
        });
    }

    public PreferenceView get(Long userId) {
        return toView(getOrCreate(userId));
    }

    @Transactional
    public PreferenceView update(Long userId, PreferenceRequest request) {
        PreferenceEntity entity = getOrCreate(userId);
        List<String> allergens = TextUtil.whitelist(request.allergens(), Constants.ALLERGEN_SET);
        List<String> cuisines = TextUtil.whitelist(request.cuisines(), Constants.CATEGORY_SET);
        String spice = Constants.SPICE_SET.contains(request.spiceLevel())
                ? request.spiceLevel() : "None";
        String taste = Constants.TASTE_SET.contains(request.taste())
                ? request.taste() : "Balanced";

        long min = request.budgetMinCents() == null ? 0L : Math.max(0L, request.budgetMinCents());
        long max = request.budgetMaxCents() == null ? 0L : Math.max(0L, request.budgetMaxCents());
        if (max > 0 && min > max) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The minimum budget cannot be greater than the maximum budget.");
        }

        entity.setAllergens(TextUtil.joinCsv(allergens));
        entity.setCuisines(TextUtil.joinCsv(cuisines));
        entity.setSpiceLevel(spice);
        entity.setTaste(taste);
        entity.setBudgetMin(Money.toDecimal(min));
        entity.setBudgetMax(Money.toDecimal(max));
        entity.setRecommendEnabled(Boolean.TRUE.equals(request.recommendEnabled()));
        entity.setUpdatedAt(AppClock.now());
        repository.save(entity);
        // Preference changes affect menu ordering, so cached pages must be dropped.
        menuCache.invalidateAll();
        return toView(entity);
    }

    public static PreferenceView toView(PreferenceEntity p) {
        return new PreferenceView(
                TextUtil.splitCsv(p.getAllergens()),
                TextUtil.splitCsv(p.getCuisines()),
                p.getSpiceLevel(), p.getTaste(),
                Money.toCents(p.getBudgetMin()), Money.toCents(p.getBudgetMax()),
                p.isRecommendEnabled());
    }
}
