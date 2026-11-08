
package grocery;

import java.time.LocalDate;
import java.util.List;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.agent.ClaudeClient;
import grocery.agent.LLMClient;
import grocery.agent.RequestedItem;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;

public class AgentDemo {

    public static void main(String[] args) throws Exception {

        ProductSearchService searchService =
            new ProductSearchService(
                List.of(
                    new LongosAdapter(),
                    new WalmartAdapter(),
                    new NoFrillsAdapter()
                )
            );

        PromotionService promotionService =
            new PromotionService();

        LLMClient llm = new ClaudeClient();

        ShoppingAgent agent = new ShoppingAgent(
            llm,
            searchService,
            promotionService
        );

        ShoppingRequest request = new ShoppingRequest(
            "I need milk, bread and eggs for breakfast",
            35.00
        );

        System.out.println("=== AI GROCERY COMPANION ===");

        // First verify the actual LLM response
        List<RequestedItem> requestedItems =
            agent.createItemList(request);

        System.out.println("\nAI-generated item list:");

        for (RequestedItem item : requestedItems) {
            System.out.printf(
                "%s | %s | Quantity: %d%n",
                item.name(),
                item.category(),
                item.quantity()
            );
        }

        // Then run the actual shopping optimization
        ShoppingPlan plan = agent.buildPlan(
            request,
            requestedItems,
            false,
            LocalDate.now()
        );

        System.out.println("\nOptimized shopping plan:");

        for (ShoppingItem item : plan.getShoppingItems()) {
            Product product = item.getProduct();

            System.out.printf(
                "%s x%d | %s | $%.2f%n",
                item.getName(),
                item.getQuantity(),
                product.getStore(),
                item.getSubtotal()
            );
        }

        System.out.printf(
            "\nRegular total: $%.2f%n",
            plan.calculateTotal()
        );
    }
}
