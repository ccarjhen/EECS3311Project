
package grocery.cli;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.agent.ClaudeClient;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.MarkPurchasedCommand;
import grocery.command.RemoveItemCommand;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.PriceComparisonService;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;
import grocery.strategy.OptimizationStrat;

public class ShoppingCLI {

    private final Scanner scanner = new Scanner(System.in);

    private final ProductSearchService searchService;
    private final PromotionService promotionService;
    private final PriceComparisonService comparisonService;
    private final ShoppingPlanPricingService pricingService;
    private final ShoppingController controller;
    private final UserPreferences preferences;

    private ShoppingAgent agent;
    private ShoppingPlan currentPlan;
    private boolean hasMembership = false;

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public ShoppingCLI() {

        searchService = new ProductSearchService(
            List.of(
                new LongosAdapter(),
                new WalmartAdapter(),
                new NoFrillsAdapter()
            )
        );

        promotionService = new PromotionService();
        comparisonService = new PriceComparisonService();
        pricingService =
            new ShoppingPlanPricingService(promotionService);

        controller = new ShoppingController(searchService);

        preferences = new UserPreferences();

        // agent = new ShoppingAgent(
        //     new ClaudeClient(),
        //     searchService,
        //     promotionService
        // );
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
            0. Exit
            ====================================
            """);
    }

    // ========================================
    // F01-F04: AI PLAN GENERATION
    // ========================================

    private void generateAIPlan() throws Exception {
        if (agent == null) {
            agent = new ShoppingAgent(
                new ClaudeClient(),
                searchService,
                promotionService
            );
        }

        System.out.println("\n=== AI SHOPPING PLAN ===");

        String prompt = readText(
            "Describe your shopping goal: "
        );

        double budget = readDouble("Enter budget ($): ");

        if (budget <= 0) {
            System.out.println("Budget must be positive.");
            return;
        }

        String member = readText(
            "Do you have applicable loyalty memberships? (y/n): "
        );

        hasMembership = member.equalsIgnoreCase("y");

        ShoppingRequest request =
            new ShoppingRequest(prompt, budget);

        System.out.println("\nGenerating shopping plan...");

        ShoppingPlan generated = agent.createPlan(
            request,
            hasMembership,
            LocalDate.now()
        );

        setCurrentPlan(generated);

        System.out.println("\nShopping plan generated!");
        viewShoppingPlan();
    }

    // ========================================
    // F03: VIEW SHOPPING PLAN
    // ========================================

    private void viewShoppingPlan() {

        if (!hasPlan()) {
            return;
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
            double total = pricingService.calculatePlanTotal(
                currentPlan,
                hasMembership,
                LocalDate.now()
            );

            System.out.printf(
                "Estimated total after discounts: $%.2f%n",
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

        String query = readText("Search product: ");

        List<Product> results = controller.search(query);

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

        String query = readText(
            "Enter product name (e.g. 2% Milk): "
        );

        List<Product> results = controller.search(query);

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

        String query = readText("Search product: ");
        int quantity = readInt("Quantity: ");

        if (quantity <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }

        List<Product> products = controller.search(query);

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

            System.out.println("Promotion: " + status);
            System.out.printf("Total: $%.2f%n%n", total);
        }
    }

    // ========================================
    // F09: SHOPPING OPTIMIZATION
    // ========================================

    private void optimizePlan() {

        if (!hasPlan()) {
            return;
        }

        System.out.println("""
            
            === OPTIMIZATION STRATEGIES ===
            1. Cheapest
            2. Fewest Stores
            3. One Store
            """);

        int choice = readInt("Choose strategy: ");

        OptimizationStrat strategy;

        switch (choice) {

            case 1 -> strategy = new CheapestStrat(
                searchService,
                promotionService,
                hasMembership,
                LocalDate.now(),
                preferences
            );

            case 2 -> strategy = new FewestStoresStrat(
                searchService,
                promotionService,
                hasMembership,
                LocalDate.now(),
                preferences
            );

            case 3 -> {

                System.out.println("""
                    1. Longos
                    2. Walmart
                    3. No Frills
                    """);

                int storeChoice = readInt("Choose store: ");

                String store = switch (storeChoice) {
                    case 1 -> "Longos";
                    case 2 -> "Walmart";
                    case 3 -> "No Frills";
                    default -> null;
                };

                if (store == null) {
                    System.out.println("Invalid store.");
                    return;
                }

                strategy = new OneStoreStrat(
                    searchService,
                    promotionService,
                    hasMembership,
                    LocalDate.now(),
                    store,
                    preferences
                );
            }

            default -> {
                System.out.println("Invalid strategy.");
                return;
            }
        }

        // Execute through ShoppingController
        controller.setOptimizationStrategy(strategy);

        // Assign products again based on selected strategy
        ShoppingPlan result = controller.optimize(currentPlan);

        setCurrentPlan(result);

        System.out.println("\nOptimization complete.");
        viewShoppingPlan();
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

            List<Product> products = controller.search(name);

            // Basic first-match selection for CLI
            if (!products.isEmpty()) {
                item.setProduct(products.get(0));
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
}
