
package grocery.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.adapter.CachedWebStoreAdapter;
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

    // Maximum limits for the shopping plan and candidate items per request
    private static final int MAX_PLAN_ITEMS = 10;
    private static final int MAX_CANDIDATES_PER_ITEM = 3;


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
            LocalDate date,
            UserPreferences preferences
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

        OptimizationStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            hasMembership,
            date,
            preferences
        );


        return strategy.optimize(initialPlan);
    }


    public ShoppingPlan buildPlan(
            ShoppingRequest request,
            List<RequestedItem> items,
            boolean hasMembership,
            LocalDate date) {

        return buildPlan(
            request,
            items,
            hasMembership,
            date,
            new UserPreferences()
        );
    }


    
    // Existing callers can continue using this version.
    public ShoppingPlan createPlan(
            ShoppingRequest request,
            boolean hasMembership,
            LocalDate date) throws Exception {

        return createPlan(
            request,
            hasMembership,
            date,
            new UserPreferences()
        );
    }

    // GUI uses this version with saved preferences.
    public ShoppingPlan createPlan(
            ShoppingRequest request,
            boolean hasMembership,
            LocalDate date,
            UserPreferences preferences) throws Exception {

        if (preferences == null) {
            throw new IllegalArgumentException(
                "Preferences cannot be null."
            );
        }

        String preferencesContext = """
            
            Shopper preferences:
            Preferred stores: %s
            Preferred brands: %s
            Dietary restrictions: %s
            Allergies: %s
            Excluded items: %s

            Instructions:
            - Consider preferred stores and brands when possible.
            - Do not intentionally recommend excluded items.
            - Respect the stated dietary restrictions.
            - Do not claim that any product is allergy-safe.
            - If ingredient suitability cannot be verified,
            do not make safety guarantees.
            """.formatted(
                preferences.getPreferredStores(),
                preferences.getPreferredBrands(),
                preferences.getDietaryRestrictions(),
                preferences.getAllergies(),
                preferences.getExcludedItems()
            );

        ShoppingRequest enrichedRequest = new ShoppingRequest(
            request.prompt() + "\n" + preferencesContext,
            request.budget()
        );

        List<RequestedItem> items =
            createItemList(enrichedRequest);

        // Reject explicitly excluded item names.
        for (RequestedItem item : items) {
            String itemName =
                item.name().trim().toLowerCase(
                    java.util.Locale.ROOT
                );

            for (String excluded :
                    preferences.getExcludedItems()) {

                String excludedName =
                    excluded.trim().toLowerCase(
                        java.util.Locale.ROOT
                    );

                if (!excludedName.isEmpty()
                        && (itemName.equals(excludedName)
                            || itemName.contains(excludedName))) {

                    throw new IllegalStateException(
                        "Generated list contains an excluded item: "
                        + item.name()
                    );
                }
            }
        }

        return buildPlan(
            request,
            items,
            hasMembership,
            date,
            preferences
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

        
        List<RequestedItem> generatedItems = createItemList(request);

        List<RequestedItem> requestedItems =
            generatedItems.stream()
                .limit(MAX_PLAN_ITEMS)
                .toList();

        List<RequestedItem> excessItems =
            generatedItems.stream()
                .skip(MAX_PLAN_ITEMS)
                .toList();


        // 2. Store all accepted web candidates.
        // WebStoreAdapter webAdapter = new WebStoreAdapter();

        Map<String, List<WebProductListing>> candidatesByItem =
            new LinkedHashMap<>();

        List<WebProductListing> acceptedListings =
            new ArrayList<>();

        List<RequestedItem> unresolved =
            new ArrayList<>();

        List<String> warnings =
            new ArrayList<>();

        if (!excessItems.isEmpty()) {
            warnings.add(
                "The plan was limited to "
                + MAX_PLAN_ITEMS
                + " grocery items. "
                + excessItems.size()
                + " additional item(s) were not searched."
            );
        }

        // --Limit the number of web searches per request.--
        int searchLimit = Math.min(requestedItems.size(), 10);

        
        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {
            List<CompletableFuture<ItemSearchResult>> futures =
                new ArrayList<>();

            for (RequestedItem item : requestedItems) {

                futures.add(
                    CompletableFuture.supplyAsync(
                        () -> searchOneItem(
                            item,
                            webSearch,
                            extractor
                        ),
                        executor
                    )
                );
            }

            // Collect results in the original item order.
            for (CompletableFuture<ItemSearchResult> future : futures) {

                ItemSearchResult result = future.join();
                RequestedItem item = result.item();

                if (result.error() != null) {
                    unresolved.add(item);
                    warnings.add(
                        item.name() + ": " + result.error()
                    );
                    continue;
                }

                List<WebProductListing> candidates =
                    result.candidates();

                candidatesByItem.put(item.name(), candidates);

                if (candidates.isEmpty()) {
                    unresolved.add(item);
                    warnings.add(
                        item.name()
                        + ": no suitable priced listings found"
                    );
                    continue;
                }

                if (!useEstimatedPrices) {
                    unresolved.add(item);
                    warnings.add(
                        item.name()
                        + ": estimated prices not authorized"
                    );
                    continue;
                }

                for (WebProductListing listing : candidates) {
                    // webAdapter.addApprovedListing(listing);
                    acceptedListings.add(listing);
                }
            }

        } finally {
            executor.shutdown();
        }


        // 6. Use web listings in ProductSearchService.
        ProductSearchService webProducts =
            new ProductSearchService(
                List.of(
                    new CachedWebStoreAdapter(
                        candidatesByItem
                    )
                )
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

        String requested = item.name()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .trim();

        String productText = (
            listing.name() + " " + listing.brand()
        )
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ");

        String[] keywords = requested.split("\\s+");

        for (String keyword : keywords) {

            if (keyword.isBlank()) {
                continue;
            }

            boolean found =
                java.util.Arrays.stream(
                    productText.split("\\s+")
                ).anyMatch(word ->
                    word.equals(keyword)
                );

            if (!found) {
                return false;
            }
        }

        return true;
    }


    private record ItemSearchResult(
        RequestedItem item,
        List<WebProductListing> candidates,
        String error
    ) {}

    private ItemSearchResult searchOneItem(
            RequestedItem item,
            WebProductSearchService webSearch,
            WebListingExtractor extractor) {

        try {
            System.out.println(
                "Searching online for: " + item.name()
            );

            WebSearchResult research =
                webSearch.search(item.name());

            List<WebProductListing> extracted =
                extractor.extract(research, item.category());

            List<WebProductListing> candidates =
                extracted.stream()
                    .filter(p -> matchesRequestedItem(item, p))
                    .filter(p ->
                        Double.isFinite(p.price())
                        && p.price() > 0
                        && Double.isFinite(p.size())
                        && p.size() > 0
                    )
                    .sorted(Comparator.comparingDouble(
                        WebProductListing::price
                    ))
                    .distinct()
                    .limit(MAX_CANDIDATES_PER_ITEM)
                    .toList();

            System.out.printf(
                "%s: %d extracted, %d accepted%n",
                item.name(),
                extracted.size(),
                candidates.size()
            );

            return new ItemSearchResult(item, candidates, null);

        } catch (Exception e) {
            return new ItemSearchResult(
                item,
                List.of(),
                e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage()
            );
        }
    }

}
