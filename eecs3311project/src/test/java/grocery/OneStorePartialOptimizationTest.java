package grocery;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import grocery.adapter.CachedWebStoreAdapter;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.model.WebProductListing;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.strategy.OneStoreStrat;

class OneStorePartialOptimizationTest {
    private final PromotionService promotions = new PromotionService();

    private ShoppingPlan plan() {
        ShoppingPlan plan = new ShoppingPlan("Breakfast", 40.0);
        plan.addItem(new ShoppingItem("Eggs", "Dairy", 1));
        plan.addItem(new ShoppingItem("Bread", "Bakery", 1));
        plan.addItem(new ShoppingItem("Milk", "Dairy", 1));
        return plan;
    }

    private WebProductListing listing(String name, String category, String store, double price) {
        return new WebProductListing(name, "Test", category, store, price, 1, "pcs", "https://example.com/item");
    }

    private OneStoreStrat strategy(java.util.Map<String, List<WebProductListing>> cache, String store) {
        return new OneStoreStrat(
            new ProductSearchService(List.of(new CachedWebStoreAdapter(cache))),
            promotions, false, LocalDate.of(2026, 11, 13), store, new UserPreferences());
    }

    @Test void partialPlanPreservesUnmatchedItems() {
        var cache = java.util.Map.of(
            "Eggs", List.of(listing("Eggs", "Dairy", "Walmart", 4.99)),
            "Bread", List.of(listing("Bread", "Bakery", "Walmart", 2.99)),
            "Milk", List.of(listing("Milk", "Dairy", "No Frills", 5.99))
        );
        ShoppingPlan result = strategy(cache, "Walmart").optimize(plan());
        assertEquals(3, result.getShoppingItems().size());
        assertEquals(7.98, result.calculateTotal(), 0.001);
        assertNull(result.getShoppingItems().get(2).getProduct());
        assertTrue(result.getShoppingItems().stream()
            .filter(i -> i.getProduct() != null)
            .allMatch(i -> i.getProduct().getStore().equals("Walmart")));
    }

    @Test void completeOneStorePlanHasNoMissingItems() {
        var cache = java.util.Map.of(
            "Eggs", List.of(listing("Eggs", "Dairy", "Walmart", 4.99)),
            "Bread", List.of(listing("Bread", "Bakery", "Walmart", 2.99)),
            "Milk", List.of(listing("Milk", "Dairy", "Walmart", 5.99))
        );
        ShoppingPlan result = strategy(cache, "Walmart").optimize(plan());
        assertEquals(3, result.getShoppingItems().size());
        assertTrue(result.getShoppingItems().stream().allMatch(i -> i.getProduct() != null));
        assertEquals(13.97, result.calculateTotal(), 0.001);
    }

    @Test void zeroCoverageDoesNotClaimSuccess() {
        var cache = java.util.Map.of("Milk", List.of(
            listing("Milk", "Dairy", "No Frills", 5.99)));
        assertThrows(IllegalStateException.class,
            () -> strategy(cache, "Walmart").optimize(plan()));
    }
}
