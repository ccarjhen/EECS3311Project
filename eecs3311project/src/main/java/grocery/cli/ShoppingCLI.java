package grocery.cli;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import grocery.adapter.CachedWebStoreAdapter;
import grocery.agent.ClaudeClient;
import grocery.agent.RequestedItem;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.agent.WebPlanResult;
import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.RemoveItemCommand;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.model.WebProductListing;
import grocery.service.ChecklistRepository;
import grocery.service.PriceComparisonService;
import grocery.service.ProductSearchHistoryRepository;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.ShoppingPlanPricingService;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import grocery.service.WebSearchResult;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;
import grocery.strategy.OptimizationStrat;
import grocery.util.StoreNameNormalizer;

/** Console interface following the same five pages as the JavaFX application. */
public class ShoppingCLI {
    private final Scanner scanner = new Scanner(System.in);
    private final PromotionService promotions = new PromotionService();
    private final ShoppingPlanPricingService pricing = new ShoppingPlanPricingService(promotions);
    private final PriceComparisonService comparisons = new PriceComparisonService();
    private final ChecklistRepository checklists = new ChecklistRepository();
    private final ProductSearchHistoryRepository history = new ProductSearchHistoryRepository();
    private final UserPreferences preferences = new UserPreferences();
    private final Set<String> preferredStores = new LinkedHashSet<>();
    private final Set<String> preferredBrands = new LinkedHashSet<>();
    private final Set<String> restrictions = new LinkedHashSet<>();
    private final Set<String> allergies = new LinkedHashSet<>();
    private final Set<String> exclusions = new LinkedHashSet<>();
    private final Map<String, List<WebProductListing>> candidates = new LinkedHashMap<>();
    private final Map<String, List<WebProductListing>> searchResults = new LinkedHashMap<>();

    private ClaudeClient claude;
    private ShoppingAgent agent;
    private WebProductSearchService webSearch;
    private WebListingExtractor extractor;
    private ShoppingPlan currentPlan;
    private ShoppingPlan originalPlan;
    private ShoppingPlan optimizationBase;
    private WebPlanResult webPlan;
    private String activeOptimization = "Cheapest";
    private boolean membership;

    public static void main(String[] args) {
        new ShoppingCLI().start();
    }

