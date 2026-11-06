package grocery.strategy;

import java.time.LocalDate;
import java.util.Set;

import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;

public class OneStoreStrat implements OptimizationStrat {

    private final OptimizationSupport support;
    private final String selectedStore;

    public OneStoreStrat(ProductSearchService searchService,
                         PromotionService promotionService,
                         boolean hasMembership,
                         LocalDate date,
                         String selectedStore,
                         UserPreferences preferences) {

        this.support = new OptimizationSupport(
            searchService, promotionService, hasMembership, date, preferences
        );
        this.selectedStore = selectedStore;
    }

    @Override
    public ShoppingPlan optimize(ShoppingPlan plan) {
        return support.buildPlan(plan, Set.of(selectedStore));
    }
}
