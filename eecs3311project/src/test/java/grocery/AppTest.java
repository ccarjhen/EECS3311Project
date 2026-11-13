
package grocery;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.agent.LLMClient;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.MarkPurchasedCommand;
import grocery.command.RemoveItemCommand;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.Promotion;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.observer.PlanObserver;
import grocery.service.PriceComparisonService;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;
import grocery.strategy.OptimizationStrat;

public class AppTest {

    // ========================================
    // SHARED TEST DATA
    // ========================================

    private static final LocalDate TEST_DATE =
            LocalDate.of(2026, 10, 31);

    private ProductSearchService searchService;
    private PromotionService promotionService;
    private PriceComparisonService comparisonService;
    private ShoppingPlanPricingService pricingService;
    private ShoppingController controller;
    private UserPreferences preferences;

    // ========================================
    // SETUP: RUN BEFORE EACH TEST
    // ========================================

    @BeforeEach
    public void setUp() {

        searchService = new ProductSearchService(
            List.of(
                // new LongosAdapter(),
                // new WalmartAdapter(),
                // new NoFrillsAdapter()
            )
        );

        promotionService = new PromotionService();
        comparisonService = new PriceComparisonService();

        pricingService = new ShoppingPlanPricingService(
            promotionService
        );

        controller = new ShoppingController(searchService);

        preferences = new UserPreferences();
        preferences.addPreferredStore("Longos");
        preferences.addPreferredBrand("Neilson");
        preferences.addExcludedItem("Pork");
    }

    // ========================================
    // DOMAIN MODEL TESTS
    // ========================================

    @Test
    public void testProductCreation() {

        Product milk = createMilkProduct(6.49);

        assertAll(
            () -> assertEquals("2% Milk", milk.getName()),
            () -> assertEquals("Walmart", milk.getStore()),
            () -> assertEquals(6.49, milk.getPrice(), 0.001),
            () -> assertEquals(4, milk.getSize()),
            () -> assertEquals("L", milk.getUnit())
        );
    }

    @Test
    public void testShoppingItemSubtotal() {

        Product milk = createMilkProduct(6.49);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 2);

        item.setProduct(milk);

