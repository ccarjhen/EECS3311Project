package grocery.gui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.controller.ShoppingController;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
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

    // <CodeBlock language="java" editable>
    @FXML private Button cheapestButton;
    @FXML private Button fewestStoresButton;
    @FXML private Button oneStoreButton;
    @FXML private Label optimizationStatusLabel;

    @FXML private Button alternativeButton;

    // Used to animate the progress bar.
    private Timeline budgetAnimation;

    private ShoppingPlan currentPlan;

    private final ProductSearchService searchService =
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
        new ShoppingController(searchService);

    private MainController mainController;

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

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
            Product product = data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product == null ? "Unassigned" : product.getName()
            );
        });

        storeColumn.setCellValueFactory(data -> {
            Product product = data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product == null ? "-" : product.getStore()
            );
        });

        saleColumn.setCellValueFactory(data -> {
            Product product = data.getValue().getProduct();

            return new ReadOnlyStringWrapper(
                product != null && product.hasPromotion()
                    ? "✓"
                    : "—"
            );
        });

        priceColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                String.format("$%.2f", data.getValue().getSubtotal())
            )
        );


        // Display helpful text when the table is empty.
        itemsTable.setPlaceholder(
            new Label("No products yet. Generate a shopping plan first.")
        );

        // Enable Find Alternative only when an item is selected.
        alternativeButton.setDisable(true);

        itemsTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldItem, selectedItem) -> {
                alternativeButton.setDisable(selectedItem == null);
            });

        // Prevent resizing, sorting and reordering columns.
        for (TableColumn<ShoppingItem, ?> column
                : itemsTable.getColumns()) {

            column.setResizable(false);
            column.setSortable(false);
            column.setReorderable(false);
        }

        // Automatically distribute columns across the table width.
        itemsTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
    }

    public void setShoppingPlan(ShoppingPlan plan) {
        currentPlan = plan;

        // Enable optimization only when a plan exists.
        boolean hasPlan = plan != null;

        cheapestButton.setDisable(!hasPlan);
        fewestStoresButton.setDisable(!hasPlan);
        oneStoreButton.setDisable(!hasPlan);

        if (plan == null) {
            planNameLabel.setText("Shopping Plan");
            totalLabel.setText("$0.00");
            budgetLabel.setText("No shopping plan generated.");
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
            String.format("%.1f%% of budget", fraction * 100)
        );

        double remaining = budget - total;

        remainingLabel.setText(
            remaining >= 0
                ? String.format("$%.2f remaining", remaining)
                : String.format("$%.2f over budget", -remaining)
        );

        animateBudgetProgress(fraction);
    }

    @FXML
    private void optimizeCheapest() {

        OptimizationStrat strategy = new CheapestStrat(
            searchService,
            promotionService,
            false,
            LocalDate.now(),
            null
        );

        applyOptimization("Cheapest", strategy);
    }

    @FXML
    private void optimizeFewestStores() {

        OptimizationStrat strategy = new FewestStoresStrat(
            searchService,
            promotionService,
            false,
            LocalDate.now(),
            null
        );

        applyOptimization("Fewest Stores", strategy);
    }

    @FXML
    private void optimizeOneStore() {

        if (currentPlan == null) {
            showError("Generate a shopping plan first.");
            return;
        }

        // Collect stores with products matching plan items.
        // This does not guarantee each store has every item.
        Set<String> storeNames = new TreeSet<>();

        for (ShoppingItem item : currentPlan.getShoppingItems()) {

            for (Product product : searchService.search(item.getName())) {

                if (product.getCategory().equalsIgnoreCase(
                        item.getCategory())) {

                    storeNames.add(product.getStore());
                }
            }
        }

        if (storeNames.isEmpty()) {
            showError("No stores found for your shopping plan.");
            return;
        }

        List<String> stores = new ArrayList<>(storeNames);

        ChoiceDialog<String> dialog = new ChoiceDialog<>(
            stores.get(0),
            stores
        );

        dialog.setTitle("One Store Optimization");
        dialog.setHeaderText("Choose a grocery store");
        dialog.setContentText("Store:");

        dialog.showAndWait().ifPresent(selectedStore -> {

            OptimizationStrat strategy = new OneStoreStrat(
                searchService,
                promotionService,
                false,
                LocalDate.now(),
                selectedStore,
                null
            );

            applyOptimization("One Store: " + selectedStore, strategy);
        });
    }

    private void applyOptimization(
            String name,
            OptimizationStrat strategy) {

        if (currentPlan == null) {
            showError("Generate a shopping plan first.");
            return;
        }

        try {
            shoppingController.setOptimizationStrategy(strategy);

            ShoppingPlan optimized =
                shoppingController.optimize(currentPlan);

            // Update the shared plan so it remains selected
            // when navigating between different pages.
            currentPlan = optimized;

            if (mainController != null) {
                mainController.setCurrentPlan(optimized);
            }

            // Refresh the table, total and budget indicators.
            setShoppingPlan(optimized);

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

    @FXML
    private void findAlternative() {

        // Get the selected item from the table.
        ShoppingItem selectedItem =
            itemsTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showError("Please select an item from the table first.");
            return;
        }

        Product currentProduct = selectedItem.getProduct();

        // Search the existing store catalogues.
        List<Product> alternatives = searchService.search(
            selectedItem.getName()
        ).stream()

            // Keep only products in the same category.
            .filter(product ->
                product.getCategory() != null
                && product.getCategory().equalsIgnoreCase(
                    selectedItem.getCategory()
                )
            )

            // Exclude the currently selected product.
            .filter(product ->
                currentProduct == null
                || !Objects.equals(
                    product.getProdId(),
                    currentProduct.getProdId()
                )
                || !Objects.equals(
                    product.getStore(),
                    currentProduct.getStore()
                )
            )

            // Sort alternatives from lowest to highest price.
            .sorted(Comparator.comparingDouble(Product::getPrice))

            .collect(Collectors.toList());

        if (alternatives.isEmpty()) {
            showError(
                "No alternative products were found for "
                + selectedItem.getName()
            );
            return;
        }

        // Let the shopper select an alternative.
        ChoiceDialog<Product> dialog = new ChoiceDialog<>(
            alternatives.get(0),
            alternatives
        );

        dialog.setTitle("Find Alternative");

        dialog.setHeaderText(
            "Alternatives for " + selectedItem.getName()
        );

        dialog.setContentText("Select a product:");

        dialog.showAndWait().ifPresent(chosenProduct -> {

            // Change only the selected product.
            selectedItem.setProduct(chosenProduct);

            // Refresh the whole plan.
            setShoppingPlan(currentPlan);

            // Keep the selected shopping item highlighted.
            itemsTable.getSelectionModel().select(selectedItem);

            optimizationStatusLabel.setText(
                "Replaced " + selectedItem.getName()
                + " with " + chosenProduct.getName()
                + " from " + chosenProduct.getStore()
            );
        });
    }


    private void animateBudgetProgress(double targetProgress) {

        // Stop an existing animation before starting another.
        if (budgetAnimation != null) {
            budgetAnimation.stop();
        }

        double startProgress = budgetProgress.getProgress();

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



    private void showError(String message) {

        Alert alert = new Alert(Alert.AlertType.WARNING);

        alert.setTitle("Shopping Plan");
        alert.setHeaderText("Optimization");
        alert.setContentText(message);

        alert.showAndWait();
    }
}
