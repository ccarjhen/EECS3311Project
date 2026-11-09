
package grocery.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.adapter.WebStoreAdapter;
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


    // ========================================
    // AI WEB SHOPPING PLAN
    // F01, F02, F03, F04, F05, F09
    // ========================================

    
    public WebPlanResult createWebPlan(
            ShoppingRequest request,
            WebProductSearchService webSearch,
            WebListingExtractor extractor,
            boolean useEstimatedPrices,
            boolean hasMembership,
            LocalDate date) throws Exception {

        if (request == null || webSearch == null ||
                extractor == null || date == null) {
            throw new IllegalArgumentException(
                "Web planning arguments cannot be null"
            );
        }

        // 1. Claude generates the shopping list.
        List<RequestedItem> requestedItems =
            createItemList(request);

        // 2. Store all accepted web candidates.
        WebStoreAdapter webAdapter = new WebStoreAdapter();

        Map<String, List<WebProductListing>> candidatesByItem =
            new LinkedHashMap<>();

        List<WebProductListing> acceptedListings =
            new ArrayList<>();

        List<RequestedItem> unresolved =
            new ArrayList<>();

        List<String> warnings =
            new ArrayList<>();

        // Limit the number of web searches per request.
        int searchLimit = Math.min(requestedItems.size(), 10);

        // 3. Search online for each grocery item.
        for (int i = 0; i < requestedItems.size(); i++) {

            RequestedItem item = requestedItems.get(i);

            if (i >= searchLimit) {
                unresolved.add(item);
                warnings.add(
                    item.name() + ": web search limit reached"
                );
                continue;
            }

            System.out.println(
                "\nSearching online for: " + item.name()
            );

            try {
                WebSearchResult research =
                    webSearch.search(item.name());

                List<WebProductListing> extracted =
                    extractor.extract(
                        research,
                        item.category()
                    );

                // 4. Filter matching products.
                // Sort by listed package price, then keep 3.
                List<WebProductListing> candidates =
                    extracted.stream()
                        .filter(p ->
                            matchesRequestedItem(item, p)
                        )
                        .filter(p ->
                            Double.isFinite(p.price()) &&
                            p.price() > 0 &&
                            Double.isFinite(p.size()) &&
                            p.size() > 0
                        )
                        .sorted(Comparator.comparingDouble(
                            WebProductListing::price
                        ))
                        .distinct()
                        .limit(3)
                        .toList();

                candidatesByItem.put(
                    item.name(),
                    candidates
                );

                if (candidates.isEmpty()) {
                    unresolved.add(item);
                    warnings.add(
                        item.name() +
                        ": no suitable priced listings found"
                    );
                    continue;
                }

                if (!useEstimatedPrices) {
                    unresolved.add(item);
                    warnings.add(
                        item.name() +
                        ": estimated prices not authorized for optimization"
                    );
                    continue;
                }

                // 5. Add candidates to the web adapter.
                for (WebProductListing listing : candidates) {
                    webAdapter.addApprovedListing(listing);
                    acceptedListings.add(listing);
                }

            } catch (Exception ex) {
                unresolved.add(item);
                warnings.add(
                    item.name() +
                    ": web search failed - " +
                    ex.getMessage()
                );
            }
        }

        // 6. Use web listings in ProductSearchService.
        ProductSearchService webProducts =
            new ProductSearchService(
                List.of(webAdapter)
            );

        ShoppingPlan initialPlan =
            new ShoppingPlan(
                "AI Web Shopping Plan",
                request.budget()
            );

        for (RequestedItem item : requestedItems) {

            if (unresolved.contains(item)) {
                continue;
            }

            initialPlan.addItem(
                new ShoppingItem(
                    item.name(),
                    item.category(),
                    item.quantity()
                )
            );
        }

        // 7. Optimize using your existing Strategy pattern.
        ShoppingPlan optimizedPlan =
            new ShoppingPlan(
                "AI Web Shopping Plan",
                request.budget()
            );

        // Optimize items individually so one missing item
        // does not invalidate the whole shopping plan.
        for (ShoppingItem item :
                initialPlan.getShoppingItems()) {

            ShoppingPlan singleItemPlan =
                new ShoppingPlan(
                    "Single Item Plan",
                    request.budget()
                );

            singleItemPlan.addItem(item);

            try {
                OptimizationStrat strategy =
                    new CheapestStrat(
                        webProducts,
                        promotionService,
                        hasMembership,
                        date,
                        new UserPreferences()
                    );

                ShoppingPlan optimizedItem =
                    strategy.optimize(singleItemPlan);

                for (ShoppingItem selected :
                        optimizedItem.getShoppingItems()) {

                    optimizedPlan.addItem(selected);
                }

            } catch (IllegalStateException ex) {

                RequestedItem missing = requestedItems.stream()
                    .filter(r ->
                        r.name().equalsIgnoreCase(
                            item.getName()
                        )
                    )
                    .findFirst()
                    .orElse(null);

                if (missing != null &&
                        !unresolved.contains(missing)) {
                    unresolved.add(missing);
                }

                warnings.add(
                    item.getName() +
                    ": optimization failed - " +
                    ex.getMessage()
                );
            }
        }

        // 8. Check estimated budget.
        double total = optimizedPlan.calculateTotal();

        if (total > request.budget()) {
            warnings.add(
                String.format(
                    "Estimated total $%.2f exceeds budget $%.2f",
                    total,
                    request.budget()
                )
            );
        }

        if (useEstimatedPrices &&
                !acceptedListings.isEmpty()) {
            warnings.add(
                "Web-listed prices are estimates. " +
                "Retailer prices and availability may differ."
            );
        }

        return new WebPlanResult(
            optimizedPlan,
            acceptedListings,
            unresolved,
            warnings,
            candidatesByItem,
            useEstimatedPrices
        );
    }


    // Check whether a retrieved listing matches the
    // requested product rather than choosing unrelated food.
    private boolean matchesRequestedItem(
            RequestedItem item,
            WebProductListing listing) {

        String requestedName =
            item.name().trim().toLowerCase(Locale.ROOT);

        String listedName =
            listing.name().trim().toLowerCase(Locale.ROOT);

        return listedName.contains(requestedName)
            && listing.category().equalsIgnoreCase(
                item.category()
            );
    }

}
