package grocery.gui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import grocery.adapter.CachedWebStoreAdapter;
import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.model.WebProductListing;
import grocery.service.PreferencesService;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.strategy.CheapestStrat;
import grocery.strategy.FewestStoresStrat;
import grocery.strategy.OneStoreStrat;
import grocery.strategy.OptimizationStrat;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;

public class PlanController {

    // ========================================
    // FXML COMPONENTS
    // ========================================

    @FXML private Label planNameLabel;
    @FXML private Label totalLabel;
    @FXML private Label budgetLabel;
    @FXML private Label percentLabel;
    @FXML private Label remainingLabel;

    @FXML private ProgressBar budgetProgress;

    @FXML private TableView<ShoppingItem> itemsTable;

    @FXML private TableColumn<ShoppingItem, String> itemColumn;
    @FXML private TableColumn<ShoppingItem, Integer> quantityColumn;
    @FXML private TableColumn<ShoppingItem, String> productColumn;
    @FXML private TableColumn<ShoppingItem, String> storeColumn;
    @FXML private TableColumn<ShoppingItem, String> saleColumn;
    @FXML private TableColumn<ShoppingItem, String> priceColumn;

    @FXML private Button cheapestButton;
    @FXML private Button fewestStoresButton;
    @FXML private Button oneStoreButton;
    @FXML private Button alternativeButton;

    @FXML private Label optimizationStatusLabel;

    // ========================================
    // CONTROLLER STATE
    // ========================================

    private ShoppingPlan currentPlan;
    private ShoppingPlan optimizationBasePlan;
    private MainController mainController;
    private Timeline budgetAnimation;

    // The previously retrieved web products.
    private Map<String, List<WebProductListing>> webCandidates =
        Map.of();

    // Legacy sample data, only for sample-based plans.
    private final ProductSearchService sampleSearchService =
        new ProductSearchService(
            List.of(
                new LongosAdapter(),
                new WalmartAdapter(),
                new NoFrillsAdapter()
            )
        );

    private final PromotionService promotionService =
        new PromotionService();

    private final ShoppingController shoppingController =
        new ShoppingController(sampleSearchService);

    // ========================================
    // MAIN CONTROLLER CONNECTION
    // ========================================

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    public void setWebCandidates(
            Map<String, List<WebProductListing>> candidates) {

        this.webCandidates = candidates == null
            ? Map.of()
            : Map.copyOf(candidates);
    }

    // ========================================
    // INITIALIZE GUI
    // ========================================

