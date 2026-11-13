
package grocery.cli;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import grocery.agent.ClaudeClient;
import grocery.agent.RequestedItem;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.agent.WebPlanResult;
import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.MarkPurchasedCommand;
import grocery.command.RemoveItemCommand;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.model.WebProductListing;
import grocery.service.PriceComparisonService;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;
import grocery.strategy.OptimizationStrat;
import grocery.util.StoreNameNormalizer;

public class ShoppingCLI {

    private final Scanner scanner = new Scanner(System.in);

    private final ProductSearchService searchService;
    private final PromotionService promotionService;
    private final PriceComparisonService comparisonService;
    private final ShoppingPlanPricingService pricingService;
    private final ShoppingController controller;
    private final UserPreferences preferences;

    private ProductSearchService activeSearchService;   

    private ShoppingAgent agent;
    private ShoppingPlan currentPlan;
    private boolean hasMembership = false;

    private WebProductSearchService webSearchService;
    private WebListingExtractor webListingExtractor;

    

    // Retain web-search information for later viewing.
    private WebPlanResult currentWebResult;

    // Track which type of plan is active.
    private boolean usingWebPlan = false;

    

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public ShoppingCLI() {

        searchService = new ProductSearchService(
            List.of(
                // new LongosAdapter(),
                // new WalmartAdapter(),
                // new NoFrillsAdapter()
            )
        );

        promotionService = new PromotionService();
        comparisonService = new PriceComparisonService();
        pricingService =
            new ShoppingPlanPricingService(promotionService);

        controller = new ShoppingController(searchService);

        preferences = new UserPreferences();

        activeSearchService = searchService;

    }

    // ========================================
    // START APPLICATION
    // ========================================

    public static void main(String[] args) {
        try {
            ShoppingCLI cli = new ShoppingCLI();
            cli.start();
        } catch (Exception e) {
            System.out.println(
                "Unable to start application: " + e.getMessage()
            );
        }
    }

    public void start() {

        boolean running = true;

        System.out.println("\nAI GROCERY COMPANION AGENT");

        while (running) {

            displayMenu();
            int choice = readInt("Enter choice: ");

            try {
                switch (choice) {
                    case 1 -> generateAIPlan();
                    case 2 -> viewShoppingPlan();
                    case 3 -> searchProducts();
                    case 4 -> comparePrices();
                    case 5 -> viewPromotions();
                    case 6 -> optimizePlan();
                    case 7 -> manageChecklist();
                    case 8 -> {
                        showWebCandidates();
                        showWebWarnings();
                    }
                    case 0 -> {
                        running = false;
                        System.out.println("Goodbye!");
                    }
                    default -> System.out.println(
                        "Invalid menu choice."
                    );
                }
            } catch (Exception e) {
                System.out.println(
                    "\nOperation failed: " + e.getMessage()
                );
            }
        }
    }

    private void displayMenu() {
        System.out.println("""
            
            ====================================
                 AI GROCERY COMPANION
            ====================================
            1. Generate AI Shopping Plan
            2. View Shopping Plan
            3. Search Products
            4. Compare Prices
            5. View Promotions
            6. Optimize Shopping Plan
            7. Manage Checklist
            8. View Web Product Alternatives
            0. Exit
            ====================================
            """);
    }

    // ========================================
    // F01-F04: AI PLAN GENERATION
    // ========================================

   
    private void generateAIPlan() throws Exception {

        System.out.println("""
            
            === GENERATE SHOPPING PLAN ===
            1. AI Planning with Web Search
            2. AI Planning with Sample Catalogue
            0. Back
            """);

        int mode = readInt("Choose mode: ");

        if (mode == 0) {
            return;
        }

        if (mode != 1 && mode != 2) {
            System.out.println("Invalid mode.");
            return;
        }

        String prompt = readText(
            "Describe your shopping goal: "
        );

        double budget = readDouble("Enter budget ($): ");

        if (budget <= 0) {
            System.out.println("Budget must be positive.");
            return;
        }

        ShoppingRequest request =
            new ShoppingRequest(prompt, budget);

        // Initialize Claude only when needed.
        if (agent == null) {
            ClaudeClient claude = new ClaudeClient();

            agent = new ShoppingAgent(
                claude,
                searchService,
                promotionService
            );

            webSearchService =
                new WebProductSearchService(claude);

            webListingExtractor =
                new WebListingExtractor(claude);
        }

        if (mode == 1) {
            generateWebPlan(request);
        } else {
            generateSamplePlan(request);
        }
    }


