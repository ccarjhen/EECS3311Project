package grocery;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.adapter.StoreAdapter;
import grocery.agent.LLMClient;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.Promotion;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;
import grocery.strategy.CheapestStrat;

class AppTest {

    private static final LocalDate TEST_DATE =
        LocalDate.of(2026, 10, 31);

    private ProductSearchService searchService;
    private PromotionService promotionService;
    private ShoppingPlanPricingService pricingService;
    private ShoppingController controller;

    // --- SETUP ---

    @BeforeEach
    void setUp() {

        // In-memory test catalogue.
        // No real retailer or Claude API calls.

        Map<String, List<Product>> catalogue = Map.of(

            "milk", List.of(
                product("M1", "Milk", "Dairy",
                    "Walmart", 5.00),

                product("M2", "Milk", "Dairy",
                    "No Frills", 6.00)
            ),

            "bread", List.of(
                product("B1", "Bread", "Bakery",
                    "Walmart", 3.00),

                product("B2", "Bread", "Bakery",
                    "No Frills", 2.00)
            ),

            "eggs", List.of(
                product("E1", "Eggs", "Dairy",
                    "Walmart", 4.00),

                product("E2", "Eggs", "Dairy",
                    "No Frills", 5.00)
            )
        );

        StoreAdapter mockAdapter = query -> {

            if (query == null) {
                return List.of();
            }

            String key = query.trim()
                .toLowerCase(Locale.ROOT);

            return catalogue.getOrDefault(
                key,
                List.of()
            );
        };

        searchService = new ProductSearchService(
            List.of(mockAdapter)
        );

        promotionService = new PromotionService();

        pricingService = new ShoppingPlanPricingService(
            promotionService
        );

        controller = new ShoppingController(searchService);
    }

    // --- PRODUCT AND SHOPPING PLAN ---

    @Test
    void testProductCreation() {

        Product milk = product(
            "M1", "Milk", "Dairy",
            "Walmart", 5.00
        );

        assertAll(
            () -> assertEquals("Milk", milk.getName()),
            () -> assertEquals("Dairy", milk.getCategory()),
            () -> assertEquals("Walmart", milk.getStore()),
            () -> assertEquals(5.00, milk.getPrice(), 0.001),
            () -> assertEquals(1.0, milk.getSize(), 0.001),
            () -> assertEquals("pcs", milk.getUnit())
        );
    }

    @Test
    void testShoppingPlanBudget() {

        ShoppingPlan plan = new ShoppingPlan(
            "Weekly Groceries", 20.00
        );

        ShoppingItem milk =
            new ShoppingItem("Milk", "Dairy", 2);

        milk.setProduct(
            product("M1", "Milk", "Dairy",
                "Walmart", 5.00)
        );

        plan.addItem(milk);

        assertEquals(
            10.00,
            plan.calculateTotal(),
            0.001
        );

        assertTrue(plan.isOnBudget());
    }

    // --- PRODUCT SEARCH ---

    @Test
    void testProductSearch() {

        List<Product> results = controller.search("milk");

        assertEquals(2, results.size());

        assertTrue(results.stream().allMatch(
            p -> p.getName().equalsIgnoreCase("Milk")
        ));
    }

    @Test
    void testProductNotFound() {

        List<Product> results =
            controller.search("nonexistentproduct123");

        assertTrue(results.isEmpty());
    }

    // --- PROMOTION TESTS ---

    @Test
    void testPromotionEligibility() {

        Product milk = promotionalMilk(
            6.49, true, 2
        );

        String result = promotionService.checkPromotions(
            milk, 2, true, TEST_DATE
        );

        assertEquals("Eligible", result);
    }

