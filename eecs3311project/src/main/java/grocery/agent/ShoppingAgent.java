
package grocery.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.OptimizationStrat;

public class ShoppingAgent {

    private final LLMClient llm;
    private final ProductSearchService searchService;
    private final PromotionService promotionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ShoppingAgent(
            LLMClient llm,
            ProductSearchService searchService,
            PromotionService promotionService) {

        this.llm = llm;
        this.searchService = searchService;
        this.promotionService = promotionService;
    }

    // F01 + F02 + F04
    public List<RequestedItem> createItemList(
            ShoppingRequest request) throws Exception {

        String prompt = """
            You are a grocery shopping planning assistant.

            Interpret the user's goal and generate the groceries needed.

            Respond with ONLY valid JSON, in exactly this format:

            {
              "items": [
                {
                  "name": "Milk",
                  "category": "Dairy",
                  "quantity": 1
                }
              ]
            }

            Rules:
            - Use short, common grocery item names.
            - Quantity means number of packages, not grams or litres.
            - Quantity must be a positive integer.
            - Use categories such as Dairy, Bakery, Produce,
              Meat, Breakfast, Beverages, or Other.
            - Only propose groceries relevant to the request.
            - Do not invent prices, brands, promotions or stores.
            - Do not include Markdown or explanations.
            - Do not claim that products are allergy-safe.
            - If information is insufficient, do not guess
              sensitive dietary requirements.

            Shopper request:
            """ + request.prompt() +
            "\nBudget: $" + request.budget();

        String response = llm.ask(prompt);

        JsonNode root = mapper.readTree(response);
        JsonNode itemsNode = root.path("items");

        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalStateException(
                "AI did not provide a valid shopping list"
            );
        }

        if (itemsNode.size() > 30) {
            throw new IllegalStateException(
                "AI returned too many items"
            );
        }

        List<RequestedItem> items = new ArrayList<>();

        for (JsonNode node : itemsNode) {

            String name = node.path("name").asText("");
            String category = node.path("category").asText("Other");
            JsonNode quantityNode = node.path("quantity");

            if (!quantityNode.isIntegralNumber() ||
                    !quantityNode.canConvertToInt()) {
                throw new IllegalStateException(
                    "AI returned an invalid quantity"
                );
            }

            items.add(new RequestedItem(
                name,
                category,
                quantityNode.asInt()
            ));
        }

        return items;
    }

    // F03: Convert the AI items into a ShoppingPlan
    public ShoppingPlan buildPlan(
            ShoppingRequest request,
            List<RequestedItem> requestedItems,
            boolean hasMembership,
            LocalDate date
            ) {

        ShoppingPlan initialPlan =
            new ShoppingPlan(
                "AI Shopping Plan",
                request.budget()
            );

        for (RequestedItem item : requestedItems) {

            // Search the actual store catalogues
            var matchingProducts = searchService.search(item.name());

            // Use the catalogue category when available
            String category = matchingProducts.isEmpty()
                ? item.category()
                : matchingProducts.get(0).getCategory();

            ShoppingItem shoppingItem = new ShoppingItem(
                item.name(),
                category,
                item.quantity()
            );

            initialPlan.addItem(shoppingItem);
        }

        UserPreferences preferences = new UserPreferences();

        OptimizationStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            hasMembership,
            date,
            preferences
        );


        return strategy.optimize(initialPlan);
    }

    // Complete AI-assisted workflow
    public ShoppingPlan createPlan(
            ShoppingRequest request,
            boolean hasMembership,
            LocalDate date) throws Exception {

        List<RequestedItem> items = createItemList(request);

        return buildPlan(
            request,
            items,
            hasMembership,
            date
             // Pass default preferences for now
        );
    }
}