    private void generateWebPlan(
            ShoppingRequest request) throws Exception {

        System.out.println("\n=== WEB SHOPPING MODE ===");

        String answer = readText(
            "Use online listed prices as estimates? (y/n): "
        );

        boolean useEstimates =
            answer.equalsIgnoreCase("y");

        String membership = readText(
            "Do you have applicable loyalty memberships? (y/n): "
        );

        boolean selectedMembership =
            membership.equalsIgnoreCase("y");

        System.out.println(
            "\nGenerating AI web shopping plan..."
        );

        // Keep earlier plan intact if generation fails.
        WebPlanResult result = agent.createWebPlan(
            request,
            webSearchService,
            webListingExtractor,
            useEstimates,
            selectedMembership,
            LocalDate.now()
        );


        // // One fresh catalogue per web plan; do not add the same listings twice.
        // WebStoreAdapter adapter = new WebStoreAdapter();
        // for (WebProductListing listing : result.approvedListings()) {
        //     // Normalize store names before optimization so that, for example,
        //     // "No Frills Canada" and "No Frills" are treated as one store.
        //     WebProductListing normalized = new WebProductListing(
        //         listing.name(),
        //         listing.brand(),
        //         listing.category(),
        //         StoreNameNormalizer.normalize(listing.store()),
        //         listing.price(),
        //         listing.size(),
        //         listing.unit(),
        //         listing.sourceUrl()
        //     );
        //     adapter.addApprovedListing(normalized);
        // }

        // Switch to the new catalogue only after plan generation succeeds.
        // activeSearchService = new ProductSearchService(List.of(adapter));
        currentWebResult = result;
        usingWebPlan = true;
        hasMembership = selectedMembership;
        setCurrentPlan(result.plan());

        System.out.println("\nWeb shopping plan generated.");

        viewShoppingPlan();
        showWebCandidates();
        showWebWarnings();
    }


    private void generateSamplePlan(
            ShoppingRequest request) throws Exception {

        System.out.println("\n=== SAMPLE CATALOGUE MODE ===");

        String membership = readText(
            "Do you have applicable loyalty memberships? (y/n): "
        );

        boolean selectedMembership =
            membership.equalsIgnoreCase("y");

        System.out.println("\nGenerating shopping plan...");

        ShoppingPlan result = agent.createPlan(
            request,
            selectedMembership,
            LocalDate.now()
        );

        activeSearchService = searchService;
        currentWebResult = null;
        usingWebPlan = false;
        hasMembership = selectedMembership;

        setCurrentPlan(result);

        System.out.println("\nSample shopping plan generated.");
        System.out.println(
            "Note: Product prices come from the sample catalogues."
        );

        viewShoppingPlan();
    }


    private void showWebCandidates() {

        if (currentWebResult == null) {
            System.out.println(
                "No web search results available."
            );
            return;
        }

        System.out.println("\n=== WEB PRODUCT CANDIDATES ===");

        currentWebResult.candidatesByItem()
            .forEach((itemName, candidates) -> {

                System.out.println("\n" + itemName + ":");

                if (candidates.isEmpty()) {
                    System.out.println(
                        "  No suitable listings found."
                    );
                    return;
                }

                for (int i = 0; i < candidates.size(); i++) {

                    WebProductListing product =
                        candidates.get(i);

                    System.out.printf(
                        "%d. %s | %s | $%.2f CAD%n",
                        i + 1,
                        product.name(),
                        product.store(),
                        product.price()
                    );

                    System.out.printf(
                        "   Brand: %s | Size: %.2f %s%n",
                        product.brand(),
                        product.size(),
                        product.unit()
                    );

                    System.out.println(
                        "   Source: " + product.sourceUrl()
                    );
                }
            });
    }


