package com.webox;

import com.webox.common.ApiException;
import com.webox.dto.Dtos.OptionGroupView;
import com.webox.dto.Dtos.OptionItemView;
import com.webox.service.PricingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {
    private final List<OptionGroupView> burgerGroups = List.of(
            new OptionGroupView(6L, "Bun", true, 1, List.of(
                    new OptionItemView(15L, "Plain", 0L),
                    new OptionItemView(16L, "Whole Wheat", 0L))),
            new OptionGroupView(7L, "Sauce", true, 1, List.of(
                    new OptionItemView(17L, "Ketchup", 0L),
                    new OptionItemView(18L, "Mustard", 0L))),
            new OptionGroupView(8L, "Add-ons", false, 3, List.of(
                    new OptionItemView(21L, "Cheese", 300L),
                    new OptionItemView(22L, "Bacon", 500L))));

    @Test
    void selectedAddOnsUpdateUnitPriceInRealTime() {
        PricingService.PricedLine line = PricingService.price(
                3800L, List.of(16L, 18L, 21L, 22L), burgerGroups);
        assertEquals(4600L, line.unitCents());
        assertEquals(List.of("Bun: Whole Wheat", "Sauce: Mustard",
                "Add-ons: Cheese", "Add-ons: Bacon"), line.optionLabels());
    }

    @Test
    void requiredOptionGroupsMustBeFilled() {
        ApiException exception = assertThrows(ApiException.class,
                () -> PricingService.price(3800L, List.of(16L), burgerGroups));
        assertTrue(exception.getMessage().contains("Sauce"));
    }

    @Test
    void invalidAndDuplicateOptionsAreRejected() {
        assertThrows(ApiException.class,
                () -> PricingService.price(3800L, List.of(16L, 18L, 999L), burgerGroups));
        assertThrows(ApiException.class,
                () -> PricingService.price(3800L, List.of(16L, 16L, 18L), burgerGroups));
    }

    @Test
    void maxSelectIsEnforced() {
        List<OptionGroupView> singleChoice = List.of(
                new OptionGroupView(1L, "Base", false, 1, List.of(
                        new OptionItemView(1L, "Quinoa", 0L),
                        new OptionItemView(2L, "Brown Rice", 0L))));
        assertThrows(ApiException.class,
                () -> PricingService.price(1000L, List.of(1L, 2L), singleChoice));
    }
}
