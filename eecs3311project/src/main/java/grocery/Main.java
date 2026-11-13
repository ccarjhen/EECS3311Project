
package grocery;

import java.time.LocalDate;
import java.util.List;

import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.MarkPurchasedCommand;
import grocery.command.RemoveItemCommand;
import grocery.controller.ShoppingController;
import grocery.model.Product;
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

public class Main {

    // Fixed date for reproducible testing
    private static final LocalDate TEST_DATE =
            LocalDate.of(2026, 10, 31);

    private static final boolean HAS_MEMBERSHIP = true;

    // Shared services
    private static ProductSearchService searchService;
    private static PromotionService promotionService;
    private static PriceComparisonService comparisonService;
    private static ShoppingPlanPricingService pricingService;
    private static ShoppingController controller;

    // ========================================
    // MAIN METHOD
    // ========================================

    public static void main(String[] args) {

        initializeServices();

        System.out.println("====================================");
        System.out.println("     AI GROCERY COMPANION AGENT");
        System.out.println("          TESTING PROGRAM");
        System.out.println("====================================");

        runTest("F05 - Product Search",
                Main::testProductSearch);

        runTest("F06 - Price Comparison",
                Main::testPriceComparison);

        runTest("F07 - Promotion Detection",
                Main::testPromotions);

        runTest("Shopping Item",
                Main::testShoppingItem);

        runTest("Shopping Plan",
                Main::testShoppingPlan);

        runTest("F12 - User Preferences",
                Main::testUserPreferences);

        runTest("F09 - Shopping Optimization",
                Main::testOptimization);

        runTest("Shopping Controller",
                Main::testShoppingController);

        runTest("F10 - Interactive Checklist",
                Main::testInteractiveChecklist);

        System.out.println("\n====================================");
        System.out.println("       TEST EXECUTION FINISHED");
        System.out.println("====================================");
    }

    // ========================================
    // INITIALIZATION
    // ========================================

    private static void initializeServices() {

        // Store Adapters
        // StoreAdapter longos = new LongosAdapter();
        // StoreAdapter walmart = new WalmartAdapter();
        // StoreAdapter noFrills = new NoFrillsAdapter();

        // Services
        searchService = new ProductSearchService(
                List.of()
        );

        promotionService = new PromotionService();

        comparisonService = new PriceComparisonService();

        pricingService = new ShoppingPlanPricingService(
                promotionService
        );

        controller = new ShoppingController(searchService);
    }

    // ========================================
    // TEST RUNNER
    // ========================================