    private void showWebWarnings() {

        if (currentWebResult == null) {
            return;
        }

        if (!currentWebResult.unresolvedItems().isEmpty()) {

            System.out.println("\n=== UNRESOLVED ITEMS ===");

            for (RequestedItem item :
                    currentWebResult.unresolvedItems()) {

                System.out.println("- " + item.name());
            }
        }

        if (!currentWebResult.warnings().isEmpty()) {

            System.out.println("\n=== WARNINGS ===");

            for (String warning :
                    currentWebResult.warnings()) {

                System.out.println("- " + warning);
            }
        }

        System.out.println(
            "\nShopping plan complete: " +
            currentWebResult.isComplete()
        );

        System.out.println(
            "Price status: " +
            (currentWebResult.estimatedPricesUsed()
                ? "Estimated web-listed prices"
                : "Web prices not authorized for optimization")
        );
    }





    // ========================================
    // F03: VIEW SHOPPING PLAN
    // ========================================

    private void viewShoppingPlan() {

        if (!hasPlan()) {
            return;
        }

        if (usingWebPlan) {
            System.out.println(
                "Price source: Web listings (estimates, not verified)"
            );

            if (currentWebResult != null &&
                    !currentWebResult.isComplete()) {
                System.out.println(
                    "WARNING: This is a partial shopping plan."
                );
            }
        } else {
            System.out.println(
                "Price source: Sample catalogue"
            );
        }

        System.out.println("\n=== CURRENT SHOPPING PLAN ===");
        System.out.println("Plan: " + currentPlan.getName());

        List<ShoppingItem> items =
            currentPlan.getShoppingItems();

        for (int i = 0; i < items.size(); i++) {

            ShoppingItem item = items.get(i);
            Product product = item.getProduct();

            String status = item.isPurchased()
                ? "[X]" : "[ ]";

            System.out.printf(
                "%d. %s %s x%d%n",
                i + 1,
                status,
                item.getName(),
                item.getQuantity()
            );

            if (product != null) {
                System.out.printf(
                    "   %s | %s | $%.2f%n",
                    product.getName(),
                    product.getStore(),
                    item.getSubtotal()
                );
            } else {
                System.out.println(
                    "   No product assigned"
                );
            }
        }

        System.out.printf(
            "%nBudget: $%.2f%n",
            currentPlan.getEstimatedBudget()
        );

        // A complete effective total requires
        // every item to have an assigned product.
        boolean complete = items.stream()
            .allMatch(i -> i.getProduct() != null);

        if (complete) {
            if (usingWebPlan && currentWebResult != null
                    && !currentWebResult.isComplete()) {
                System.out.println("WARNING: Total excludes unresolved shopping items.");
            }
            double total = pricingService.calculatePlanTotal(
                currentPlan,
                hasMembership,
                LocalDate.now()
            );

            System.out.printf(
                usingWebPlan
                    ? "Estimated total (web-listed prices): $%.2f%n"
                    : "Estimated total after discounts: $%.2f%n",
                total
            );

            System.out.println(
                total <= currentPlan.getEstimatedBudget()
                    ? "Status: Within budget"
                    : "Status: Over budget"
            );
        } else {
            System.out.println(
                "Estimated total: Incomplete - some items have no product"
            );
        }
    }

    // ========================================
    // F05: PRODUCT SEARCH
    // ========================================

    private void searchProducts() {

        System.out.println("\n=== PRODUCT SEARCH ===");
        System.out.println(usingWebPlan
            ? "Searching products retrieved for the current web plan."
            : "Searching sample catalogue products.");

        String query = readText("Search product: ");

        List<Product> results = activeSearchService.search(query);

        if (results.isEmpty()) {
            System.out.println("No matching products found.");
            return;
        }

        printProducts(results);
    }

    // ========================================
    // F06: PRICE COMPARISON
    // ========================================

