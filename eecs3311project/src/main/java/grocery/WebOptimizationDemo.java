
package grocery;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import grocery.agent.ClaudeClient;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.model.WebProductListing;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import grocery.service.WebSearchResult;
import grocery.strategy.CheapestStrat;
import grocery.strategy.OptimizationStrat;

public class WebOptimizationDemo {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        ClaudeClient claude = new ClaudeClient();

        WebProductSearchService webSearch =
            new WebProductSearchService(claude);

        WebListingExtractor extractor =
            new WebListingExtractor(claude);

        // WebStoreAdapter webAdapter =
        //     new WebStoreAdapter();

        System.out.println("=== AI WEB SHOPPING OPTIMIZER ===");

        System.out.print("Product to search: ");
        String query = scanner.nextLine().trim();

        System.out.print("Product category: ");
        String category = scanner.nextLine().trim();

        System.out.print("Required number of packages: ");
        int quantity = Integer.parseInt(
            scanner.nextLine().trim()
        );

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "Quantity must be positive"
            );
        }

        // STEP 1: Search with Claude
        System.out.println("\nSearching online...");

        WebSearchResult research =
            webSearch.search(query);

        // STEP 2: Extract structured candidates
        List<WebProductListing> candidates =
            extractor.extract(research, category);

        if (candidates.isEmpty()) {
            System.out.println(
                "No price-bearing web listings could be extracted."
            );
            return;
        }

        // STEP 3: Review results
        for (WebProductListing listing : candidates) {

            System.out.println("\n------------------------");
            System.out.println("Product: " + listing.name());
            System.out.println("Brand: " + listing.brand());
            System.out.println("Store: " + listing.store());

            System.out.printf(
                "Reported price: $%.2f CAD%n",
                listing.price()
            );

            System.out.printf(
                "Size: %.2f %s%n",
                listing.size(),
                listing.unit()
            );

            System.out.println(
                "Source: " + listing.sourceUrl()
            );

            System.out.print(
                "After reviewing the source, approve this price? (y/n): "
            );

            String answer = scanner.nextLine().trim();

            if (answer.equalsIgnoreCase("y")) {
                // webAdapter.addApprovedListing(listing);
            }
        }

        // STEP 4: Use approved web products
        ProductSearchService searchService =
            new ProductSearchService(
                List.of()
            );

        if (searchService.search(query).isEmpty()) {
            System.out.println(
                "No approved listings available for optimization."
            );
            return;
        }

        // STEP 5: Create a shopping plan
        ShoppingPlan plan =
            new ShoppingPlan("Web Shopping Plan", 50.00);

        plan.addItem(
            new ShoppingItem(query, category, quantity)
        );

        // STEP 6: Run your existing Strategy pattern
        OptimizationStrat cheapest = new CheapestStrat(
            searchService,
            new PromotionService(),
            false,
            LocalDate.now(),
            new UserPreferences()
        );

        ShoppingPlan result = cheapest.optimize(plan);

        // STEP 7: Display the result
        System.out.println("\n=== OPTIMIZED WEB SHOPPING PLAN ===");

        for (ShoppingItem item : result.getShoppingItems()) {

            Product product = item.getProduct();

            System.out.printf(
                "%s x%d | %s | $%.2f%n",
                product.getName(),
                item.getQuantity(),
                product.getStore(),
                item.getSubtotal()
            );
        }

        System.out.printf(
            "\nEstimated total: $%.2f%n",
            result.calculateTotal()
        );

        System.out.println(
            "Note: Prices were approved for this demo; " +
            "current store availability is not guaranteed."
        );
    }
}
