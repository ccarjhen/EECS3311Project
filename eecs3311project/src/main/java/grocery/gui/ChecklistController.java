package grocery.gui;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.ChecklistCommand;
import grocery.command.RemoveItemCommand;
import grocery.command.SetPurchasedCommand;
import grocery.controller.ShoppingController;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.observer.PlanObserver;
import grocery.service.ChecklistRepository;
import grocery.service.ProductSearchService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ChecklistController implements PlanObserver {

    @FXML private FlowPane savedTabs;
    @FXML private ListView<ShoppingItem> checklistView;

    @FXML private Label planNameLabel;
    @FXML private Label totalLabel;
    @FXML private Label progressLabel;
    @FXML private Label percentageLabel;
    @FXML private Label statusLabel;

    @FXML private ProgressBar checklistProgress;

    @FXML private TextField itemNameField;
    @FXML private TextField categoryField;
    @FXML private TextField quantityField;
    @FXML private TextField newQuantityField;

    private MainController mainController;
    private ShoppingPlan currentPlan;
    private String selectedSavedName;

    private final ChecklistRepository repository =
        new ChecklistRepository();

    private final ProductSearchService searchService =
        new ProductSearchService(List.of(
            new LongosAdapter(),
            new WalmartAdapter(),
            new NoFrillsAdapter()
        ));

    private final ShoppingController shoppingController =
        new ShoppingController(searchService);

    public void setMainController(MainController controller) {
        mainController = controller;
    }

    @FXML
    public void initialize() {

        checklistView.setPlaceholder(
            new Label("No checklist items yet.")
        );

        checklistView.setCellFactory(view -> new ListCell<>() {

            private final CheckBox box = new CheckBox();
            private final Label name = new Label();
            private final Label details = new Label();

            private final VBox labels =
                new VBox(4, name, details);

            private final HBox row =
                new HBox(16, box, labels);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 4 8;");

                box.setOnAction(event -> {
                    ShoppingItem item = getItem();

                    if (currentPlan != null && item != null) {
                        executeCommand(
                            new SetPurchasedCommand(
                                currentPlan,
                                item,
                                box.isSelected()
                            )
                        );
                    }
                });
            }

            @Override
            protected void updateItem(
                    ShoppingItem item,
                    boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                box.setSelected(item.isPurchased());

                name.setText(
                    item.getName() + " x" + item.getQuantity()
                );

                name.setStyle(
                    item.isPurchased()
                        ? "-fx-strikethrough: true;"
                          + "-fx-text-fill: #888888;"
                        : "-fx-text-fill: #04151F;"
                );

                details.setText(
                    item.getProduct() == null
                        ? "Product not assigned"
                        : item.getProduct().getStore()
                          + " | $"
                          + String.format(
                              "%.2f",
                              item.getSubtotal()
                          )
                );

                details.setStyle(
                    "-fx-text-fill: #777777;"
                    + "-fx-font-size: 12px;"
                );

                setText(null);
                setGraphic(row);
            }
        });

        reloadTabs();
    }

    public void setShoppingPlan(ShoppingPlan plan) {

        detach();

        currentPlan = plan;
        selectedSavedName = null;

        if (currentPlan != null) {
            currentPlan.addObserver(this);
        }

        refreshChecklist();
        reloadTabs();
    }

    public void detach() {
        if (currentPlan != null) {
            currentPlan.removeObserver(this);
        }
    }

    @Override
    public void onPlanChanged(ShoppingPlan plan) {
        Platform.runLater(() -> {
            if (plan == currentPlan) {
                refreshChecklist();
            }
        });
    }

    private void reloadTabs() {

        savedTabs.getChildren().clear();

        try {
            for (String name : repository.listNames()) {

                Button button = new Button(name);
                button.getStyleClass().add("saved-checklist-tab");

                if (name.equals(selectedSavedName)) {
                    button.getStyleClass().add("selected-tab");
                }

                button.setOnAction(event ->
                    openSavedChecklist(name)
                );

                savedTabs.getChildren().add(button);
            }

        } catch (IOException e) {
            statusLabel.setText(
                "Unable to read saved checklists: "
                + e.getMessage()
            );
        }
    }

    private void openSavedChecklist(String name) {

        try {
            ShoppingPlan loaded = repository.load(name);

            detach();

            currentPlan = loaded;
            selectedSavedName = name;
            currentPlan.addObserver(this);

            refreshChecklist();
            reloadTabs();

            statusLabel.setText(
                "Opened checklist: " + name
            );

        } catch (IOException | RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void refreshChecklist() {

        if (currentPlan == null) {
            planNameLabel.setText("No checklist selected");
            totalLabel.setText("$0.00");
            progressLabel.setText("0 of 0 purchased");
            percentageLabel.setText("0%");
            checklistProgress.setProgress(0);
            checklistView.getItems().clear();
            return;
        }

        planNameLabel.setText(
            selectedSavedName != null
                ? selectedSavedName
                : currentPlan.getName()
        );

        totalLabel.setText(
            String.format(
                "$%.2f",
                currentPlan.calculateTotal()
            )
        );

        ShoppingItem selected =
            checklistView.getSelectionModel().getSelectedItem();

        checklistView.setItems(
            FXCollections.observableArrayList(
                currentPlan.getShoppingItems()
            )
        );

        if (selected != null) {
            checklistView.getSelectionModel().select(selected);
        }

        checklistView.refresh();

        int total = currentPlan.getShoppingItems().size();

        long purchased = currentPlan.getShoppingItems()
            .stream()
            .filter(ShoppingItem::isPurchased)
            .count();

        double progress = total == 0
            ? 0
            : (double) purchased / total;

        progressLabel.setText(
            purchased + " of " + total + " purchased"
        );

        percentageLabel.setText(
            String.format("%.0f%%", progress * 100)
        );

        checklistProgress.setProgress(progress);
    }

    private void executeCommand(ChecklistCommand command) {

        try {
            shoppingController.updateChecklist(command);
            statusLabel.setText(
                "Checklist updated. Click Save to keep changes."
            );
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void addItem() {

        if (currentPlan == null) {
            showError("Generate or open a checklist first.");
            return;
        }

        String name = itemNameField.getText().trim();
        String category = categoryField.getText().trim();

        if (name.isEmpty()) {
            showError("Enter an item name.");
            return;
        }

        if (category.isEmpty()) {
            category = "Other";
        }

        try {
            int qty = Integer.parseInt(
                quantityField.getText().trim()
            );

            if (qty <= 0) {
                throw new NumberFormatException();
            }

            executeCommand(new AddItemCommand(
                currentPlan,
                new ShoppingItem(name, category, qty)
            ));

            itemNameField.clear();
            categoryField.clear();
            quantityField.setText("1");

        } catch (NumberFormatException e) {
            showError("Quantity must be a positive integer.");
        }
    }

    @FXML
    private void changeQuantity() {

        ShoppingItem selected =
            checklistView.getSelectionModel().getSelectedItem();

        if (currentPlan == null || selected == null) {
            showError("Select an item first.");
            return;
        }

        try {
            int qty = Integer.parseInt(
                newQuantityField.getText().trim()
            );

            if (qty <= 0) {
                throw new NumberFormatException();
            }

            executeCommand(new ChangeQuantityCommand(
                currentPlan, selected, qty
            ));

            newQuantityField.clear();

        } catch (NumberFormatException e) {
            showError("Enter a positive whole number.");
        }
    }

    @FXML
    private void removeItem() {

        ShoppingItem selected =
            checklistView.getSelectionModel().getSelectedItem();

        if (currentPlan == null || selected == null) {
            showError("Select an item to remove.");
            return;
        }

        executeCommand(
            new RemoveItemCommand(currentPlan, selected)
        );
    }

    @FXML
    private void saveChecklist() {

        if (currentPlan == null) {
            showError("No checklist to save.");
            return;
        }

        String name = selectedSavedName;

        if (name == null) {
            TextInputDialog dialog = new TextInputDialog(
                currentPlan.getName()
            );

            dialog.setTitle("Save Checklist");
            dialog.setHeaderText("Name your checklist");
            dialog.setContentText("Checklist name:");

            Optional<String> result = dialog.showAndWait();

            if (result.isEmpty()) {
                return;
            }

            name = result.get().trim();
        }

        if (name.isBlank()) {
            showError("Checklist name cannot be empty.");
            return;
        }

        try {
            if (selectedSavedName == null
                    && repository.exists(name)) {

                Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION
                );

                confirm.setHeaderText(
                    "Overwrite existing checklist?"
                );
                confirm.setContentText(name);

                if (confirm.showAndWait().orElse(
                        ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }

            repository.save(name, currentPlan);
            selectedSavedName = name;

            reloadTabs();

            statusLabel.setText(
                "Checklist saved: " + name
            );

        } catch (IOException | RuntimeException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void renameChecklist() {

        if (selectedSavedName == null) {
            showError("Select a saved checklist to rename.");
            return;
        }

        TextInputDialog dialog =
            new TextInputDialog(selectedSavedName);

        dialog.setTitle("Rename Checklist");
        dialog.setHeaderText("Rename your checklist");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        String newName = result.get().trim();

        try {
            // Save any unsaved edits before renaming.
            repository.save(selectedSavedName, currentPlan);
            repository.rename(selectedSavedName, newName);
            openSavedChecklist(newName);

            statusLabel.setText(
                "Renamed checklist to: " + newName
            );

        } catch (IOException | RuntimeException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void deleteChecklist() {

        if (selectedSavedName == null) {
            showError("Select a saved checklist to delete.");
            return;
        }

        Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION
        );

        confirm.setTitle("Delete Checklist");
        confirm.setHeaderText(
            "Delete " + selectedSavedName + "?"
        );
        confirm.setContentText(
            "This saved checklist will be deleted."
        );

        if (confirm.showAndWait().orElse(
                ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            repository.delete(selectedSavedName);

            detach();
            currentPlan = null;
            selectedSavedName = null;

            refreshChecklist();
            reloadTabs();

            statusLabel.setText("Checklist deleted.");

        } catch (IOException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void viewPlan() {

        if (currentPlan == null) {
            showError("Select a checklist first.");
            return;
        }

        if (mainController != null) {
            mainController.setCurrentPlan(currentPlan);
            mainController.showPlan();
        }
    }

    private void showError(String message) {
        statusLabel.setText(
            message == null ? "An error occurred." : message
        );
    }
}
