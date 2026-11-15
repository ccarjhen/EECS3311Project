package grocery.strategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.util.StoreNameNormalizer;

public class FewestStoresStrat implements OptimizationStrat {

    private final OptimizationSupport support;

    private List<ShoppingItem> items;
    private List<List<Product>> candidates;
    private List<String> stores;
    private int[] coverage;
    private int[] suffixCoverage;
    private int targetCoverage;

    private ShoppingPlan bestPlan;
    private int bestStoreCount;
    private double bestCost;

    public FewestStoresStrat(
            ProductSearchService searchService,
            PromotionService promotionService,
            boolean hasMembership,
            LocalDate date,
            UserPreferences preferences) {

        this.support = new OptimizationSupport(
            searchService,
            promotionService,
            hasMembership,
            date,
            preferences
        );
    }

    @Override
    public ShoppingPlan optimize(ShoppingPlan plan) {

        if (plan == null) {
            throw new IllegalArgumentException(
                "Shopping plan cannot be null"
            );
        }

        items = new ArrayList<>(plan.getShoppingItems());
        candidates = new ArrayList<>();

        Map<String, Integer> storeCoverage = new TreeMap<>();

        // Retrieve eligible cached candidates once per item.
        for (int i = 0; i < items.size(); i++) {

            ShoppingItem item = items.get(i);

            List<Product> options =
                support.findOptions(item, null);

            candidates.add(options);

            for (Product product : options) {

                String store = normalize(product.getStore());

                int mask = storeCoverage.getOrDefault(store, 0);
                mask |= (1 << i);

                storeCoverage.put(store, mask);
            }
        }

        stores = new ArrayList<>(storeCoverage.keySet());
        coverage = new int[stores.size()];
        suffixCoverage = new int[stores.size() + 1];

        targetCoverage = 0;

        for (int i = 0; i < stores.size(); i++) {
            coverage[i] = storeCoverage.get(stores.get(i));
            targetCoverage |= coverage[i];
        }

        // Which items can still be covered by remaining stores?
        for (int i = stores.size() - 1; i >= 0; i--) {
            suffixCoverage[i] =
                suffixCoverage[i + 1] | coverage[i];
        }

        bestPlan = null;
        bestStoreCount = Integer.MAX_VALUE;
        bestCost = Double.POSITIVE_INFINITY;

        // Find a feasible upper bound quickly.
        Set<String> greedyStores = greedySelection();

        if (!greedyStores.isEmpty()) {
            evaluate(plan, greedyStores);
        }

        // Exact search, with coverage and store-count pruning.
        searchCombinations(
            plan,
            0,
            0,
            new HashSet<>()
        );

        // Even when no listings exist, retain requested items.
        if (bestPlan == null) {
            bestPlan = createPartialPlan(
                plan,
                Set.of()
            );
        }

        long missing = bestPlan.getShoppingItems()
            .stream()
            .filter(item -> item.getProduct() == null)
            .count();

        if (missing > 0) {
            System.out.printf(
                "[PLAN] Fewest Stores: partial plan, " +
                "%d item(s) unassigned.%n",
                missing
            );
        } else {
            System.out.println(
                "[PLAN] Fewest Stores: complete plan."
            );
        }

        return bestPlan;
    }

    private String normalize(String store) {
        return StoreNameNormalizer.normalize(store);
    }

    // Build a quick valid store set to limit the exact search.
    private Set<String> greedySelection() {

        Set<String> selected = new HashSet<>();
        int covered = 0;

        while (covered != targetCoverage) {

            int bestIndex = -1;
            int bestGain = 0;

            for (int i = 0; i < stores.size(); i++) {

                if (selected.contains(stores.get(i))) {
                    continue;
                }

                int gain = Integer.bitCount(
                    coverage[i] & targetCoverage & ~covered
                );

                if (gain > bestGain) {
                    bestGain = gain;
                    bestIndex = i;
                }
            }

            if (bestIndex < 0) {
                break;
            }

            selected.add(stores.get(bestIndex));
            covered |= coverage[bestIndex];
        }

        return selected;
    }

    private void searchCombinations(
            ShoppingPlan original,
            int index,
            int covered,
            Set<String> selected) {

        // All fulfillable items are covered.
        if ((covered & targetCoverage) == targetCoverage) {
            evaluate(original, selected);
            return;
        }

        if (index >= stores.size()) {
            return;
        }

        // Another store cannot improve the current best count.
        if (selected.size() >= bestStoreCount) {
            return;
        }

        // Remaining stores cannot cover all fulfillable items.
        if (((covered | suffixCoverage[index])
                & targetCoverage) != targetCoverage) {
            return;
        }

        // Include the next retailer.
        String store = stores.get(index);

        selected.add(store);

        searchCombinations(
            original,
            index + 1,
            covered | coverage[index],
            selected
        );

        selected.remove(store);

        // Exclude the next retailer.
        searchCombinations(
            original,
            index + 1,
            covered,
            selected
        );
    }

    private void evaluate(
            ShoppingPlan original,
            Set<String> selected) {

        ShoppingPlan candidate =
            createPartialPlan(original, selected);

        int usedStores = selected.size();
        double total = effectiveTotal(candidate);

        if (usedStores < bestStoreCount ||
                (usedStores == bestStoreCount &&
                 total < bestCost)) {

            bestStoreCount = usedStores;
            bestCost = total;
            bestPlan = candidate;
        }
    }

    private ShoppingPlan createPartialPlan(
            ShoppingPlan original,
            Set<String> allowedStores) {

        ShoppingPlan result = new ShoppingPlan(
            original.getName(),
            original.getEstimatedBudget()
        );

        for (int i = 0; i < items.size(); i++) {

            ShoppingItem item = items.get(i);

            ShoppingItem copy = new ShoppingItem(
                item.getName(),
                item.getCategory(),
                item.getQuantity()
            );

            copy.setPurchased(item.isPurchased());

            Product cheapest = null;
            double lowestCost = Double.POSITIVE_INFINITY;

            for (Product product : candidates.get(i)) {

                String store = normalize(product.getStore());

                if (!allowedStores.contains(store)) {
                    continue;
                }

                double cost = support.cost(
                    product,
                    item.getQuantity()
                );

                if (cost < lowestCost) {
                    lowestCost = cost;
                    cheapest = product;
                }
            }

            // Null means the item remains Unassigned.
            copy.setProduct(cheapest);
            result.addItem(copy);
        }

        return result;
    }

    private double effectiveTotal(ShoppingPlan plan) {

        double total = 0;

        for (ShoppingItem item : plan.getShoppingItems()) {

            Product product = item.getProduct();

            if (product != null) {
                total += support.cost(
                    product,
                    item.getQuantity()
                );
            }
        }

        return total;
    }
}