    public void start() {
        while (true) {
            System.out.println("\n========== AI GROCERY COMPANION ==========");
            System.out.println("1. Home - Generate Shopping Plan");
            System.out.println("2. Shopping Plan");
            System.out.println("3. Products");
            System.out.println("4. Preferences");
            System.out.println("5. Checklists");
            System.out.println("0. Exit");
            String choice = input("Select: ");
            if (choice.equals("0")) { System.out.println("Goodbye!"); return; }
            try {
                switch (choice) {
                    case "1" -> homeMenu();
                    case "2" -> planMenu();
                    case "3" -> productsMenu();
                    case "4" -> preferencesMenu();
                    case "5" -> checklistsMenu();
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                System.out.println("Operation failed: " + e.getMessage());
                // Do not log request bodies, secrets, or API credentials.
            }
        }
    }

    private void ensureAI() {
        if (claude == null) {
            claude = new ClaudeClient();
            // The shopping agent is responsible for planning; the web service supplies products.
            agent = new ShoppingAgent(claude, new ProductSearchService(List.of()), promotions);
            webSearch = new WebProductSearchService(claude);
            extractor = new WebListingExtractor(claude);
        }
    }

    private void homeMenu() throws Exception {
        System.out.println("\n=== HOME: GENERATE SHOPPING PLAN ===");
        String goal = required("Describe your grocery shopping goal: ");
        double budget = positiveDouble("Budget (CAD): ");
        membership = yes("Do you have applicable loyalty memberships? (y/n): ");
        ensureAI();
        System.out.println("[HOME] Generating an AI plan and searching online for products...");
        // Do not erase an existing plan if an external request fails.
        String context = "";
        if (!preferredStores.isEmpty() || !preferredBrands.isEmpty()
                || !restrictions.isEmpty() || !allergies.isEmpty() || !exclusions.isEmpty()) {
            context = "\nShopping preferences: preferred stores " + preferredStores
                + "; preferred brands " + preferredBrands
                + "; dietary restrictions " + restrictions
                + "; allergies " + allergies
                + "; excluded ingredients/products " + exclusions
                + ". Do not assert allergy safety without checked ingredients.";
        }
        ShoppingRequest request = new ShoppingRequest(goal + context, budget);
        WebPlanResult result = agent.createWebPlan(request, webSearch, extractor,
            true, membership, LocalDate.now(),
            message -> System.out.println("[HOME] " + message));
        currentPlan = copyPlan(result.plan());
        originalPlan = copyPlan(result.plan());
        optimizationBase = copyPlan(result.plan());
        webPlan = result;
        candidates.clear();
        result.candidatesByItem().forEach((name, listings) ->
            candidates.put(name, new ArrayList<>(listings)));
        activeOptimization = "Cheapest";
        System.out.println("Generated " + currentPlan.getShoppingItems().size() + " item(s).");
        if (!result.isComplete()) {
            System.out.println("Partial plan: " + result.unresolvedItems().size() + " unresolved item(s).");
        }
        for (String warning : result.warnings()) System.out.println("Warning: " + warning);
        showPlan();
    }

    private void planMenu() throws Exception {
        while (true) {
            System.out.println("\n=== SHOPPING PLAN ===");
            System.out.println("1. View Plan\n2. Cheapest\n3. Fewest Stores\n4. One Store");
            System.out.println("5. Find Alternative\n6. Remove Product\n7. Refresh Original Plan");
            System.out.println("8. View Source Candidates\n9. Save as Checklist\n0. Back");
            String c = input("Select: ");
            if (c.equals("0")) return;
            if (currentPlan == null) { System.out.println("Generate or load a plan first."); return; }
            switch (c) {
                case "1" -> showPlan();
                case "2" -> optimize("Cheapest");
                case "3" -> optimize("Fewest Stores");
                case "4" -> optimize("One Store");
                case "5" -> replaceProduct();
                case "6" -> removeFromPlan();
                case "7" -> restoreOriginal();
                case "8" -> showCandidates();
                case "9" -> saveChecklist();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void showPlan() {
        if (currentPlan == null) { System.out.println("No current plan."); return; }
        System.out.printf("\n%s | Budget $%.2f | Strategy: %s%n", currentPlan.getName(),
            currentPlan.getEstimatedBudget(), activeOptimization);
        List<ShoppingItem> items = currentPlan.getShoppingItems();
        for (int i = 0; i < items.size(); i++) {
            ShoppingItem item = items.get(i);
            Product p = item.getProduct();
            System.out.printf("%2d. [%s] %-18s x%-3d %-26s %-17s $%.2f%n",
                i + 1, item.isPurchased() ? "x" : " ", item.getName(), item.getQuantity(),
                p == null ? "Unassigned" : p.getName(), p == null ? "-" : p.getStore(), item.getSubtotal());
        }
        boolean complete = items.stream().allMatch(i -> i.getProduct() != null);
        if (complete) {
            double total = pricing.calculatePlanTotal(currentPlan, membership, LocalDate.now());
            System.out.printf("Estimated total: $%.2f | Remaining: $%.2f | %s%n", total,
                currentPlan.getEstimatedBudget() - total,
                total <= currentPlan.getEstimatedBudget() ? "Within budget" : "Over budget");
        } else System.out.println("Total incomplete: some items have no selected product.");
        System.out.println("Web prices are estimates, not guaranteed current prices or availability.");
        if (webPlan != null && !webPlan.isComplete())
            System.out.println("This plan may exclude unresolved requested items.");
    }

    private void optimize(String type) {
        ShoppingPlan source = optimizationBase == null ? currentPlan : optimizationBase;
        Map<String, List<WebProductListing>> normalized = new LinkedHashMap<>();
        candidates.forEach((key, listings) -> normalized.put(key,
            listings.stream().map(p -> new WebProductListing(
                p.name(), p.brand(), p.category(), StoreNameNormalizer.normalize(p.store()),
                p.price(), p.size(), p.unit(), p.sourceUrl())).toList()));
        ProductSearchService service = new ProductSearchService(
            List.of(new CachedWebStoreAdapter(normalized)));
        OptimizationStrat strategy;
        switch (type) {
            case "Cheapest" -> strategy = new CheapestStrat(service, promotions, membership,
                LocalDate.now(), preferences);
            case "Fewest Stores" -> strategy = new FewestStoresStrat(service, promotions, membership,
                LocalDate.now(), preferences);
            case "One Store" -> {
                List<String> stores = candidates.values().stream().flatMap(List::stream)
                    .map(WebProductListing::store).map(StoreNameNormalizer::normalize)
                    .distinct().sorted().toList();
                if (stores.isEmpty()) { System.out.println("No cached stores available."); return; }
                for (int i = 0; i < stores.size(); i++)
                    System.out.printf("%d. %s%n", i + 1, stores.get(i));
                int index = selection("Choose store: ", stores.size());
                if (index < 0) return;
                strategy = new OneStoreStrat(service, promotions, membership,
                    LocalDate.now(), stores.get(index), preferences);
            }
            default -> { return; }
        }
        try {
            ShoppingPlan optimized = strategy.optimize(source);
            currentPlan = optimized;
            activeOptimization = type;
            showPlan();
        } catch (RuntimeException e) {
            System.out.println("Optimization unavailable: " + e.getMessage());
            System.out.println("Your previous plan remains unchanged.");
        }
    }

    private void replaceProduct() {
        if (currentPlan.getShoppingItems().isEmpty()) return;
        showPlan();
        int index = selection("Item to replace: ", currentPlan.getShoppingItems().size());
        if (index < 0) return;
        ShoppingItem item = currentPlan.getShoppingItems().get(index);
        List<WebProductListing> alternatives = candidatesFor(item.getName());
        if (alternatives.isEmpty()) { System.out.println("No cached alternatives for this item."); return; }
        for (int i = 0; i < alternatives.size(); i++) showListing(i + 1, alternatives.get(i));
        int selected = selection("Choose alternative: ", alternatives.size());
        if (selected < 0) return;
        WebProductListing listing = alternatives.get(selected);
        item.setProduct(product(listing));
        // The selected product becomes the baseline for subsequent optimizations.
        if (optimizationBase != null) {
            for (ShoppingItem base : optimizationBase.getShoppingItems()) {
                if (base.getName().equalsIgnoreCase(item.getName())) {
                    base.setProduct(product(listing)); break;
                }
            }
        }
        System.out.println("Alternative selected.");
        showPlan();
    }

    private void removeFromPlan() {
        if (currentPlan.getShoppingItems().isEmpty()) return;
        showPlan();
        int index = selection("Remove item: ", currentPlan.getShoppingItems().size());
        if (index < 0 || !yes("Confirm removal? (y/n): ")) return;
        ShoppingItem removed = currentPlan.getShoppingItems().get(index);
        new ShoppingController(new ProductSearchService(List.of()))
            .updateChecklist(new RemoveItemCommand(currentPlan, removed));
        if (optimizationBase != null) {
            optimizationBase.getShoppingItems().stream()
                .filter(i -> i.getName().equalsIgnoreCase(removed.getName()))
                .findFirst().ifPresent(optimizationBase::removeItem);
        }
        candidates.remove(removed.getName());
        System.out.println("Removed. Refresh Original Plan restores original items.");
    }

    private void restoreOriginal() {
        if (originalPlan == null) { System.out.println("No original AI plan available."); return; }
        if (!yes("Restore original AI plan and discard changes? (y/n): ")) return;
        currentPlan = copyPlan(originalPlan);
        optimizationBase = copyPlan(originalPlan);
        candidates.clear();
        if (webPlan != null) webPlan.candidatesByItem().forEach((k,v) -> candidates.put(k, new ArrayList<>(v)));
        activeOptimization = "Cheapest";
        System.out.println("Original plan restored without calling Claude.");
        showPlan();
    }

    private void showCandidates() {
        if (candidates.isEmpty()) { System.out.println("No cached candidates."); return; }
        candidates.forEach((name, list) -> {
            System.out.println("\n" + name + ":");
            for (int i = 0; i < list.size(); i++) showListing(i + 1, list.get(i));
        });
        if (webPlan != null) for (RequestedItem item : webPlan.unresolvedItems())
            System.out.println("Unresolved: " + item.name());
    }

    private void productsMenu() throws Exception {
        while (true) {
            System.out.println("\n=== PRODUCTS ===");
            System.out.println("1. Web Product Search\n2. Show All Retrieved Results");
            System.out.println("3. View/Search Recent History\n4. Filter Current Results");
            System.out.println("5. Compare Prices\n6. Add Product to Shopping Plan\n0. Back");
            String choice = input("Select: ");
            if (choice.equals("0")) return;
            switch (choice) {
                case "1" -> webProductSearch();
                case "2" -> printListings(allSearchResults());
                case "3" -> recentSearches();
                case "4" -> filterResults();
                case "5" -> compareCachedPrices();
                case "6" -> addSearchProduct();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void webProductSearch() throws Exception {
        String query = required("Product to search: ");
        String category = input("Category (default Other): ");
        if (category.isBlank()) category = "Other";
        ensureAI();
        System.out.println("Searching the web for " + query + "...");
        WebSearchResult research = webSearch.search(query);
        List<WebProductListing> listings = extractor.extract(research, category);
        searchResults.put(query, new ArrayList<>(listings));
        history.save(query, listings);
        if (listings.isEmpty()) System.out.println("No sourced price-bearing listings found.");
        else printListings(listings);
        System.out.println("Results saved as a snapshot; retailer prices can change.");
    }

    private void recentSearches() throws IOException {
        List<ProductSearchHistoryRepository.SavedSearch> saved = history.load();
        if (saved.isEmpty()) { System.out.println("No saved searches."); return; }
        for (int i = 0; i < saved.size(); i++)
            System.out.printf("%d. %s (%d products, %s)%n", i + 1, saved.get(i).query(),
                saved.get(i).listings().size(), saved.get(i).savedAt());
        int index = selection("Open recent search (0 to back): ", saved.size());
        if (index < 0) return;
        var selected = saved.get(index);
        searchResults.put(selected.query(), new ArrayList<>(selected.listings()));
        printListings(selected.listings());
    }

    private void filterResults() {
        System.out.println("0. All current results");
        List<String> terms = new ArrayList<>(searchResults.keySet());
        for (int i = 0; i < terms.size(); i++) System.out.printf("%d. %s%n", i + 1, terms.get(i));
        int n = nonnegativeInt("Select filter: ");
        if (n == 0) printListings(allSearchResults());
        else if (n <= terms.size()) printListings(searchResults.get(terms.get(n - 1)));
        else System.out.println("Invalid filter.");
    }

    private void compareCachedPrices() {
        List<WebProductListing> listings = allSearchResults();
        if (listings.isEmpty()) { System.out.println("Search for products first."); return; }
        printListings(listings);
        int selected = selection("Reference listing: ", listings.size());
        if (selected < 0) return;
        Product reference = product(listings.get(selected));
        List<Product> comparable = comparisons.comparePrices(reference,
            listings.stream().map(ShoppingCLI::product).toList());
        if (comparable.isEmpty()) { System.out.println("No comparable products."); return; }
        for (Product p : comparable)
            System.out.printf("%s | %s | $%.2f | normalized unit price $%.2f%n",
                p.getName(), p.getStore(), p.getPrice(), comparisons.getNormalizedUnitPrice(p));
    }

    private void addSearchProduct() {
        if (currentPlan == null) { System.out.println("Generate or load a plan first."); return; }
        List<WebProductListing> listings = allSearchResults();
        if (listings.isEmpty()) { System.out.println("Search for products first."); return; }
        printListings(listings);
        int selected = selection("Product to add: ", listings.size());
        if (selected < 0) return;
        WebProductListing listing = listings.get(selected);
        ShoppingItem item = new ShoppingItem(listing.name(), listing.category(),
            positiveInt("Number of packages: "));
        item.setProduct(product(listing));
        new ShoppingController(new ProductSearchService(List.of()))
            .updateChecklist(new AddItemCommand(currentPlan, item));
        if (optimizationBase == null) optimizationBase = copyPlan(currentPlan);
        else optimizationBase.addItem(copyItem(item));
        candidates.computeIfAbsent(item.getName(), k -> new ArrayList<>());
        if (!candidates.get(item.getName()).contains(listing)) candidates.get(item.getName()).add(listing);
        System.out.println("Added to current plan.");
    }

    private void preferencesMenu() {
        while (true) {
            System.out.println("\n=== PREFERENCES (CURRENT CLI SESSION) ===");
            System.out.println("Stores: " + preferredStores + " | Brands: " + preferredBrands);
            System.out.println("Restrictions: " + restrictions + " | Allergies: " + allergies);
            System.out.println("Excluded items: " + exclusions);
            System.out.println("1. Add Preferred Store\n2. Add Preferred Brand\n3. Add Dietary Restriction");
            System.out.println("4. Add Allergy\n5. Exclude Product\n6. Set Membership\n0. Back");
            String c = input("Select: ");
            if (c.equals("0")) return;
            switch (c) {
                case "1" -> { String v = required("Store: "); preferredStores.add(v); preferences.addPreferredStore(v); }
                case "2" -> { String v = required("Brand: "); preferredBrands.add(v); preferences.addPreferredBrand(v); }
                case "3" -> { String v = required("Restriction: "); restrictions.add(v); preferences.addDietaryRestriction(v); }
                case "4" -> { String v = required("Allergy: "); allergies.add(v); preferences.addAllergy(v); }
                case "5" -> { String v = required("Exclude: "); exclusions.add(v); preferences.addExcludedItem(v); }
                case "6" -> membership = yes("Membership active? (y/n): ");
                default -> System.out.println("Invalid choice.");
            }
            System.out.println("Changes apply during this CLI session; saved GUI preferences are not modified.");
        }
    }

    private void checklistsMenu() throws IOException {
        while (true) {
            System.out.println("\n=== CHECKLISTS ===");
            System.out.println("1. View Current Checklist\n2. Add Item\n3. Remove Item");
            System.out.println("4. Change Quantity\n5. Toggle Purchased\n6. Save Checklist");
            System.out.println("7. Load Checklist\n8. Rename Saved Checklist\n9. Delete Saved Checklist\n0. Back");
            String c = input("Select: ");
            if (c.equals("0")) return;
            switch (c) {
                case "1" -> showPlan();
                case "2" -> checklistAdd();
                case "3" -> checklistRemove();
                case "4" -> checklistQuantity();
                case "5" -> checklistToggle();
                case "6" -> saveChecklist();
                case "7" -> loadChecklist();
                case "8" -> renameChecklist();
                case "9" -> deleteChecklist();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void checklistAdd() {
        if (currentPlan == null) { System.out.println("No current checklist. Generate or load a plan."); return; }
        String name = required("Item name: ");
        String category = input("Category (default Other): ");
        if (category.isBlank()) category = "Other";
        int quantity = positiveInt("Quantity: ");
        ShoppingItem item = new ShoppingItem(name, category, quantity);
        List<WebProductListing> options = candidatesFor(name);
        if (!options.isEmpty()) item.setProduct(product(options.get(0)));
        new ShoppingController(new ProductSearchService(List.of()))
            .updateChecklist(new AddItemCommand(currentPlan, item));
        if (optimizationBase != null) optimizationBase.addItem(copyItem(item));
        System.out.println(item.getProduct() == null ? "Added without a price." : "Added with cached product.");
    }

    private void checklistRemove() {
        if (currentPlan == null || currentPlan.getShoppingItems().isEmpty()) return;
        showPlan();
        int index = selection("Item to remove: ", currentPlan.getShoppingItems().size());
        if (index < 0) return;
        ShoppingItem item = currentPlan.getShoppingItems().get(index);
        new ShoppingController(new ProductSearchService(List.of()))
            .updateChecklist(new RemoveItemCommand(currentPlan, item));
        if (optimizationBase != null) optimizationBase.getShoppingItems().stream()
            .filter(i -> i.getName().equalsIgnoreCase(item.getName())).findFirst()
            .ifPresent(optimizationBase::removeItem);
        System.out.println("Item removed.");
    }

    private void checklistQuantity() {
        if (currentPlan == null || currentPlan.getShoppingItems().isEmpty()) return;
        showPlan();
        int index = selection("Item number: ", currentPlan.getShoppingItems().size());
        if (index < 0) return;
        int quantity = positiveInt("New quantity: ");
        ShoppingItem item = currentPlan.getShoppingItems().get(index);
        new ShoppingController(new ProductSearchService(List.of()))
            .updateChecklist(new ChangeQuantityCommand(currentPlan, item, quantity));
        if (optimizationBase != null) optimizationBase.getShoppingItems().stream()
            .filter(i -> i.getName().equalsIgnoreCase(item.getName())).findFirst()
            .ifPresent(i -> i.setQuantity(quantity));
    }

    private void checklistToggle() {
        if (currentPlan == null || currentPlan.getShoppingItems().isEmpty()) return;
        showPlan();
        int index = selection("Item number: ", currentPlan.getShoppingItems().size());
        if (index < 0) return;
        ShoppingItem item = currentPlan.getShoppingItems().get(index);
        item.setPurchased(!item.isPurchased());
        if (optimizationBase != null) optimizationBase.getShoppingItems().stream()
            .filter(i -> i.getName().equalsIgnoreCase(item.getName())).findFirst()
            .ifPresent(i -> i.setPurchased(item.isPurchased()));
        System.out.println(item.isPurchased() ? "Marked purchased." : "Marked not purchased.");
    }

    private void saveChecklist() throws IOException {
        if (currentPlan == null) { System.out.println("No plan to save."); return; }
        String name = required("Checklist name: ");
        if (checklists.exists(name) && !yes("Overwrite existing checklist? (y/n): ")) return;
        checklists.save(name, currentPlan);
        System.out.println("Saved checklist '" + name + "'.");
    }

    private List<String> savedNames() throws IOException {
        List<String> names = checklists.listNames();
        for (int i = 0; i < names.size(); i++) System.out.printf("%d. %s%n", i + 1, names.get(i));
        if (names.isEmpty()) System.out.println("No saved checklists.");
        return names;
    }

    private void loadChecklist() throws IOException {
        List<String> names = savedNames();
        if (names.isEmpty()) return;
        int index = selection("Load checklist: ", names.size());
        if (index < 0) return;
        currentPlan = checklists.load(names.get(index));
        originalPlan = null; // A loaded checklist is not an AI-generation snapshot.
        optimizationBase = copyPlan(currentPlan);
        webPlan = null;
        candidates.clear();
        activeOptimization = "Loaded checklist";
        System.out.println("Checklist loaded.");
        showPlan();
    }

    private void renameChecklist() throws IOException {
        List<String> names = savedNames();
        if (names.isEmpty()) return;
        int index = selection("Checklist to rename: ", names.size());
        if (index < 0) return;
        String name = required("New name: ");
        checklists.rename(names.get(index), name);
        System.out.println("Renamed.");
    }

    private void deleteChecklist() throws IOException {
        List<String> names = savedNames();
        if (names.isEmpty()) return;
        int index = selection("Checklist to delete: ", names.size());
        if (index < 0 || !yes("Permanently delete this saved checklist? (y/n): ")) return;
        checklists.delete(names.get(index));
        System.out.println("Deleted saved checklist.");
    }

    private List<WebProductListing> candidatesFor(String item) {
        for (var e : candidates.entrySet())
            if (e.getKey().equalsIgnoreCase(item)) return e.getValue();
        return List.of();
    }

    private List<WebProductListing> allSearchResults() {
        return searchResults.values().stream().flatMap(List::stream).distinct().toList();
    }

    private static Product product(WebProductListing p) {
        return new Product("WEB-CLI-" + Math.abs(p.sourceUrl().hashCode()), p.name(), p.brand(),
            p.category(), StoreNameNormalizer.normalize(p.store()), p.price(), p.size(), p.unit());
    }

    private static ShoppingItem copyItem(ShoppingItem item) {
        ShoppingItem copy = new ShoppingItem(item.getName(), item.getCategory(), item.getQuantity());
        copy.setProduct(item.getProduct());
        copy.setPurchased(item.isPurchased());
        return copy;
    }

    private static ShoppingPlan copyPlan(ShoppingPlan plan) {
        ShoppingPlan copy = new ShoppingPlan(plan.getName(), plan.getEstimatedBudget());
        for (ShoppingItem item : plan.getShoppingItems()) copy.addItem(copyItem(item));
        return copy;
    }

    private static void showListing(int i, WebProductListing p) {
        System.out.printf("%d. %s | %s | %s | $%.2f | %.2f %s%n   Source: %s%n",
            i, p.name(), p.brand(), p.store(), p.price(), p.size(), p.unit(), p.sourceUrl());
    }

    private static void printListings(List<WebProductListing> listings) {
        if (listings.isEmpty()) { System.out.println("No product listings."); return; }
        for (int i = 0; i < listings.size(); i++) showListing(i + 1, listings.get(i));
    }

    private String input(String message) {
        System.out.print(message);
        if (!scanner.hasNextLine()) throw new IllegalStateException("No more console input.");
        return scanner.nextLine().trim();
    }

    private String required(String message) {
        while (true) {
            String text = input(message);
            if (!text.isBlank()) return text;
            System.out.println("This field cannot be empty.");
        }
    }

    private int nonnegativeInt(String message) {
        while (true) {
            try { int n = Integer.parseInt(input(message)); if (n >= 0) return n; }
            catch (NumberFormatException ignored) { }
            System.out.println("Enter a nonnegative whole number.");
        }
    }

    private int positiveInt(String message) {
        while (true) { int value = nonnegativeInt(message); if (value > 0) return value;
            System.out.println("Quantity must be positive."); }
    }

    private double positiveDouble(String message) {
        while (true) {
            try {
                double value = Double.parseDouble(input(message));
                if (Double.isFinite(value) && value > 0) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a positive finite amount.");
        }
    }

    private boolean yes(String message) {
        while (true) {
            String c = input(message).toLowerCase(Locale.ROOT);
            if (c.equals("y") || c.equals("yes")) return true;
            if (c.equals("n") || c.equals("no")) return false;
            System.out.println("Enter y or n.");
        }
    }

    /** Returns a zero-based index; entering 0 cancels. */
    private int selection(String message, int max) {
        if (max < 1) return -1;
        while (true) {
            int choice = nonnegativeInt(message);
            if (choice == 0) return -1;
            if (choice <= max) return choice - 1;
            System.out.println("Choose a number between 0 and " + max + ".");
        }
    }
}
