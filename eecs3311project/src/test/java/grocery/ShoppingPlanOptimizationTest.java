package grocery;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.adapter.StoreAdapter;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;

public class ShoppingPlanOptimizationTest {

    private static final LocalDate TEST_DATE =
        LocalDate.of(2026, 11, 12);

    private ProductSearchService searchService;
    private PromotionService promotionService;
    private UserPreferences preferences;
    private ShoppingPlan originalPlan;

    // --- SETUP METHODS ---

    @BeforeEach
    void setUp() {

        // Controlled products for testing only.
        Map<String, List<Product>> catalog = Map.of(

            "Milk", List.of(
                product("M1", "Milk", "Dairy", "Store A", 4.00),
                product("M2", "Milk", "Dairy", "Store B", 5.00)
            ),

            "Bread", List.of(
                product("B1", "Bread", "Bakery", "Store A", 4.00),
                product("B2", "Bread", "Bakery", "Store B", 2.00)
            ),

            "Eggs", List.of(
                product("E1", "Eggs", "Dairy", "Store A", 3.00),
                product("E2", "Eggs", "Dairy", "Store B", 4.00)
            )
        );

        // Stub adapter: no network/API calls.
        StoreAdapter testAdapter = query ->
            catalog.getOrDefault(query, List.of());

        searchService = new ProductSearchService(
            List.of(testAdapter)
        );

        promotionService = new PromotionService();
        preferences = new UserPreferences();

        originalPlan = new ShoppingPlan(
            "Test Grocery Plan",
            30.00
        );

        originalPlan.addItem(
            new ShoppingItem("Milk", "Dairy", 1)
        );

        originalPlan.addItem(
            new ShoppingItem("Bread", "Bakery", 1)
        );

        originalPlan.addItem(
            new ShoppingItem("Eggs", "Dairy", 1)
        );
    }

    // --- TEST HELPER METHODS ---

    private Product product(
            String id,
            String name,
            String category,
            String store,
            double price) {

        return new Product(
            id,
            name,
            "Test Brand",
            category,
            store,
            price,
            1,
            "pcs"
        );
    }

    private CheapestStrat cheapestStrategy() {

        return new CheapestStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );
    }

    private FewestStoresStrat fewestStoresStrategy() {

        return new FewestStoresStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );
    }

    private OneStoreStrat oneStoreStrategy(String store) {

        return new OneStoreStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            store,
            preferences
        );
    }

    private Set<String> selectedStores(ShoppingPlan plan) {

        return plan.getShoppingItems()
            .stream()
            .map(item -> item.getProduct().getStore())
            .collect(Collectors.toSet());
    }


    // --- SHOPPING PLAN MODEL TESTS ---

    @Test
    void testShoppingPlanTotal() {
        ShoppingPlan plan = cheapestStrategy()
            .optimize(originalPlan);

        assertEquals(
            9.00,
            plan.calculateTotal(),
            0.001
        );
    }

    @Test
    void testRemoveProductUpdatesTotal() {
        ShoppingPlan plan = cheapestStrategy()
            .optimize(originalPlan);

        ShoppingItem bread = plan.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Bread"))
            .findFirst()
            .orElseThrow();

        plan.removeItem(bread);

        assertEquals(2, plan.getShoppingItems().size());

        // Milk $4 + Eggs $3
        assertEquals(7.00, plan.calculateTotal(), 0.001);
    }

    @Test
    void testReplaceProductUpdatesTotal() {

        ShoppingPlan plan = cheapestStrategy()
            .optimize(originalPlan);

        ShoppingItem milk = plan.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Milk"))
            .findFirst()
            .orElseThrow();

        // Replace $4 milk with $5 milk.
        milk.setProduct(
            product("M2", "Milk", "Dairy", "Store B", 5.00)
        );

        assertEquals(
            "Store B",
            milk.getProduct().getStore()
        );

        assertEquals(
            10.00,
            plan.calculateTotal(),
            0.001
        );
    }

    // --- CHEAPEST STRATEGY TESTS ---

    @Test
    void testCheapestOptimization() {
        ShoppingPlan result = cheapestStrategy()
            .optimize(originalPlan);

        // Milk A $4 + Bread B $2 + Eggs A $3
        assertEquals(9.00, result.calculateTotal(), 0.001);

        assertEquals(
            Set.of("Store A", "Store B"),
            selectedStores(result)
        );
    }


    // --- FEWEST STORES STRATEGY TESTS ---

    @Test
    void testFewestStoresOptimization() {
        ShoppingPlan result = fewestStoresStrategy()
            .optimize(originalPlan);

        // Every item is available in both stores.
        // Therefore, only one store is necessary.
        assertEquals(1, selectedStores(result).size());

        // Store A costs $11 and Store B costs $11.
        assertEquals(11.00, result.calculateTotal(), 0.001);
    }

    // --- ONE STORE STRATEGY TESTS ---

    @Test
    void testOneStoreOptimization() {
        ShoppingPlan result = oneStoreStrategy("Store B")
            .optimize(originalPlan);

        assertEquals(
            Set.of("Store B"),
            selectedStores(result)
        );

        // Milk $5 + Bread $2 + Eggs $4
        assertEquals(11.00, result.calculateTotal(), 0.001);
    }

    // --- SWITCHING STRATEGIES ---

    @Test
    void testSwitchOptimizationStrategies() {

        ShoppingPlan fewest = fewestStoresStrategy()
            .optimize(originalPlan);

        ShoppingPlan cheapest = cheapestStrategy()
            .optimize(originalPlan);

        assertEquals(11.00, fewest.calculateTotal(), 0.001);
        assertEquals(9.00, cheapest.calculateTotal(), 0.001);

        assertEquals(1, selectedStores(fewest).size());
        assertEquals(2, selectedStores(cheapest).size());
    }

    // --- ORIGINAL PLAN MUST REMAIN UNCHANGED ---  

    @Test
    void testOriginalPlanIsNotModified() {

        cheapestStrategy().optimize(originalPlan);

        assertEquals(
            3,
            originalPlan.getShoppingItems().size()
        );

        for (ShoppingItem item :
                originalPlan.getShoppingItems()) {

            assertNull(item.getProduct());
        }
    }

    // --- UNFULFILLABLE PLAN ---

    @Test
    void testUnavailableStoreThrowsException() {

        assertThrows(
            IllegalStateException.class,
            () -> oneStoreStrategy("Unknown Store")
                .optimize(originalPlan)
        );
    }

    // --- USER PREFERENCES ---

    @Test
    void testExcludedProductsAreIgnored() {

        // Exclude the cheaper product by name.
        preferences.addExcludedItem("Cheap Milk");

        StoreAdapter adapter = query -> {
            if (!query.equals("Milk")) {
                return List.of();
            }

            return List.of(
                product(
                    "M1",
                    "Cheap Milk",
                    "Dairy",
                    "Store A",
                    2.00
                ),
                product(
                    "M2",
                    "Regular Milk",
                    "Dairy",
                    "Store B",
                    5.00
                )
            );
        };

        ProductSearchService service =
            new ProductSearchService(List.of(adapter));

        ShoppingPlan milkPlan =
            new ShoppingPlan("Milk Test", 10.00);

        milkPlan.addItem(
            new ShoppingItem("Milk", "Dairy", 1)
        );

        CheapestStrat strategy = new CheapestStrat(
            service,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );

        ShoppingPlan result =
            strategy.optimize(milkPlan);

        assertEquals(
            "Regular Milk",
            result.getShoppingItems()
                .get(0)
                .getProduct()
                .getName()
        );

        assertEquals(5.00, result.calculateTotal(), 0.001);
    }
}