    private void comparePrices() {

        System.out.println("\n=== PRICE COMPARISON ===");
        System.out.println(usingWebPlan
            ? "Comparing previously retrieved web estimates."
            : "Comparing sample catalogue prices.");

        String query = readText(
            "Enter product name (e.g. 2% Milk): "
        );

        List<Product> results = activeSearchService.search(query);

        if (results.isEmpty()) {
            System.out.println("No products found.");
            return;
        }

        System.out.println("Available matching products:");
        printProducts(results);

        int index = readInt(
            "Choose reference product number: "
        ) - 1;

        if (index < 0 || index >= results.size()) {
            System.out.println("Invalid product selection.");
            return;
        }

        Product reference = results.get(index);

        List<Product> ranked =
            comparisonService.comparePrices(
                reference, results
            );

        System.out.println("\nComparable products:");

        for (Product product : ranked) {
            System.out.printf(
                "%s | %s | $%.2f | Normalized unit price: $%.2f%n",
                product.getName(),
                product.getStore(),
                product.getPrice(),
                comparisonService.getNormalizedUnitPrice(product)
            );
        }
    }

    // ========================================
    // F07: PROMOTION DETECTION
    // ========================================

    private void viewPromotions() {

        System.out.println("\n=== PROMOTION SEARCH ===");
        if (usingWebPlan) {
            System.out.println("Online promotions have not been checked or verified.");
        }

        String query = readText("Search product: ");
        int quantity = readInt("Quantity: ");

        if (quantity <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }

        List<Product> products = activeSearchService.search(query);

        if (products.isEmpty()) {
            System.out.println("No products found.");
            return;
        }

        for (Product product : products) {

            String status = promotionService.checkPromotions(
                product,
                quantity,
                hasMembership,
                LocalDate.now()
            );

            double total = promotionService.getDiscountedTotal(
                product,
                quantity,
                hasMembership,
                LocalDate.now()
            );

            System.out.printf(
                "%s | %s%n",
                product.getName(),
                product.getStore()
            );

            if (usingWebPlan) {
                System.out.println("Promotion: Not verified online");
                System.out.printf("Estimated total before verified discounts: $%.2f%n%n",
                    product.getPrice() * quantity);
            } else {
                System.out.println("Promotion: " + status);
                System.out.printf("Total: $%.2f%n%n", total);
            }
        }

        
    }

    // ========================================
    // F09: SHOPPING OPTIMIZATION
    // ========================================

    
    private void optimizePlan() {

        if (!hasPlan()) {
            System.out.println(
                "Please generate a shopping plan first."
            );
            return;
        }

        ShoppingPlan originalPlan = currentPlan;

        System.out.println(
            "\n=== OPTIMIZATION STRATEGIES ==="
        );

        System.out.println("1. Cheapest");
        System.out.println("2. Fewest Stores");
        System.out.println("3. One Store");

        int choice = readInt("Choose strategy: ");

        if (choice < 1 || choice > 3) {
            System.out.println("Invalid strategy.");
            return;
        }

        OptimizationStrat strategy;

        switch (choice) {

            case 1 -> {
                strategy = new CheapestStrat(
                    activeSearchService,
                    promotionService,
                    hasMembership,
                    LocalDate.now(),
                    preferences
                );
            }

            case 2 -> {
                strategy = new FewestStoresStrat(
                    activeSearchService,
                    promotionService,
                    hasMembership,
                    LocalDate.now(),
                    preferences
                );
            }

            case 3 -> {

                List<String> stores = getAvailableStores();

                if (stores.isEmpty()) {
                    System.out.println(
                        "No retailers are available."
                    );
                    return;
                }

                System.out.println(
                    "\n=== AVAILABLE STORES ==="
                );

                for (int i = 0; i < stores.size(); i++) {
                    System.out.printf(
                        "%d. %s%n",
                        i + 1,
                        stores.get(i)
                    );
                }

                int storeChoice =
                    readInt("Choose store: ");

                if (storeChoice < 1 ||
                        storeChoice > stores.size()) {
                    System.out.println(
                        "Invalid store selection."
                    );
                    return;
                }

                String selectedStore =
                    stores.get(storeChoice - 1);

               strategy = new OneStoreStrat(
                    activeSearchService,
                    promotionService,
                    hasMembership,
                    LocalDate.now(),
                    selectedStore,
                    preferences
                );
            }

            default -> {
                return;
            }
        }

        try {

            // Create an optimized plan without overwriting
            // the current plan before success.
            ShoppingPlan optimized =
                strategy.optimize(originalPlan);

            // OptimizationSupport.buildPlan already copies purchased flags.
            // Other strategy implementations should do the same.

            setCurrentPlan(optimized);

            System.out.println(
                "\nShopping plan optimized successfully!"
            );

            viewShoppingPlan();

        } catch (IllegalStateException ex) {

            System.out.println(
                "\nOptimization could not complete: " +
                ex.getMessage()
            );

            System.out.println(
                "Your previous shopping plan was preserved."
            );
        }
    }