        assertEquals(12.98, item.getSubtotal(), 0.001);
    }

    @Test
    public void testShoppingPlanTotal() {

        ShoppingPlan plan =
                new ShoppingPlan("Weekly Groceries", 50.00);

        Product bread = new Product(
            "P002", "White Bread", "Wonder",
            "Bakery", "Walmart", 3.49, 675, "g"
        );

        ShoppingItem item =
                new ShoppingItem("Bread", "Bakery", 2);

        item.setProduct(bread);
        plan.addItem(item);

        assertAll(
            () -> assertEquals(1, plan.getShoppingItems().size()),
            () -> assertEquals(6.98, plan.calculateTotal(), 0.001),
            () -> assertTrue(plan.isOnBudget())
        );
    }

    @Test
    public void testMarkPurchased() {

        ShoppingItem item =
                new ShoppingItem("Eggs", "Dairy", 1);

        assertFalse(item.isPurchased());

        item.checkPurchased();

        assertTrue(item.isPurchased());
    }

    @Test
    public void testInvalidQuantity() {

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        assertThrows(
            IllegalArgumentException.class,
            () -> item.setQuantity(0)
        );
    }

    // ========================================
    // F05 - PRODUCT SEARCH
    // ========================================

    @Test
    public void testProductSearch() {

        List<Product> results = controller.search("milk");

        assertFalse(results.isEmpty());

        for (Product product : results) {
            assertTrue(
                product.getName().toLowerCase()
                    .contains("milk")
            );
        }
    }

    @Test
    public void testProductNotFound() {

        List<Product> results =
                searchService.search("nonexistentproduct123");

        assertTrue(results.isEmpty());
    }

    // ========================================
    // F06 - PRICE COMPARISON
    // ========================================

    @Test
    public void testPriceComparison() {

        Product reference = createMilkProduct(6.49);

        List<Product> results =
            comparisonService.comparePrices(
                reference, searchService.search("milk")
            );

        assertFalse(results.isEmpty());

        for (int i = 1; i < results.size(); i++) {

            double previous =
                comparisonService.getNormalizedUnitPrice(
                    results.get(i - 1)
                );

            double current =
                comparisonService.getNormalizedUnitPrice(
                    results.get(i)
                );

            assertTrue(previous <= current);
        }

        for (Product product : results) {
            assertTrue(
                comparisonService.areComparable(
                    reference, product
                )
            );
        }
    }

    @Test
    public void testUnitNormalization() {

        Product milkLitres = new Product(
            "T001", "Test Milk", "Test",
            "Dairy", "Store A", 4.00, 1, "L"
        );

        Product milkMillilitres = new Product(
            "T002", "Test Milk", "Test",
            "Dairy", "Store B", 3.00, 500, "ml"
        );

        assertEquals(
            4.00,
            comparisonService.getNormalizedUnitPrice(milkLitres),
            0.001
        );

        assertEquals(
            6.00,
            comparisonService.getNormalizedUnitPrice(milkMillilitres),
            0.001
        );

        assertTrue(
            comparisonService.areComparable(
                milkLitres, milkMillilitres
            )
        );
    }

    // ========================================
    // F07 - PROMOTION DETECTION
    // ========================================

    @Test
    public void testPromotionEligibility() {

        Product milk = createPromotionalMilk(
            6.49, true, 2
        );

        String result = promotionService.checkPromotions(
            milk, 2, true, TEST_DATE
        );

        assertEquals("Eligible", result);
    }

    @Test
    public void testMembershipRequired() {

        Product milk = createPromotionalMilk(
            6.49, true, 2
        );

        String result = promotionService.checkPromotions(
            milk, 2, false, TEST_DATE
        );

        assertEquals(
            "Promotion requires membership",
            result
        );
    }

    @Test
    public void testPromotionExpired() {

        Product milk = createPromotionalMilk(
            6.49, true, 2
        );

        String result = promotionService.checkPromotions(
            milk, 2, true, LocalDate.of(2026, 11, 6)
        );

        assertEquals(
            "Promotion is not active today",
            result
        );
    }

    @Test
    public void testMinimumQuantity() {

        Product milk = createPromotionalMilk(
            6.49, false, 2
        );

        String result = promotionService.checkPromotions(
            milk, 1, false, TEST_DATE
        );

        assertEquals(
            "Promotion requires minimum quantity of 2",
            result
        );
    }

    @Test
    public void testDiscountCalculation() {

        Product milk = createPromotionalMilk(
            5.00, false, 1
        );

        double finalTotal =
            promotionService.getDiscountedTotal(
                milk, 2, false, TEST_DATE
            );

        assertEquals(8.00, finalTotal, 0.001);
    }

    @Test
    public void testPromotionAwarePlanTotal() {

        Product milk = createPromotionalMilk(
            5.00, false, 1
        );

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 2);

        item.setProduct(milk);

        ShoppingPlan plan =
                new ShoppingPlan("Test Plan", 50.00);

        plan.addItem(item);

        assertEquals(
            10.00, plan.calculateTotal(), 0.001
        );

        assertEquals(
            8.00,
            pricingService.calculatePlanTotal(
                plan, false, TEST_DATE
            ),
            0.001
        );
    }

    // ========================================
    // F09 - OPTIMIZATION STRATEGIES
    // ========================================

    @Test
    public void testCheapestOptimization() {

        ShoppingPlan plan = createSamplePlan();

        OptimizationStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );

        ShoppingPlan result = strategy.optimize(plan);

        assertAll(
            () -> assertEquals(3, result.getShoppingItems().size()),
            () -> assertTrue(result.getShoppingItems().stream()
                    .allMatch(item -> item.getProduct() != null))
        );
    }

    @Test
    public void testFewestStoresOptimization() {

        ShoppingPlan plan = createSamplePlan();

        OptimizationStrat strategy = new FewestStoresStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );

        ShoppingPlan result = strategy.optimize(plan);

        long storeCount = result.getShoppingItems().stream()
            .map(item -> item.getProduct().getStore())
            .distinct()
            .count();

        // All three sample items are available at Walmart,
        // so one store should be enough.
        assertEquals(1, storeCount);
    }

    @Test
    public void testOneStoreOptimization() {

        ShoppingPlan plan = createSamplePlan();

        OptimizationStrat strategy = new OneStoreStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            "Walmart",
            preferences
        );

        ShoppingPlan result = strategy.optimize(plan);

        assertEquals(3, result.getShoppingItems().size());

        for (ShoppingItem item : result.getShoppingItems()) {

            assertNotNull(item.getProduct());

            assertEquals(
                "Walmart",
                item.getProduct().getStore()
            );
        }
    }

    // ========================================
    // SHOPPING CONTROLLER
    // ========================================

    @Test
    public void testControllerOptimization() {

        ShoppingPlan plan = createSamplePlan();

        OptimizationStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            preferences
        );

        controller.setOptimizationStrategy(strategy);

        ShoppingPlan result = controller.optimize(plan);

        assertEquals(3, result.getShoppingItems().size());

        for (ShoppingItem item : result.getShoppingItems()) {
            assertNotNull(item.getProduct());
        }
    }

    @Test
    public void testControllerWithoutStrategy() {

        ShoppingPlan plan = createSamplePlan();

        assertThrows(
            IllegalStateException.class,
            () -> controller.optimize(plan)
        );
    }


    // ========================================
    // F10 - INTERACTIVE CHECKLIST TESTS
    // Command + Observer Patterns
    // ========================================

    // TEST 1: Add Item Command
    @Test
    public void testAddItemCommand() {

        ShoppingPlan plan =
                new ShoppingPlan("Checklist Test", 50.00);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        controller.updateChecklist(
            new AddItemCommand(plan, item)
        );

        assertEquals(1, plan.getShoppingItems().size());
        assertTrue(plan.getShoppingItems().contains(item));
    }

    // TEST 2: Remove Item Command
    @Test
    public void testRemoveItemCommand() {

        ShoppingPlan plan =
                new ShoppingPlan("Checklist Test", 50.00);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        plan.addItem(item);

        controller.updateChecklist(
            new RemoveItemCommand(plan, item)
        );

        assertTrue(plan.getShoppingItems().isEmpty());
    }

    // TEST 3: Mark Purchased Command
    @Test
    public void testMarkPurchasedCommand() {

        ShoppingPlan plan =
                new ShoppingPlan("Checklist Test", 50.00);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        plan.addItem(item);

        assertFalse(item.isPurchased());

        controller.updateChecklist(
            new MarkPurchasedCommand(plan, item)
        );

        assertTrue(item.isPurchased());
    }

    // TEST 4: Change Quantity Command
    @Test
    public void testChangeQuantityCommand() {

        ShoppingPlan plan =
                new ShoppingPlan("Checklist Test", 50.00);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 5.00, 4, "L"
        );

        item.setProduct(milk);
        plan.addItem(item);

        controller.updateChecklist(
            new ChangeQuantityCommand(plan, item, 3)
        );

        assertEquals(3, item.getQuantity());

        // $5.00 x 3 = $15.00
        assertEquals(15.00, plan.calculateTotal(), 0.001);
    }

    // TEST 5: Observer Notification
    @Test
    public void testObserverNotification() {

        ShoppingPlan plan =
                new ShoppingPlan("Checklist Test", 50.00);

        AtomicInteger notificationCount =
                new AtomicInteger(0);

        PlanObserver observer = updatedPlan -> {
            notificationCount.incrementAndGet();
        };

        plan.addObserver(observer);

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 1);

        // Add
        controller.updateChecklist(
            new AddItemCommand(plan, item)
        );

        assertEquals(1, notificationCount.get());

        // Change quantity
        controller.updateChecklist(
            new ChangeQuantityCommand(plan, item, 2)
        );

        assertEquals(2, notificationCount.get());

        // Mark purchased
        controller.updateChecklist(
            new MarkPurchasedCommand(plan, item)
        );

        assertEquals(3, notificationCount.get());

        // Remove
        controller.updateChecklist(
            new RemoveItemCommand(plan, item)
        );

        assertEquals(4, notificationCount.get());

        plan.removeObserver(observer);
    }

    // ========================================
    // 
    // ========================================

    @Test
    public void testShoppingAgentCreatesPlan() throws Exception {

        // Fake LLM used only for automated testing
        LLMClient fakeLLM = prompt -> """
            {
            "items": [
                {
                "name": "Milk",
                "category": "Dairy",
                "quantity": 2
                },
                {
                "name": "Bread",
                "category": "Bakery",
                "quantity": 1
                },
                {
                "name": "Eggs",
                "category": "Dairy",
                "quantity": 1
                }
            ]
            }
            """;

        ShoppingAgent agent = new ShoppingAgent(
            fakeLLM,
            searchService,
            promotionService
        );

        ShoppingRequest request = new ShoppingRequest(
            "I need milk, bread and eggs",
            40.00
        );

        ShoppingPlan result = agent.createPlan(
            request,
            false,
            LocalDate.of(2026, 10, 31)
        );

        assertEquals(3, result.getShoppingItems().size());

        for (ShoppingItem item : result.getShoppingItems()) {
            assertNotNull(item.getProduct());
            assertTrue(item.getQuantity() > 0);
        }
    }



    // ========================================
    // HELPER METHODS
    // ========================================

    private Product createMilkProduct(double price) {

        return new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", price, 4, "L"
        );
    }

    private Product createPromotionalMilk(
            double price,
            boolean membershipRequired,
            int minimumQuantity) {

        Product milk = createMilkProduct(price);

        Promotion promotion = new Promotion(
            "20% off Milk",
            20.0,
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 11, 5),
            membershipRequired,
            minimumQuantity
        );

        milk.setPromotion(promotion);

        return milk;
    }

    private ShoppingPlan createSamplePlan() {

        ShoppingPlan plan =
            new ShoppingPlan("Test Plan", 50.00);

        plan.addItem(
            new ShoppingItem("Milk", "Dairy", 2)
        );

        plan.addItem(
            new ShoppingItem("Bread", "Bakery", 1)
        );

        plan.addItem(
            new ShoppingItem("Eggs", "Dairy", 1)
        );

        return plan;
    }
}
