package grocery.strategy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;

class OptimizationSupport {

    private final ProductSearchService searchService;
    private final PromotionService promotionService;
    private final boolean hasMembership;
    private final LocalDate date;
    private final UserPreferences userPreferences;
    private final ShoppingPlanPricingService pricingService;

    OptimizationSupport(ProductSearchService searchService, PromotionService promotionService, boolean hasMembership, LocalDate date, UserPreferences preferences) {
        this.searchService = searchService;
        this.promotionService = promotionService;
        this.hasMembership = hasMembership;
        this.date = date;
        this.userPreferences = preferences;
        this.pricingService = new ShoppingPlanPricingService(promotionService);
    }

    private boolean isExcluded(Product product) {
        if(userPreferences == null) {
            return false;
        }

        String productName = product.getName().toLowerCase(Locale.ROOT);
        
        for (String exclusion : userPreferences.getExcludedItems()) {

            String excluded = exclusion.trim()
                    .toLowerCase(Locale.ROOT);

            if (!excluded.isEmpty() && productName.contains(excluded)) {
                return true;
            }
        }

        return false;
    }

    List<Product> findOptions(ShoppingItem item, Set<String> stores){
        List<Product> result = new ArrayList<>();

        for(Product product: searchService.search(item.getName())){
            if (stores != null && !stores.contains(product.getStore())){
                continue;
            }

            // Initial compatibility rule: same requested category
            if(!product.getCategory().equalsIgnoreCase(item.getCategory())){
                continue;
            }

            // Exclusion rule based on user preferences
            if (isExcluded(product)) {
                continue;
            }

            result.add(product);
        }

        return result;
    }

    double cost(Product product, int quantity){
        return promotionService.getDiscountedTotal(product, quantity, hasMembership, date);
    }

    Product cheapest(List<Product> options, int quantity){
        return options.stream().min(Comparator.comparingDouble(p -> cost(p, quantity))).orElseThrow(() -> new IllegalArgumentException("No suitable product found"));
    }

    ShoppingPlan buildPlan(ShoppingPlan original,
                           Set<String> allowedStores) {

        ShoppingPlan result = new ShoppingPlan(
            original.getName(), original.getEstimatedBudget()
        );

        for (ShoppingItem item : original.getShoppingItems()) {
            List<Product> options = findOptions(item, allowedStores);

            if (options.isEmpty()) {
                // throw new IllegalStateException(
                //     "Cannot fulfill shopping item: " + item.getName()
                // );
                continue; // Skip this item if no options are available
            }

            Product selected = cheapest(options, item.getQuantity());

            ShoppingItem copy = new ShoppingItem(
                item.getName(), item.getCategory(), item.getQuantity()
            );

            copy.setProduct(selected);
            copy.setPurchased(item.isPurchased());
            result.addItem(copy);
        }

        return result;
    }

    double effectiveTotal(ShoppingPlan plan) {
        return pricingService.calculatePlanTotal(plan, hasMembership, date);
    }

   
}