    // ========================================
    // F10: INTERACTIVE CHECKLIST
    // ========================================

    private void manageChecklist() {

        if (!hasPlan()) {
            return;
        }

        System.out.println("""
            
            === CHECKLIST MENU ===
            1. Add Item
            2. Remove Item
            3. Change Quantity
            4. Mark Purchased
            5. View Checklist
            0. Back
            """);

        int choice = readInt("Choose action: ");

        if (choice == 0) {
            return;
        }

        if (choice == 5) {
            viewShoppingPlan();
            return;
        }

        if (choice == 1) {

            String name = readText("Item name: ");
            String category = readText("Category: ");
            int quantity = readInt("Quantity: ");

            if (quantity <= 0) {
                System.out.println("Invalid quantity.");
                return;
            }

            ShoppingItem item =
                new ShoppingItem(name, category, quantity);

            List<Product> products = activeSearchService.search(name);

            // Only assign a product with a matching category.
            // Do not silently substitute data from the sample catalogue.
            products.stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category))
                .findFirst()
                .ifPresent(item::setProduct);

            if (item.getProduct() == null) {
                System.out.println("No matching product in the active catalogue. "
                    + "The item will be added without a price.");
            }

            controller.updateChecklist(
                new AddItemCommand(currentPlan, item)
            );

            System.out.println("Item added.");
            return;
        }

        viewShoppingPlan();

        int index = readInt("Select item number: ") - 1;

        List<ShoppingItem> items =
            currentPlan.getShoppingItems();

        if (index < 0 || index >= items.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        ShoppingItem item = items.get(index);

        switch (choice) {

            case 2 -> {
                controller.updateChecklist(
                    new RemoveItemCommand(currentPlan, item)
                );
                System.out.println("Item removed.");
            }

            case 3 -> {
                int quantity = readInt("New quantity: ");

                controller.updateChecklist(
                    new ChangeQuantityCommand(
                        currentPlan, item, quantity
                    )
                );

                System.out.println("Quantity updated.");
            }

            case 4 -> {
                controller.updateChecklist(
                    new MarkPurchasedCommand(currentPlan, item)
                );

                System.out.println("Item marked purchased.");
            }

            default -> System.out.println(
                "Invalid checklist action."
            );
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private void setCurrentPlan(ShoppingPlan plan) {
        currentPlan = plan;

        currentPlan.addObserver(updatedPlan ->
            System.out.println("[Observer] Shopping plan updated.")
        );
    }

    private boolean hasPlan() {
        if (currentPlan == null) {
            System.out.println(
                "No shopping plan exists. Generate one first."
            );
            return false;
        }
        return true;
    }

    private void printProducts(List<Product> products) {

        for (int i = 0; i < products.size(); i++) {

            Product p = products.get(i);

            System.out.printf(
                "%d. %s | %s | %s | $%.2f | %.2f %s%n",
                i + 1,
                p.getName(),
                p.getBrand(),
                p.getStore(),
                p.getPrice(),
                p.getSize(),
                p.getUnit()
            );
        }
    }

    private String readText(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    private int readInt(String message) {

        while (true) {
            try {
                return Integer.parseInt(readText(message));
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid whole number.");
            }
        }
    }

    private double readDouble(String message) {

        while (true) {
            try {
                double value =
                    Double.parseDouble(readText(message));

                if (Double.isFinite(value)) {
                    return value;
                }

                System.out.println("Enter a finite number.");

            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number.");
            }
        }
    }


    private List<String> getAvailableStores() {

        if (usingWebPlan && currentWebResult != null) {

            return currentWebResult.approvedListings()
                .stream()
                .map(WebProductListing::store)
                .map(StoreNameNormalizer::normalize)
                .distinct()
                .sorted()
                .toList();
        }

        return List.of("Longos", "Walmart", "No Frills");
    }

}