    private static void runTest(String title, Runnable test) {

        System.out.println("\n------------------------------------");
        System.out.println(" TEST: " + title);
        System.out.println("------------------------------------");

        try {
            test.run();
            System.out.println("[COMPLETED] " + title);

        } catch (Exception e) {
            System.out.println("[ERROR] " + title);
            System.out.println("Reason: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    // ========================================
    // TEST 1: F05 PRODUCT SEARCH
    // ========================================

    private static void testProductSearch() {

        List<Product> results =
                controller.search("milk");

        for (Product product : results) {
            System.out.println(product);
        }

        System.out.println(
                "Total products found: " + results.size()
        );
    }

    // ========================================
    // TEST 2: F06 PRICE COMPARISON
    // ========================================

    private static void testPriceComparison() {

        List<Product> results = searchService.search("milk");

        // Find the requested variety of milk
        Product reference = results.stream()
                .filter(p -> p.getName().equalsIgnoreCase("2% Milk"))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException("2% Milk not found")
                );

        List<Product> ranked =
                comparisonService.comparePrices(reference, results);

        for (Product product : ranked) {

            System.out.printf(
                    "%s | %s | $%.2f | Unit Price: $%.2f%n",
                    product.getName(),
                    product.getStore(),
                    product.getPrice(),
                    comparisonService.getNormalizedUnitPrice(product)
            );
        }
    }

    // ========================================
    // TEST 3: F07 PROMOTION DETECTION
    // ========================================

    private static void testPromotions() {

        List<Product> products = searchService.search("milk");

        int quantity = 2;

        for (Product product : products) {

            String status = promotionService.checkPromotions(
                    product, quantity, HAS_MEMBERSHIP, TEST_DATE
            );

            double discount = promotionService.calculateDiscount(
                    product, quantity, HAS_MEMBERSHIP, TEST_DATE
            );

            double finalTotal = promotionService.getDiscountedTotal(
                    product, quantity, HAS_MEMBERSHIP, TEST_DATE
            );

            System.out.println("\nProduct: " + product.getName());
            System.out.println("Store: " + product.getStore());
            System.out.println("Status: " + status);

            System.out.printf("Discount: $%.2f%n", discount);
            System.out.printf("Final Total: $%.2f%n", finalTotal);
        }
    }

    // ========================================
    // TEST 4: SHOPPING ITEM
    // ========================================

    private static void testShoppingItem() {

        Product milk = searchService.search("milk")
                .stream()
                .findFirst()
                .orElseThrow();

        ShoppingItem item =
                new ShoppingItem("Milk", "Dairy", 2);

        item.setProduct(milk);

        System.out.println("Item: " + item.getName());
        System.out.println("Quantity: " + item.getQuantity());
        System.out.println("Product: " + item.getProduct().getName());

        System.out.printf(
                "Subtotal: $%.2f%n", item.getSubtotal()
        );

        item.checkPurchased();

        System.out.println("Purchased: " + item.isPurchased());
    }

    // ========================================
    // TEST 5: SHOPPING PLAN
    // ========================================

    private static void testShoppingPlan() {

        ShoppingPlan plan = createSamplePlan();

        // Assign products through optimization
        OptimizationStrat strategy = createCheapestStrategy();

        ShoppingPlan optimized = strategy.optimize(plan);

        displayPlan(optimized);

        System.out.printf(
                "Regular Total: $%.2f%n",
                optimized.calculateTotal()
        );

        System.out.printf(
                "Discounted Total: $%.2f%n",
                pricingService.calculatePlanTotal(
                        optimized, HAS_MEMBERSHIP, TEST_DATE
                )
        );

        System.out.println(
                "Within Budget: " +
                pricingService.isWithinBudget(
                        optimized, HAS_MEMBERSHIP, TEST_DATE
                )
        );
    }

    // ========================================
    // TEST 6: F12 USER PREFERENCES
    // ========================================

    private static void testUserPreferences() {

        UserPreferences preferences = createPreferences();

        System.out.println(
                "Pork excluded: " +
                preferences.isExcludedItem("Pork")
        );

        System.out.println(
                "Chicken excluded: " +
                preferences.isExcludedItem("Chicken")
        );
    }

    // ========================================
    // TEST 7: F09 SHOPPING OPTIMIZATION
    // ========================================

    private static void testOptimization() {

        ShoppingPlan plan = createSamplePlan();
        UserPreferences preferences = createPreferences();

        OptimizationStrat cheapest = new CheapestStrat(
                searchService,
                promotionService,
                HAS_MEMBERSHIP,
                TEST_DATE,
                preferences
        );

        OptimizationStrat fewestStores = new FewestStoresStrat(
                searchService,
                promotionService,
                HAS_MEMBERSHIP,
                TEST_DATE,
                preferences
        );

        OptimizationStrat oneStore = new OneStoreStrat(
                searchService,
                promotionService,
                HAS_MEMBERSHIP,
                TEST_DATE,
                "Walmart",
                preferences
        );

        testStrategy("CHEAPEST", cheapest, plan);

        testStrategy("FEWEST STORES", fewestStores, plan);

        testStrategy("ONE STORE - WALMART", oneStore, plan);
    }

    private static void testStrategy(
            String name,
            OptimizationStrat strategy,
            ShoppingPlan plan) {

        System.out.println("\n=== " + name + " ===");

        try {
            ShoppingPlan result = strategy.optimize(plan);

            displayPlan(result);

            System.out.printf(
                    "Promotion-adjusted total: $%.2f%n",
                    pricingService.calculatePlanTotal(
                            result, HAS_MEMBERSHIP, TEST_DATE
                    )
            );

        } catch (IllegalStateException e) {
            System.out.println(
                    "Optimization failed: " + e.getMessage()
            );
        }
    }

    // ========================================
    // TEST 8: SHOPPING CONTROLLER
    // ========================================

    private static void testShoppingController() {

        ShoppingPlan plan = createSamplePlan();

        // Controller uses Strategy pattern
        controller.setOptimizationStrategy(
                createCheapestStrategy()
        );

        ShoppingPlan result = controller.optimize(plan);

        System.out.println("Controller optimized plan:");

        displayPlan(result);
    }


    // ========================================
    // TEST 9: F10 INTERACTIVE CHECKLIST
    // Command + Observer Patterns
    // ========================================

    private static void testInteractiveChecklist() {

        ShoppingPlan plan =
                new ShoppingPlan("Weekly Checklist", 50.00);

        // OBSERVER PATTERN
        // Receives notifications when the plan changes
        PlanObserver observer = updatedPlan -> {

            System.out.println("\n[OBSERVER] Checklist updated!");

            System.out.println(
                "Items in checklist: " +
                updatedPlan.getShoppingItems().size()
            );

            System.out.printf(
                "Regular total: $%.2f%n",
                updatedPlan.calculateTotal()
            );
        };

        plan.addObserver(observer);

        // Prepare a shopping item
        ShoppingItem milk =
                new ShoppingItem("Milk", "Dairy", 1);

        Product milkProduct = searchService.search("milk")
                .stream()
                .findFirst()
                .orElseThrow();

        milk.setProduct(milkProduct);

        // COMMAND 1: Add item
        System.out.println("\n1. ADD ITEM");

        controller.updateChecklist(
            new AddItemCommand(plan, milk)
        );

        displayPlan(plan);

        // COMMAND 2: Change quantity
        System.out.println("\n2. CHANGE QUANTITY");

        controller.updateChecklist(
            new ChangeQuantityCommand(plan, milk, 3)
        );

        System.out.println(
            "New quantity: " + milk.getQuantity()
        );

        // COMMAND 3: Mark as purchased
        System.out.println("\n3. MARK PURCHASED");

        controller.updateChecklist(
            new MarkPurchasedCommand(plan, milk)
        );

        System.out.println(
            "Milk purchased: " + milk.isPurchased()
        );

        // COMMAND 4: Remove item
        System.out.println("\n4. REMOVE ITEM");

        controller.updateChecklist(
            new RemoveItemCommand(plan, milk)
        );

        System.out.println(
            "Remaining items: " +
            plan.getShoppingItems().size()
        );

        plan.removeObserver(observer);

        System.out.println("\nChecklist test completed.");
    }


    // ========================================
    // HELPER METHODS
    // ========================================

    private static ShoppingPlan createSamplePlan() {

        ShoppingPlan plan =
                new ShoppingPlan("Weekly Groceries", 50.00);

        plan.addItem(new ShoppingItem("Milk", "Dairy", 2));
        plan.addItem(new ShoppingItem("Bread", "Bakery", 1));
        plan.addItem(new ShoppingItem("Eggs", "Dairy", 1));

        return plan;
    }

    private static UserPreferences createPreferences() {

        UserPreferences preferences = new UserPreferences();

        preferences.addPreferredStore("Longos");
        preferences.addPreferredBrand("Neilson");
        preferences.addDietaryRestriction("Vegetarian");
        preferences.addAllergy("Peanuts");
        preferences.addExcludedItem("Pork");

        return preferences;
    }

    private static OptimizationStrat createCheapestStrategy() {

        return new CheapestStrat(
                searchService,
                promotionService,
                HAS_MEMBERSHIP,
                TEST_DATE,
                createPreferences()
        );
    }

    private static void displayPlan(ShoppingPlan plan) {

        System.out.println("Plan: " + plan.getName());

        for (ShoppingItem item : plan.getShoppingItems()) {

            Product product = item.getProduct();

            if (product == null) {
                System.out.println(
                        item.getName() + " | No product selected"
                );
                continue;
            }

            System.out.printf(
                    "%s x%d | %s | %s | $%.2f%n",
                    item.getName(),
                    item.getQuantity(),
                    product.getName(),
                    product.getStore(),
                    item.getSubtotal()
            );
        }
    }
}
