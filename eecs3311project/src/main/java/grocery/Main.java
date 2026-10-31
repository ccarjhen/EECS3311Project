package grocery;

import java.time.LocalDate;
import java.util.List;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.StoreAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;




public class Main {
    public static void main(String[] args) {
        // System.out.println("EECS3311 Grocery Project!");

        // Product milk = new Product(
        //     "P001", "2% Milk", "Neilson", "Dairy", "Longos", 6.49, 4, "L"
        // );

        // Product bread = new Product(
        //     "P002", "White Bread", "Wonder", "Bakery", "Walmart", 3.49, 675, "g"
        // );

        // ShoppingItem milkItem = new ShoppingItem("Milk", "Dairy", 2);
        // milkItem.setProduct(milk);

        // ShoppingItem breadItem = new ShoppingItem("Bread", "Bakery", 1);
        // breadItem.setProduct(bread);

        // ShoppingPlan plan = new ShoppingPlan("Weekly Groceries", 50.00);

        // plan.addItem(milkItem);
        // plan.addItem(breadItem);

        // for (ShoppingItem item : plan.getShoppingItems()) {
        //     System.out.println(item.getName() + "  x" + item.getQuantity() + " = $" + item.getSubtotal());
        // }

        // System.out.println("Total: $" + plan.calculateTotal());
        // System.out.println("On Budget: " + plan.isOnBudget());
        
        // milkItem.checkPurchased();
        // System.out.println("Milk purchased: " + milkItem.isPurchased());

        // // Initialize the store adapter for Longos
        // StoreAdapter store = new LongosAdapter();

        // List<Product> results = store.search("Milk");

        // for (Product product : results) {
        //     System.out.println(product);
        // }

        // StoreAdapter longos = new LongosAdapter();
        // StoreAdapter walmart = new WalmartAdapter();
        // StoreAdapter noFrills = new NoFrillsAdapter();

        // // Search for products across all stores

        // ProductSearchService searchService = new ProductSearchService(List.of(longos, walmart, noFrills));

        // List<Product> results = searchService.search("Milk");

        // System.out.println("=== Search Results ===");
        // for (Product product : results) {
        //     System.out.println(product);
        // }

        // System.out.println("Total results: " + results.size());

        // // Price comparison for the search results

        // PriceComparisonService comparisonService = new PriceComparisonService();

        // List<Product> sorted = comparisonService.comparePrices(results);


        // System.out.println("===Price Comparison===");
        // for (Product product : sorted) {
        //     System.out.println(
        //     product.getName() + " | " +
        //     product.getStore() + " | $" +
        //     product.getPrice() + " | Unit Price: $" +
        //     product.getUnitPrice() + "/" +
        //     product.getUnit()
        //     );
        // }

        // // Price Promotion
        // PromotionService promotionService = new PromotionService();

        // List<Product> resultsPromotion = searchService.search("Milk");

        // int quantity = 2;
        // boolean hasMembership = true;
        // LocalDate today = LocalDate.now();

        // System.out.println("=== Promotion Detection ===");

        // for (Product product : resultsPromotion) {
        //     double regularTotal = product.getPrice() * quantity;

        //     String status = promotionService.checkPromotions(product, quantity, hasMembership, today);

        //     double discount = promotionService.calculateDiscount(product, quantity, hasMembership, today);

        //     double finalTotal = promotionService.getDiscountedTotal(product, quantity, hasMembership, today);

        //     System.out.println("------------------------");
        //     System.out.println(product.getName());
        //     System.out.println("Store: " + product.getStore());
        //     System.out.printf("Regular total: $%.2f%n", regularTotal);
        //     System.out.println("Promotion status: " + status);
        //     System.out.printf("Discount: $%.2f%n", discount);
        //     System.out.printf("Final total: $%.2f%n", finalTotal);

        // }

        System.out.println("=====TEST=====");

        // Store Adapters
        StoreAdapter longos = new LongosAdapter();
        StoreAdapter walmart = new WalmartAdapter();
        StoreAdapter noFrills = new NoFrillsAdapter();

        // Initialize services
        ProductSearchService searchService = new ProductSearchService(List.of(longos, walmart, noFrills));
        PromotionService promotionService = new PromotionService();

        // Test 1 (F05 Product Search)
        System.out.println("\n=== Test 1 (F05 Product Search) ===");

        List<Product> milkResults = searchService.search("milk");

        for (Product product : milkResults) {
            System.out.println(product);
        }

        System.out.println(
            "Total products found: " + milkResults.size()
        );

        // Test 2 (F06 Price Comparison)
        System.out.println("\n=== Test 2 (F06 Price Comparison) ===");

         // Compare matching products with the same units
        List<Product> comparableMilk = milkResults.stream()
            .filter(p -> p.getUnit().equalsIgnoreCase("L"))
            .filter(p -> p.getSize() > 0)
            .toList();

        comparableMilk.stream()
            .sorted((a, b) -> Double.compare(
                a.getPrice() / a.getSize(),
                b.getPrice() / b.getSize()
            ))
            .forEach(product -> {
                System.out.printf(
                    "%s | %s | $%.2f | $%.2f/L%n",
                    product.getName(),
                    product.getStore(),
                    product.getPrice(),
                    product.getPrice() / product.getSize()
                );
            });

        // Test 3 (F07 Promotions)
        System.out.println("\n=== Test 3 (F07 Promotions) ===");
        
        int quantity = 2;
        boolean hasMembership = true;
        LocalDate today = LocalDate.of(2026, 10, 31);

        for (Product product : milkResults) {

            String status = promotionService.checkPromotions(
                product, quantity, hasMembership, today
            );

            double discount = promotionService.calculateDiscount(
                product, quantity, hasMembership, today
            );

            double finalTotal = promotionService.getDiscountedTotal(
                product, quantity, hasMembership, today
            );

            System.out.println("\nProduct: " + product.getName());
            System.out.println("Store: " + product.getStore());
            System.out.println("Promotion status: " + status);

            System.out.printf(
                "Discount: $%.2f%n", discount
            );

            System.out.printf(
                "Final total: $%.2f%n", finalTotal
            );
        }

        // Test 4 (Shopping Item)
        System.out.println("\n=== Test 4 (Shopping Item) ===");

        if (!milkResults.isEmpty()) {
            Product milk = milkResults.get(0);

            ShoppingItem milkItem = new ShoppingItem("Milk", "Dairy", 2);
            milkItem.setProduct(milk);

            System.out.println("Item: " + milkItem.getName());
            System.out.println("Quantity: " + milkItem.getQuantity());

            System.out.printf(
                "Subtotal: $%.2f%n",
                milkItem.getSubtotal()
            );

            milkItem.checkPurchased();

            System.out.println(
                "Purchased: " + milkItem.isPurchased()
            );

            // Test 5 (Shopping Plan)
            System.out.println("\n=== Test 5 (Shopping Plan) ===");

            ShoppingPlan plan = new ShoppingPlan("Weekly Groceries", 50.00);

            plan.addItem(milkItem);

            List<Product> breadResults = searchService.search("bread");

            
            if (!breadResults.isEmpty()) {

                ShoppingItem breadItem =
                    new ShoppingItem("Bread", "Bakery", 1);

                breadItem.setProduct(breadResults.get(0));

                plan.addItem(breadItem);
            }

            System.out.println("Plan: " + plan.getName());

            for (ShoppingItem item : plan.getShoppingItems()) {

                System.out.printf(
                    "%s x%d = $%.2f%n",
                    item.getName(),
                    item.getQuantity(),
                    item.getSubtotal()
                );
            }

            System.out.printf(
                "Estimated total: $%.2f%n",
                plan.calculateTotal()
            );

            System.out.println(
                "Within budget: " + plan.isOnBudget()
            );

        }

        // Test 6 (User Preference)
        System.out.println("\n=== Test 6 (User Preference) ===");

        UserPreferences preferences = new UserPreferences();
        
        preferences.addPreferredStore("Longos");
        preferences.addPreferredBrand("Neilson");
        preferences.addDietaryRestriction("Vegetarian");
        preferences.addAllergy("Peanuts");
        preferences.addExcludedItem("Pork");

        System.out.println(
            "Pork excluded: " +
            preferences.isExcludedItem("Pork")
        );

        System.out.println(
            "Chicken excluded: " +
            preferences.isExcludedItem("Chicken")
        );

    }
}