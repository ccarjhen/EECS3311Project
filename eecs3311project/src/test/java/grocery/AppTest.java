
package grocery;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.model.Product;
import grocery.model.Promotion;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;

public class AppTest {

    // TEST 1: Product Information
    @Test
    public void testProductCreation() {

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 6.49, 4, "L"
        );

        assertEquals("2% Milk", milk.getName());
        assertEquals("Walmart", milk.getStore());
        assertEquals(6.49, milk.getPrice(), 0.001);
    }

    // TEST 2: ShoppingItem Subtotal
    @Test
    public void testShoppingItemSubtotal() {

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 6.49, 4, "L"
        );

        ShoppingItem item =
            new ShoppingItem("Milk", "Dairy", 2);

        item.setProduct(milk);

        assertEquals(12.98, item.getSubtotal(), 0.001);
    }

    // TEST 3: ShoppingPlan Total
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

        assertEquals(6.98, plan.calculateTotal(), 0.001);
        assertTrue(plan.isOnBudget());
    }

    // TEST 4: Purchased Status
    @Test
    public void testMarkPurchased() {

        ShoppingItem item =
            new ShoppingItem("Eggs", "Dairy", 1);

        assertFalse(item.isPurchased());

        item.checkPurchased();

        assertTrue(item.isPurchased());
    }

    // TEST 5: Product Search
    @Test
    public void testProductSearch() {

        ProductSearchService searchService =
            new ProductSearchService(
                List.of(
                    new LongosAdapter(),
                    new WalmartAdapter(),
                    new NoFrillsAdapter()
                )
            );

        List<Product> results = searchService.search("Milk");

        assertFalse(results.isEmpty());

        for (Product product : results) {
            assertTrue(
                product.getName().toLowerCase()
                    .contains("milk")
            );
        }
    }

    // TEST 6: Promotion Eligibility
    @Test
    public void testPromotionEligibility() {

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 6.49, 4, "L"
        );

        Promotion promotion = new Promotion(
            "20% off Milk",
            20.0,
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 11, 5),
            true,
            2
        );

        milk.setPromotion(promotion);

        PromotionService service = new PromotionService();

        String result = service.checkPromotions(
            milk, 2, true,
            LocalDate.of(2026, 10, 31)
        );

        assertEquals("Eligible", result);
    }

    // TEST 7: Promotion Requires Membership
    @Test
    public void testMembershipRequired() {

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 6.49, 4, "L"
        );

        milk.setPromotion(new Promotion(
            "20% off Milk",
            20.0,
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 11, 5),
            true,
            2
        ));

        PromotionService service = new PromotionService();

        assertEquals(
            "Promotion requires membership",
            service.checkPromotions(
                milk, 2, false,
                LocalDate.of(2026, 10, 31)
            )
        );
    }

    // TEST 8: Discount Calculation
    @Test
    public void testDiscountCalculation() {

        Product milk = new Product(
            "P001", "2% Milk", "Neilson",
            "Dairy", "Walmart", 5.00, 1, "L"
        );

        milk.setPromotion(new Promotion(
            "20% off Milk",
            20.0,
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 11, 5),
            false,
            1
        ));

        PromotionService service = new PromotionService();

        double finalTotal = service.getDiscountedTotal(
            milk, 2, false,
            LocalDate.of(2026, 10, 31)
        );

        assertEquals(8.00, finalTotal, 0.001);
    }
}