    @FXML
    public void initialize() {

        itemColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                data.getValue().getName()
            )
        );

        quantityColumn.setCellValueFactory(data ->
            new ReadOnlyObjectWrapper<>(
                data.getValue().getQuantity()
            )
        );

        productColumn.setCellValueFactory(data -> {

            Product product =
                data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product == null
                    ? "Unassigned"
                    : product.getName()
            );
        });

        storeColumn.setCellValueFactory(data -> {

            Product product =
                data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product == null
                    ? "-"
                    : product.getStore()
            );
        });

        saleColumn.setCellValueFactory(data -> {

            Product product =
                data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product != null && product.hasPromotion()
                    ? "✓"
                    : "—"
            );
        });

        priceColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                String.format(
                    "$%.2f",
                    data.getValue().getSubtotal()
                )
            )
        );

        itemsTable.setPlaceholder(
            new Label(
                "No products yet. Generate a shopping plan first."
            )
        );

        // Find Alternative requires a selected item.
        alternativeButton.setDisable(true);

        itemsTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, previous, selected) -> {
                alternativeButton.setDisable(
                    selected == null
                );
            });

        for (TableColumn<ShoppingItem, ?> column
                : itemsTable.getColumns()) {

            column.setResizable(false);
            column.setSortable(false);
            column.setReorderable(false);
        }

        itemsTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
    }

    // ========================================
    // DISPLAY SHOPPING PLAN
    // ========================================

    public void setShoppingPlan(ShoppingPlan plan) {

        currentPlan = plan;
        optimizationBasePlan = plan;

        boolean hasPlan = plan != null
            && !plan.getShoppingItems().isEmpty();

        cheapestButton.setDisable(!hasPlan);
        fewestStoresButton.setDisable(!hasPlan);
        oneStoreButton.setDisable(!hasPlan);

        if (plan == null) {

            planNameLabel.setText("Shopping Plan");
            totalLabel.setText("$0.00");
            budgetLabel.setText(
                "No shopping plan generated."
            );
            percentLabel.setText("0% of budget");
            remainingLabel.setText("");

            budgetProgress.setProgress(0);
            itemsTable.getItems().clear();

            return;
        }

        planNameLabel.setText(plan.getName());

        itemsTable.setItems(
            FXCollections.observableArrayList(
                plan.getShoppingItems()
            )
        );

        double total = plan.calculateTotal();
        double budget = plan.getEstimatedBudget();

        double fraction = budget > 0
            ? total / budget
            : 0;

        totalLabel.setText(
            String.format("$%.2f", total)
        );

        budgetLabel.setText(
            String.format(
                "Estimated total against a $%.2f budget",
                budget
            )
        );

        percentLabel.setText(
            String.format(
                "%.1f%% of budget",
                fraction * 100
            )
        );

        double remaining = budget - total;

        remainingLabel.setText(
            remaining >= 0
                ? String.format(
                    "$%.2f remaining",
                    remaining
                )
                : String.format(
                    "$%.2f over budget",
                    -remaining
                )
        );

        animateBudgetProgress(fraction);
    }

    // ========================================
    // ACTIVE PRODUCT DATA SOURCE
    // ========================================

    private ProductSearchService getActiveSearchService() {

        // When the plan came from web search, optimization
        // must use web products rather than sample products.
        boolean isWebPlan = mainController != null
            && mainController.getCurrentWebResult() != null;

        if (isWebPlan) {

            if (webCandidates.isEmpty()) {
                throw new IllegalStateException(
                    "No cached web products are available "
                    + "for optimization."
                );
            }

            return new ProductSearchService(
                List.of(
                    new CachedWebStoreAdapter(
                        webCandidates
                    )
                )
            );
        }

        // Older sample-based plans can still use sample data.
        return sampleSearchService;
    }

    // ========================================
    // USER PREFERENCES
    // ========================================

    private UserPreferences loadPreferences() {

        try {
            return new PreferencesService().load();

        } catch (Exception e) {
            throw new IllegalStateException(
                "Unable to load shopping preferences: "
                + e.getMessage(),
                e
            );
        }
    }

    // ========================================
    // OPTIMIZATION: CHEAPEST
    // ========================================

    @FXML
    private void optimizeCheapest() {

        try {
            ProductSearchService activeSearch =
                getActiveSearchService();

            UserPreferences preferences =
                loadPreferences();

            OptimizationStrat strategy =
                new CheapestStrat(
                    activeSearch,
                    promotionService,
                    false,
                    LocalDate.now(),
                    preferences
                );

            applyOptimization("Cheapest", strategy);

        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    // ========================================
    // OPTIMIZATION: FEWEST STORES
    // ========================================

    @FXML
    private void optimizeFewestStores() {

        try {
            ProductSearchService activeSearch =
                getActiveSearchService();

            UserPreferences preferences =
                loadPreferences();

            OptimizationStrat strategy =
                new FewestStoresStrat(
                    activeSearch,
                    promotionService,
                    false,
                    LocalDate.now(),
                    preferences
                );

            applyOptimization("Fewest Stores", strategy);

        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    // ========================================
    // OPTIMIZATION: ONE STORE
    // ========================================

    @FXML
    private void optimizeOneStore() {

        if (currentPlan == null
                || currentPlan.getShoppingItems().isEmpty()) {

            showError(
                "Generate a shopping plan first."
            );
            return;
        }

        try {
            ProductSearchService activeSearch =
                getActiveSearchService();

            UserPreferences preferences =
                loadPreferences();

            // Only show stores with suitable products
            // for every shopping item in the current plan.
            Set<String> availableStores = new TreeSet<>();

            boolean firstItem = true;

            for (ShoppingItem item :
                    currentPlan.getShoppingItems()) {

                Set<String> itemStores = new TreeSet<>();

                for (Product product :
                        activeSearch.search(item.getName())) {

                    // Use the same basic category and
                    // exclusion checks as optimization.
                    if (!product.getCategory()
                            .equalsIgnoreCase(
                                item.getCategory()
                            )) {
                        continue;
                    }

                    String productName =
                        product.getName().toLowerCase(
                            java.util.Locale.ROOT
                        );

                    boolean excluded = preferences
                        .getExcludedItems()
                        .stream()
                        .filter(s -> s != null && !s.isBlank())
                        .anyMatch(s ->
                            productName.contains(
                                s.trim().toLowerCase(
                                    java.util.Locale.ROOT
                                )
                            )
                        );

                    if (!excluded) {
                        itemStores.add(product.getStore());
                    }
                }

                if (firstItem) {
                    availableStores.addAll(itemStores);
                    firstItem = false;
                } else {
                    availableStores.retainAll(itemStores);
                }
            }

            if (availableStores.isEmpty()) {
                showError(
                    "No single store has suitable cached "
                    + "products for every shopping item."
                );
                return;
            }

            List<String> stores =
                new ArrayList<>(availableStores);

            ChoiceDialog<String> dialog =
                new ChoiceDialog<>(
                    stores.get(0),
                    stores
                );

            dialog.setTitle(
                "One Store Optimization"
            );

            dialog.setHeaderText(
                "Choose a grocery store"
            );

            dialog.setContentText("Store:");

            dialog.showAndWait().ifPresent(
                selectedStore -> {

                    OptimizationStrat strategy =
                        new OneStoreStrat(
                            activeSearch,
                            promotionService,
                            false,
                            LocalDate.now(),
                            selectedStore,
                            preferences
                        );

                    applyOptimization(
                        "One Store: " + selectedStore,
                        strategy
                    );
                }
            );

        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    // ========================================
    // APPLY OPTIMIZATION
    // ========================================
    private void applyOptimization(
            String name,
            OptimizationStrat strategy) {

        if (currentPlan == null) {
            showError("Generate a shopping plan first.");
            return;
        }

        // Always optimize from the original shopping requirements,
        // not the result of a previously selected strategy.
        ShoppingPlan source = optimizationBasePlan != null
            ? optimizationBasePlan
            : currentPlan;

        try {
            shoppingController.setOptimizationStrategy(strategy);

            ShoppingPlan optimized =
                shoppingController.optimize(source);

            if (optimized == null) {
                throw new IllegalStateException(
                    "Optimization did not return a plan."
                );
            }

            currentPlan = optimized;

            if (mainController != null) {
                mainController.setCurrentPlan(optimized);
            }

            // Update the table, prices, budget, and progress.
            setShoppingPlan(optimized);

            // setShoppingPlan() changes this field, so restore
            // the original baseline for the next strategy.
            optimizationBasePlan = source;

            optimizationStatusLabel.setText(
                name + " optimization applied successfully."
            );

        } catch (RuntimeException e) {

            optimizationStatusLabel.setText(
                name + " optimization could not be applied."
            );

            showError(
                e.getMessage() == null
                    ? "Unable to optimize the shopping plan."
                    : e.getMessage()
            );
        }
    }


    // ========================================
    // FIND ALTERNATIVE
    // ========================================

    @FXML
    private void findAlternative() {

        ShoppingItem selected =
            itemsTable.getSelectionModel()
                .getSelectedItem();

        if (selected == null || currentPlan == null) {

            showError(
                "Please select a shopping item first."
            );
            return;
        }

        // Retrieve the previously saved web candidates.
        List<WebProductListing> candidates =
            webCandidates.entrySet()
                .stream()
                .filter(entry ->
                    entry.getKey().equalsIgnoreCase(
                        selected.getName()
                    )
                )
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());

        if (candidates.isEmpty()) {
            showError(
                "No saved web alternatives are available for "
                + selected.getName()
            );
            return;
        }

        Product currentProduct =
            selected.getProduct();

        List<WebProductListing> alternatives =
            new ArrayList<>();

        // Exclude the currently selected product.
        for (WebProductListing candidate : candidates) {

            boolean isCurrent =
                currentProduct != null
                && candidate.name().equalsIgnoreCase(
                    currentProduct.getName()
                )
                && candidate.store().equalsIgnoreCase(
                    currentProduct.getStore()
                )
                && Double.compare(
                    candidate.price(),
                    currentProduct.getPrice()
                ) == 0
                && Double.compare(
                    candidate.size(),
                    currentProduct.getSize()
                ) == 0
                && candidate.unit().equalsIgnoreCase(
                    currentProduct.getUnit()
                );

            if (!isCurrent) {
                alternatives.add(candidate);
            }
        }

        if (alternatives.isEmpty()) {
            showError(
                "No remaining web alternatives are available "
                + "for " + selected.getName()
            );
            return;
        }

        // Display remaining candidates using the
        // existing Find Alternative ChoiceDialog.
        List<String> choices = new ArrayList<>();

        for (WebProductListing product : alternatives) {

            choices.add(
                String.format(
                    "%s | %s | $%.2f | %.2f %s",
                    product.name(),
                    product.store(),
                    product.price(),
                    product.size(),
                    product.unit()
                )
            );
        }

        ChoiceDialog<String> dialog =
            new ChoiceDialog<>(
                choices.get(0),
                choices
            );

        dialog.setTitle("Find Alternative");

        dialog.setHeaderText(
            "Alternative products for "
            + selected.getName()
        );

        dialog.setContentText(
            "Select a product:"
        );

        var result = dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        int index = choices.indexOf(result.get());

        if (index < 0) {
            return;
        }

        WebProductListing chosen =
            alternatives.get(index);

        // Ensure the selected alternative is not excluded
        // by the shopper's saved preferences.
        UserPreferences preferences;

        try {
            preferences = loadPreferences();
        } catch (RuntimeException e) {
            showError(e.getMessage());
            return;
        }

        String chosenName =
            chosen.name().toLowerCase(
                java.util.Locale.ROOT
            );

        boolean excluded = preferences.getExcludedItems()
            .stream()
            .filter(s -> s != null && !s.isBlank())
            .anyMatch(s ->
                chosenName.contains(
                    s.trim().toLowerCase(
                        java.util.Locale.ROOT
                    )
                )
            );

        if (excluded) {
            showError(
                "This product conflicts with your excluded "
                + "items preferences."
            );
            return;
        }

        Product replacement = new Product(
            "WEB-ALT-" + index,
            chosen.name(),
            chosen.brand(),
            chosen.category(),
            chosen.store(),
            chosen.price(),
            chosen.size(),
            chosen.unit()
        );

        // Replace the product without another web search.
        selected.setProduct(replacement);

        // Refresh the current plan, price, and budget.
        setShoppingPlan(currentPlan);

        itemsTable.getSelectionModel()
            .select(selected);

        optimizationStatusLabel.setText(
            "Product replaced with "
            + chosen.name()
            + " from "
            + chosen.store()
        );
    }

    // ========================================
    // BUDGET PROGRESS ANIMATION
    // ========================================

    private void animateBudgetProgress(
            double targetProgress) {

        if (budgetAnimation != null) {
            budgetAnimation.stop();
        }

        double startProgress =
            budgetProgress.getProgress();

        double endProgress = Math.max(
            0,
            Math.min(1, targetProgress)
        );

        budgetAnimation = new Timeline(

            new KeyFrame(
                Duration.ZERO,
                new KeyValue(
                    budgetProgress.progressProperty(),
                    startProgress
                )
            ),

            new KeyFrame(
                Duration.millis(650),
                new KeyValue(
                    budgetProgress.progressProperty(),
                    endProgress
                )
            )
        );

        budgetAnimation.play();
    }

    // ========================================
    // ERROR HANDLING
    // ========================================

    private void showError(String message) {

        Alert alert = new Alert(
            Alert.AlertType.WARNING
        );

        alert.setTitle("Shopping Plan");
        alert.setHeaderText(
            "Shopping Plan Notice"
        );

        alert.setContentText(
            message == null
                ? "An unexpected error occurred."
                : message
        );

        alert.showAndWait();
    }
}
