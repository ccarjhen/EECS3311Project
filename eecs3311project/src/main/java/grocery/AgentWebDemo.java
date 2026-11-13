
package grocery;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import grocery.agent.ClaudeClient;
import grocery.agent.RequestedItem;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.agent.WebPlanResult;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.WebProductListing;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;

public class AgentWebDemo {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        ProductSearchService localSearch =
            new ProductSearchService(
                List.of(
                    // new LongosAdapter(),
                    // new WalmartAdapter(),
                    // new NoFrillsAdapter()
                )
            );

        PromotionService promotions =
            new PromotionService();

        ClaudeClient claude = new ClaudeClient();

        ShoppingAgent agent =
            new ShoppingAgent(
                claude,
                localSearch,
                promotions
            );

        WebProductSearchService webSearch =
            new WebProductSearchService(claude);

        WebListingExtractor extractor =
            new WebListingExtractor(claude);

        System.out.println("""
            
            ======================================
                 AI GROCERY COMPANION AGENT
                     WEB SHOPPING MODE
            ======================================
            """);

        System.out.print("Shopping request: ");
        String prompt = scanner.nextLine().trim();

        System.out.print("Budget ($): ");
        double budget = Double.parseDouble(
            scanner.nextLine().trim()
        );

        System.out.print(
            "Use online listed prices as estimates? (y/n): "
        );

        boolean useEstimates =
            scanner.nextLine().trim().equalsIgnoreCase("y");

        ShoppingRequest request =
            new ShoppingRequest(prompt, budget);

        System.out.println(
            "\nGenerating AI shopping plan..."
        );

        // One complete AI workflow.
        WebPlanResult result = agent.createWebPlan(
            request,
            webSearch,
            extractor,
            useEstimates,
            false,
            LocalDate.now()
        );

        // =====================================
        // DISPLAY SELECTED SHOPPING PLAN
        // =====================================

        ShoppingPlan plan = result.plan();

        System.out.println(
            "\n=== RECOMMENDED SHOPPING PLAN ==="
        );

        if (plan.getShoppingItems().isEmpty()) {
            System.out.println(
                "No items could be optimized."
            );
        }

        for (ShoppingItem item :
                plan.getShoppingItems()) {

            Product product = item.getProduct();

            System.out.printf(
                "%s x%d%n",
                item.getName(),
                item.getQuantity()
            );

            System.out.println(
                "  Selected: " + product.getName()
            );

            System.out.println(
                "  Store: " + product.getStore()
            );

            System.out.printf(
                "  Listed price: $%.2f%n",
                product.getPrice()
            );

            System.out.printf(
                "  Estimated subtotal: $%.2f%n%n",
                item.getSubtotal()
            );
        }

        System.out.printf(
            "Estimated basket total: $%.2f%n",
            plan.calculateTotal()
        );

        System.out.printf(
            "Budget: $%.2f%n",
            plan.getEstimatedBudget()
        );

        // =====================================
        // DISPLAY PRODUCT ALTERNATIVES
        // =====================================

        System.out.println(
            "\n=== PRODUCT CANDIDATES ==="
        );

        result.candidatesByItem().forEach(
            (itemName, candidates) -> {

                System.out.println(
                    "\n" + itemName + ":"
                );

                if (candidates.isEmpty()) {
                    System.out.println(
                        "  No suitable listings found."
                    );
                }

                for (int i = 0; i < candidates.size(); i++) {

                    WebProductListing listing =
                        candidates.get(i);

                    System.out.printf(
                        "  %d. %s | %s | $%.2f | %.2f %s%n",
                        i + 1,
                        listing.name(),
                        listing.store(),
                        listing.price(),
                        listing.size(),
                        listing.unit()
                    );

                    System.out.println(
                        "     Source: " +
                        listing.sourceUrl()
                    );
                }
            }
        );

        // =====================================
        // DISPLAY UNRESOLVED ITEMS
        // =====================================

        if (!result.unresolvedItems().isEmpty()) {

            System.out.println(
                "\n=== UNRESOLVED ITEMS ==="
            );

            for (RequestedItem item :
                    result.unresolvedItems()) {

                System.out.println(
                    "- " + item.name()
                );
            }
        }

        // =====================================
        // DISPLAY WARNINGS
        // =====================================

        if (!result.warnings().isEmpty()) {

            System.out.println(
                "\n=== WARNINGS ==="
            );

            for (String warning : result.warnings()) {
                System.out.println("- " + warning);
            }
        }

        System.out.println(
            "\nPlan complete: " + result.isComplete()
        );

        System.out.println(
            "Price status: " +
            (result.estimatedPricesUsed()
                ? "Estimated web-listed prices"
                : "Estimates not authorized")
        );

        System.out.println(
            "\nPrices and stock should be confirmed " +
            "with the retailer before purchasing."
        );
    }
}