    @Test
    void testMembershipRequired() {

        Product milk = promotionalMilk(
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
    void testPromotionExpired() {

        Product milk = promotionalMilk(
            6.49, true, 2
        );

        String result = promotionService.checkPromotions(
            milk,
            2,
            true,
            LocalDate.of(2026, 11, 6)
        );

        assertEquals(
            "Promotion is not active today",
            result
        );
    }

    @Test
    void testMinimumQuantity() {

        Product milk = promotionalMilk(
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
    void testDiscountCalculation() {

        Product milk = promotionalMilk(
            5.00, false, 1
        );

        double total =
            promotionService.getDiscountedTotal(
                milk, 2, false, TEST_DATE
            );

        // $5 x 2 = $10
        // 20% discount = $8

        assertEquals(8.00, total, 0.001);
    }

    @Test
    void testPromotionAwarePlanTotal() {

        ShoppingPlan plan = new ShoppingPlan(
            "Promotional Plan", 50.00
        );

        ShoppingItem milk =
            new ShoppingItem("Milk", "Dairy", 2);

        milk.setProduct(
            promotionalMilk(5.00, false, 1)
        );

        plan.addItem(milk);

        // Regular total before promotion.
        assertEquals(
            10.00,
            plan.calculateTotal(),
            0.001
        );

        // Total after 20% discount.
        assertEquals(
            8.00,
            pricingService.calculatePlanTotal(
                plan, false, TEST_DATE
            ),
            0.001
        );
    }

    // --- SHOPPING CONTROLLER TESTS ---

    @Test
    void testControllerWithoutStrategy() {

        ShoppingPlan plan = new ShoppingPlan(
            "Test Plan", 50.00
        );

        assertThrows(
            IllegalStateException.class,
            () -> controller.optimize(plan)
        );
    }

    @Test
    void testControllerRejectsNullStrategy() {

        assertThrows(
            IllegalArgumentException.class,
            () -> controller.setOptimizationStrategy(null)
        );
    }

    @Test
    void testControllerOptimization() {

        ShoppingPlan plan = new ShoppingPlan(
            "Test Plan", 50.00
        );

        plan.addItem(
            new ShoppingItem("Milk", "Dairy", 1)
        );

        plan.addItem(
            new ShoppingItem("Bread", "Bakery", 1)
        );

        CheapestStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            false,
            TEST_DATE,
            new grocery.model.UserPreferences()
        );

        controller.setOptimizationStrategy(strategy);

        ShoppingPlan result = controller.optimize(plan);

        assertEquals(2, result.getShoppingItems().size());

        // Milk $5 + Bread $2
        assertEquals(
            7.00,
            result.calculateTotal(),
            0.001
        );
    }

    // --- SHOPPING AGENT TESTS ---

    @Test
    void testShoppingAgentCreatesPlan()
            throws Exception {

        // Simulated Claude response.
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
            TEST_DATE
        );

        assertEquals(
            3,
            result.getShoppingItems().size()
        );

        assertTrue(result.getShoppingItems().stream()
            .allMatch(item -> item.getProduct() != null));

        assertTrue(result.getShoppingItems().stream()
            .allMatch(item -> item.getQuantity() > 0));

        // Milk: 2 x $5 = $10
        // Bread: 1 x $2 = $2
        // Eggs: 1 x $4 = $4
        assertEquals(
            16.00,
            result.calculateTotal(),
            0.001
        );
    }

    @Test
    void testShoppingAgentRejectsEmptyList() {

        LLMClient fakeLLM = prompt -> """
            {
              "items": []
            }
            """;

        ShoppingAgent agent = new ShoppingAgent(
            fakeLLM,
            searchService,
            promotionService
        );

        ShoppingRequest request = new ShoppingRequest(
            "Make a grocery list",
            40.00
        );

        assertThrows(
            IllegalStateException.class,
            () -> agent.createPlan(
                request, false, TEST_DATE
            )
        );
    }

    @Test
    void testShoppingAgentRejectsInvalidQuantity() {

        LLMClient fakeLLM = prompt -> """
            {
              "items": [
                {
                  "name": "Milk",
                  "category": "Dairy",
                  "quantity": "two"
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
            "Buy milk",
            20.00
        );

        assertThrows(
            IllegalStateException.class,
            () -> agent.createPlan(
                request, false, TEST_DATE
            )
        );
    }

    // --- HELPER METHODS ---

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
            1.0,
            "pcs"
        );
    }

    private Product promotionalMilk(
            double price,
            boolean membershipRequired,
            int minimumQuantity) {

        Product milk = new Product(
            "PM1",
            "2% Milk",
            "Neilson",
            "Dairy",
            "Walmart",
            price,
            4.0,
            "L"
        );

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
}
