
package grocery.strategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;

public class FewestStoresStrat implements OptimizationStrat {

    private final ProductSearchService searchService;
    private final OptimizationSupport support;

    public FewestStoresStrat(ProductSearchService searchService,
                            PromotionService promotionService,
                            boolean hasMembership,
                            LocalDate date,
                            UserPreferences preferences) {

        this.searchService = searchService;
        this.support = new OptimizationSupport(
            searchService, promotionService, hasMembership, date, preferences
        );
    }

    @Override
    public ShoppingPlan optimize(ShoppingPlan plan) {

        Set<String> storeNames = new HashSet<>();

        for (ShoppingItem item : plan.getShoppingItems()) {
            for (Product product : support.findOptions(item, null)) {
                storeNames.add(product.getStore());
            }
        }

        List<String> stores = new ArrayList<>(storeNames);
        stores.sort(String::compareTo);

        ShoppingPlan bestPlan = null;
        int bestStoreCount = Integer.MAX_VALUE;
        double bestTotal = Double.POSITIVE_INFINITY;

        // Intended for the small sample catalogue of 3 stores.
        if (stores.size() > 20) {
            throw new IllegalStateException("Too many store combinations");
        }

        for (int mask = 1; mask < (1 << stores.size()); mask++) {

            Set<String> selectedStores = new HashSet<>();

            for (int i = 0; i < stores.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    selectedStores.add(stores.get(i));
                }
            }

            try {
                ShoppingPlan candidate =
                    support.buildPlan(plan, selectedStores);

                int count = selectedStores.size();
                double total = support.effectiveTotal(candidate);

                if (count < bestStoreCount ||
                    (count == bestStoreCount && total < bestTotal)) {

                    bestPlan = candidate;
                    bestStoreCount = count;
                    bestTotal = total;
                }

            } catch (IllegalStateException ex) {
                // This combination cannot fulfill every item.
            }
        }

        if (bestPlan == null) {
            throw new IllegalStateException(
                "No store combination can fulfill the entire plan"
            );
        }

        return bestPlan;
    }
}
