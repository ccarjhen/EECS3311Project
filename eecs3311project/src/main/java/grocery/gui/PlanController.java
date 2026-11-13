package grocery.gui;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import grocery.adapter.CachedWebStoreAdapter;
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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public class PlanController {
    @FXML private Label planNameLabel, totalLabel, budgetLabel, percentLabel, remainingLabel;
    @FXML private ProgressBar budgetProgress;
    @FXML private TableView<ShoppingItem> itemsTable;
    @FXML private TableColumn<ShoppingItem, String> itemColumn, productColumn, storeColumn,
        saleColumn, priceColumn;
    @FXML private TableColumn<ShoppingItem, Integer> quantityColumn;
    @FXML private TableColumn<ShoppingItem, ShoppingItem> sourceColumn;
    @FXML private Button cheapestButton, fewestStoresButton, oneStoreButton,
        alternativeButton, removeProductButton, refreshPlanButton;
    @FXML private Label optimizationStatusLabel;

    private ShoppingPlan currentPlan;
    private MainController mainController;
    private Timeline budgetAnimation;
    private Map<String, List<WebProductListing>> webCandidates = Map.of();
    private final PromotionService promotionService = new PromotionService();
    // Controller's service is not used for web optimization: strategy gets its own cached web service.
    private final ShoppingController shoppingController =
        new ShoppingController(new ProductSearchService(List.of()));

    public void setMainController(MainController controller) { mainController = controller; }
    public void setWebCandidates(Map<String, List<WebProductListing>> candidates) {
        webCandidates = candidates == null ? Map.of() : Map.copyOf(candidates);
        if (itemsTable != null) itemsTable.refresh();
    }

    @FXML
    public void initialize() {
        itemColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        quantityColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getQuantity()));
        productColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            d.getValue().getProduct() == null ? "Unassigned" : d.getValue().getProduct().getName()));
        storeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            d.getValue().getProduct() == null ? "—" : d.getValue().getProduct().getStore()));
        saleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            d.getValue().getProduct() != null && d.getValue().getProduct().hasPromotion() ? "✓" : "—"));
        priceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            String.format("$%.2f", d.getValue().getSubtotal())));

        productColumn.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name);
                if (empty || name == null) setTooltip(null);
                else { tip.setText(name); setTooltip(tip); }
            }
        });

        sourceColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        sourceColumn.setCellFactory(c -> new TableCell<>() {
            private final Hyperlink hyperlink = new Hyperlink("View Source");
            { hyperlink.setOnAction(e -> {
                String url = sourceFor(getItem());
                if (url != null) openSource(url);
            }); }
            @Override protected void updateItem(ShoppingItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || sourceFor(item) == null ? null : hyperlink);
            }
        });

        itemsTable.setPlaceholder(new Label("No web products in this plan."));
        itemsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (TableColumn<ShoppingItem, ?> column : itemsTable.getColumns()) {
            column.setResizable(true);
            column.setSortable(false);
            column.setReorderable(false);
        }
        itemColumn.setPrefWidth(155);
        quantityColumn.setPrefWidth(65);
        productColumn.setPrefWidth(340);
        storeColumn.setPrefWidth(145);
        saleColumn.setPrefWidth(80);
        priceColumn.setPrefWidth(110);
        sourceColumn.setPrefWidth(110);

        alternativeButton.setDisable(true);
        removeProductButton.setDisable(true);
        itemsTable.getSelectionModel().selectedItemProperty().addListener((obs, before, selected) -> {
            alternativeButton.setDisable(selected == null);
            removeProductButton.setDisable(selected == null);
        });
    }

    public void setShoppingPlan(ShoppingPlan plan) {
        currentPlan = plan;

        if (mainController != null) {
            String selected = mainController.getActiveOptimization();
            switch (selected) {
                case "Cheapest" ->
                    highlightOptimization(cheapestButton);
                case "Fewest Stores" ->
                    highlightOptimization(fewestStoresButton);
                case "One Store" ->
                    highlightOptimization(oneStoreButton);
                default ->
                    highlightOptimization(null);
            }
        }
        boolean hasItems = plan != null && !plan.getShoppingItems().isEmpty();
        cheapestButton.setDisable(!hasItems);
        fewestStoresButton.setDisable(!hasItems);
        oneStoreButton.setDisable(!hasItems);
        refreshPlanButton.setDisable(mainController == null || !mainController.hasOriginalGeneratedPlan());
        if (plan == null) {
            planNameLabel.setText("Shopping Plan");
            itemsTable.getItems().clear();
            totalLabel.setText("$0.00");
            budgetLabel.setText("No shopping plan generated.");
            percentLabel.setText("0% of budget");
            remainingLabel.setText("");
            budgetProgress.setProgress(0);
            return;
        }
        planNameLabel.setText(plan.getName());
        itemsTable.setItems(FXCollections.observableArrayList(plan.getShoppingItems()));
        refreshBudget();
    }

    private void refreshBudget() {
        if (currentPlan == null) return;
        double total = currentPlan.calculateTotal();
        double budget = currentPlan.getEstimatedBudget();
        double fraction = budget > 0 ? total / budget : 0;
        totalLabel.setText(String.format("$%.2f", total));
        budgetLabel.setText(String.format("Estimated total against a $%.2f budget", budget));
        percentLabel.setText(String.format("%.1f%% of budget", fraction * 100));
        double remaining = budget - total;
        remainingLabel.setText(remaining >= 0
            ? String.format("$%.2f remaining", remaining)
            : String.format("$%.2f over budget", -remaining));
        if (budgetAnimation != null) budgetAnimation.stop();
        double start = Math.max(0, budgetProgress.getProgress());
        budgetAnimation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(budgetProgress.progressProperty(), start)),
            new KeyFrame(Duration.millis(450),
                new KeyValue(budgetProgress.progressProperty(), Math.max(0, Math.min(1, fraction)))));
        budgetAnimation.play();
    }

    private ProductSearchService webSearchService() {
        if (mainController == null || mainController.getCurrentWebResult() == null)
            throw new IllegalStateException("Generate a web shopping plan before optimizing.");
        if (webCandidates.isEmpty())
            throw new IllegalStateException("No cached web products available for optimization.");
        return new ProductSearchService(List.of(new CachedWebStoreAdapter(webCandidates)));
    }

    private UserPreferences preferences() {
        try { return new PreferencesService().load(); }
        catch (Exception ex) { throw new IllegalStateException("Cannot load preferences: " + ex.getMessage(), ex); }
    }

    @FXML private void optimizeCheapest() {
        try { applyOptimization("Cheapest", new CheapestStrat(
            webSearchService(), promotionService, false, LocalDate.now(), preferences())); }
        catch (RuntimeException ex) { showError(ex.getMessage()); }
    }
    @FXML private void optimizeFewestStores() {
        try { applyOptimization("Fewest Stores", new FewestStoresStrat(
            webSearchService(), promotionService, false, LocalDate.now(), preferences())); }
        catch (RuntimeException ex) { showError(ex.getMessage()); }
    }
    @FXML private void optimizeOneStore() {
        if (currentPlan == null || currentPlan.getShoppingItems().isEmpty()) {
            showError("Generate a plan first."); return;
        }
        try {
            ProductSearchService search = webSearchService();
            UserPreferences prefs = preferences();
            Set<String> commonStores = new TreeSet<>();
            boolean first = true;
            for (ShoppingItem item : currentPlan.getShoppingItems()) {
                Set<String> stores = new TreeSet<>();
                for (Product product : search.search(item.getName())) {
                    if (!product.getCategory().equalsIgnoreCase(item.getCategory())) continue;
                    boolean excluded = prefs.getExcludedItems().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .anyMatch(s -> product.getName().toLowerCase(Locale.ROOT)
                            .contains(s.trim().toLowerCase(Locale.ROOT)));
                    if (!excluded) stores.add(product.getStore());
                }
                if (first) { commonStores.addAll(stores); first = false; }
                else commonStores.retainAll(stores);
            }
            if (commonStores.isEmpty()) {
                showError("No single retailer has cached candidates for every item."); return;
            }
            List<String> stores = new ArrayList<>(commonStores);
            ChoiceDialog<String> dialog = new ChoiceDialog<>(stores.get(0), stores);
            dialog.setTitle("One Store Optimization");
            dialog.setHeaderText("Choose a retailer with products for all items");
            dialog.setContentText("Store:");
            dialog.showAndWait().ifPresent(store -> applyOptimization("One Store: " + store,
                new OneStoreStrat(search, promotionService, false, LocalDate.now(), store, prefs)));
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void applyOptimization(String title, OptimizationStrat strategy) {
        if (currentPlan == null) { showError("Generate a plan first."); return; }
        try {
            System.out.println("[PLAN] Applying " + title + " optimization...");
            // Always work from stable requirements, not the previous strategy's selected products.
            ShoppingPlan baseline = mainController == null
                ? currentPlan : mainController.getOptimizationBasePlan();
            if (baseline == null) baseline = currentPlan;
            ShoppingPlan optimized = strategy.optimize(baseline);
            currentPlan = optimized;
            if (mainController != null) mainController.setCurrentPlan(optimized);
            setShoppingPlan(optimized);
            optimizationStatusLabel.setText(title + " optimization applied.");
            System.out.printf("[PLAN] %s optimization completed. Estimated total: $%.2f CAD%n",
                title, optimized.calculateTotal());

            String mode;

            if (title.startsWith("One Store")) {
                mode = "One Store";
                highlightOptimization(oneStoreButton);

            } else if (title.equals("Fewest Stores")) {
                mode = "Fewest Stores";
                highlightOptimization(fewestStoresButton);

            } else {
                mode = "Cheapest";
                highlightOptimization(cheapestButton);
            }

            if (mainController != null) {
                mainController.setActiveOptimization(mode);
            }

        } catch (RuntimeException ex) {
            System.err.println("[PLAN] " + title + " optimization failed: " + ex.getMessage());
            optimizationStatusLabel.setText(title + " optimization was not possible.");
            showError(ex.getMessage());
        }
    }

    @FXML private void findAlternative() {
        ShoppingItem item = itemsTable.getSelectionModel().getSelectedItem();
        if (item == null) { showError("Select a shopping item first."); return; }
        List<WebProductListing> candidates = webCandidates.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(item.getName()))
            .map(Map.Entry::getValue).findFirst().orElse(List.of());
        Product current = item.getProduct();
        List<WebProductListing> alternatives = candidates.stream()
            .filter(p -> !sameProduct(p, current)).toList();
        if (alternatives.isEmpty()) { showError("No remaining saved web alternatives."); return; }
        List<String> labels = new ArrayList<>();
        for (WebProductListing p : alternatives) labels.add(String.format(
            "%s | %s | $%.2f | %.2f %s", p.name(), p.store(), p.price(), p.size(), p.unit()));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.setTitle("Find Alternative");
        dialog.setHeaderText("Replace " + item.getName());
        dialog.setContentText("Retrieved products:");
        var answer = dialog.showAndWait();
        if (answer.isEmpty()) return;
        int index = labels.indexOf(answer.get());
        if (index < 0) return;
        WebProductListing selected = alternatives.get(index);
        try {
            boolean excluded = preferences().getExcludedItems().stream()
                .filter(s -> s != null && !s.isBlank())
                .anyMatch(s -> selected.name().toLowerCase(Locale.ROOT)
                    .contains(s.trim().toLowerCase(Locale.ROOT)));
            if (excluded) { showError("This product matches an excluded-item preference."); return; }
        } catch (RuntimeException ex) { showError(ex.getMessage()); return; }
        item.setProduct(new Product("WEB-ALT-" + java.util.UUID.randomUUID(), selected.name(),
            selected.brand(), selected.category(), selected.store(),
            selected.price(), selected.size(), selected.unit()));
        itemsTable.refresh();
        refreshBudget();
        optimizationStatusLabel.setText("Selected " + selected.name() + " (cached web result).");
        System.out.println("[PLAN] Replaced product with " + selected.name()
            + " from " + selected.store() + ".");
    }

    private static boolean sameProduct(WebProductListing listing, Product product) {
        return product != null && listing.name().equalsIgnoreCase(product.getName())
            && listing.store().equalsIgnoreCase(product.getStore())
            && Double.compare(listing.price(), product.getPrice()) == 0
            && Double.compare(listing.size(), product.getSize()) == 0
            && listing.unit().equalsIgnoreCase(product.getUnit());
    }

    @FXML private void removeSelectedProduct() {
        ShoppingItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentPlan == null) { showError("Select an item to remove."); return; }
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Remove Product");
        dialog.setHeaderText("Remove " + selected.getName() + "?");
        dialog.setContentText("This item will no longer be included when optimizing.");
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        currentPlan.removeItem(selected);
        if (mainController != null) {
            mainController.removeItemFromBaseline(selected);
            mainController.setCurrentPlan(currentPlan);
        }
        setShoppingPlan(currentPlan);
        optimizationStatusLabel.setText(selected.getName() + " removed.");
        System.out.println("[PLAN] Removed item: " + selected.getName());
    }

    @FXML private void refreshPlan() {
        if (mainController == null || !mainController.hasOriginalGeneratedPlan()) {
            showError("No original AI-generated plan is available to restore.");
            return;
        }

        try {
            System.out.println("[PLAN] Restoring original AI-generated plan...");
            ShoppingPlan restored = mainController.restoreOriginalPlan();

            setWebCandidates(mainController.getWebCandidates());
            setShoppingPlan(restored);
            optimizationStatusLabel.setText(
                "Original AI-generated plan restored without a new web search.");
            System.out.printf(
                "[PLAN] Original plan restored. Estimated total: $%.2f CAD%n",
                restored.calculateTotal());
        } catch (RuntimeException ex) {
            System.err.println("[PLAN] Restore failed: " + ex.getMessage());
            showError(ex.getMessage());
        }
    }

    private String sourceFor(ShoppingItem item) {
        if (item == null || item.getProduct() == null) return null;
        return webCandidates.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(item.getName()))
            .flatMap(e -> e.getValue().stream())
            .filter(p -> sameProduct(p, item.getProduct()))
            .map(WebProductListing::sourceUrl).findFirst().orElse(null);
    }

    private void openSource(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid HTTPS product source.");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Desktop browser is unavailable.");
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) { showError("Could not open source: " + ex.getMessage()); }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Shopping Plan");
        alert.setHeaderText("Shopping Plan Notice");
        alert.setContentText(message == null ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }


    private void highlightOptimization(Button active) {

        Button[] buttons = {
            cheapestButton,
            fewestStoresButton,
            oneStoreButton
        };

        for (Button button : buttons) {
            button.getStyleClass().remove("active-optimization");
        }

        if (active != null) {
            active.getStyleClass().add("active-optimization");
        }
    }

}
