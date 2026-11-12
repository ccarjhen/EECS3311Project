package grocery.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import grocery.agent.ClaudeClient;
import grocery.model.Product;
import grocery.model.WebProductListing;
import grocery.service.ProductSearchHistoryRepository;
import grocery.service.ProductSearchHistoryRepository.SavedSearch;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import grocery.service.WebSearchResult;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class ProductsController {
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortComboBox, savedSearchCombo;
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> nameColumn, brandColumn, storeColumn,
        sizeColumn, priceColumn, unitPriceColumn;
    @FXML private TableColumn<Product, Product> sourceColumn;
    @FXML private Label resultCountLabel, statusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button searchButton, refreshSearchButton, addToPlanButton;

    private final ProductSearchHistoryRepository historyRepository =
        new ProductSearchHistoryRepository();
    private final List<Product> currentResults = new ArrayList<>();
    private final Map<Product, WebProductListing> sourceByProduct = new IdentityHashMap<>();
    private List<WebProductListing> currentWebListings = List.of();
    private String currentQuery = "";
    private MainController mainController;
    private boolean searching;

    public void setMainController(MainController controller) { mainController = controller; }

    @FXML public void initialize() {
        nameColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        brandColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getBrand()));
        storeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getStore()));
        sizeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.format("%.2f %s",
            d.getValue().getSize(), d.getValue().getUnit())));
        priceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            String.format("$%.2f", d.getValue().getPrice())));
        unitPriceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
            d.getValue().getSize() > 0
                ? String.format("$%.2f/%s", d.getValue().getUnitPrice(), d.getValue().getUnit())
                : "N/A"));
        sourceColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        sourceColumn.setCellFactory(c -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink("View Source");
            { link.setOnAction(e -> {
                WebProductListing listing = sourceByProduct.get(getItem());
                if (listing != null) openSource(listing.sourceUrl());
            }); }
            @Override protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setGraphic(empty || !sourceByProduct.containsKey(product) ? null : link);
            }
        });
        for (TableColumn<Product, ?> column : productsTable.getColumns()) {
            column.setResizable(true);
            column.setSortable(false);
            column.setReorderable(false);
        }
        productsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        nameColumn.setPrefWidth(310);
        brandColumn.setPrefWidth(130);
        storeColumn.setPrefWidth(140);
        sizeColumn.setPrefWidth(120);
        priceColumn.setPrefWidth(95);
        unitPriceColumn.setPrefWidth(125);
        sourceColumn.setPrefWidth(120);
        productsTable.setPlaceholder(new Label("Enter a product name to search retailer websites."));
        sortComboBox.getItems().setAll("Price: Low to High", "Price: High to Low", "Product Name", "Store");
        sortComboBox.setValue("Price: Low to High");
        sortComboBox.setOnAction(e -> displayResults());
        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
            addToPlanButton.setDisable(selected == null));
        addToPlanButton.setDisable(true);
        resultCountLabel.setText("0 products found");
        setSearching(false);
        reloadSavedSearches();
    }

    private void setSearching(boolean value) {
        searching = value;
        searchField.setDisable(value);
        searchButton.setDisable(value);
        refreshSearchButton.setDisable(value);
        savedSearchCombo.setDisable(value);
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
    }

    @FXML private void searchProducts() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { statusLabel.setText("Enter a product name first."); return; }
        if (searching) return;
        setSearching(true);
        statusLabel.setText("Searching retailer websites for " + query + "...");
        Task<List<WebProductListing>> task = new Task<>() {
            @Override protected List<WebProductListing> call() throws Exception {
                ClaudeClient claude = new ClaudeClient();
                WebProductSearchService webSearch = new WebProductSearchService(claude);
                WebListingExtractor extractor = new WebListingExtractor(claude);
                WebSearchResult research = webSearch.search(query);
                return extractor.extract(research, "Other").stream()
                    .filter(p -> Double.isFinite(p.price()) && p.price() > 0
                        && Double.isFinite(p.size()) && p.size() > 0)
                    .distinct().limit(3).toList();
            }
        };
        task.setOnSucceeded(e -> {
            setSearching(false);
            currentQuery = query;
            showListings(task.getValue());
            statusLabel.setText(currentResults.isEmpty()
                ? "No source-backed priced listings were found for " + query + "."
                : "Found " + currentResults.size() + " web listing(s) for " + query
                    + ". Listed prices are estimates.");
        });
        task.setOnFailed(e -> {
            setSearching(false);
            clearListings();
            statusLabel.setText("Web search failed. You can retry or load a saved search.");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread worker = new Thread(task, "grocery-product-web-search");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML private void refreshSearch() {
        if (searching) return;
        if (searchField.getText().isBlank()) { statusLabel.setText("Enter a query to refresh."); return; }
        searchProducts(); // A fresh network request; may incur API usage/cost.
    }

    private void clearListings() {
        currentWebListings = List.of();
        currentResults.clear();
        sourceByProduct.clear();
        displayResults();
    }

    private void showListings(List<WebProductListing> listings) {
        clearListings();
        currentWebListings = List.copyOf(listings);
        int index = 1;
        for (WebProductListing listing : currentWebListings) {
            Product product = new Product("WEB-SEARCH-" + index++, listing.name(), listing.brand(),
                listing.category(), listing.store(), listing.price(), listing.size(), listing.unit());
            currentResults.add(product);
            sourceByProduct.put(product, listing);
        }
        displayResults();
    }

    private void displayResults() {
        List<Product> sorted = new ArrayList<>(currentResults);
        String selected = sortComboBox.getValue();
        if ("Price: Low to High".equals(selected)) sorted.sort(Comparator.comparingDouble(Product::getPrice));
        else if ("Price: High to Low".equals(selected)) sorted.sort(Comparator.comparingDouble(Product::getPrice).reversed());
        else if ("Product Name".equals(selected)) sorted.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
        else if ("Store".equals(selected)) sorted.sort(Comparator.comparing(Product::getStore, String.CASE_INSENSITIVE_ORDER));
        productsTable.setItems(FXCollections.observableArrayList(sorted));
        resultCountLabel.setText(sorted.size() + " products found");
    }

    @FXML private void addSelectedProductToPlan() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { notice("Select a web product first."); return; }
        if (mainController == null || mainController.getCurrentPlan() == null) {
            notice("Generate a shopping plan before adding products."); return;
        }
        WebProductListing listing = sourceByProduct.get(selected);
        if (listing == null) { notice("Missing source information for this product."); return; }
        try {
            mainController.addSearchedProduct(listing);
            mainController.showPlan();
        } catch (RuntimeException ex) { notice(ex.getMessage()); }
    }

    private void reloadSavedSearches() {
        try {
            savedSearchCombo.getItems().setAll(
                historyRepository.load().stream().map(SavedSearch::query).toList());
        } catch (Exception ex) {
            statusLabel.setText("Cannot read saved search history.");
            ex.printStackTrace();
        }
    }

    @FXML private void saveCurrentSearch() {
        if (currentQuery.isBlank() || currentWebListings.isEmpty()) {
            statusLabel.setText("Search for products before saving."); return;
        }
        try {
            historyRepository.save(currentQuery, currentWebListings);
            reloadSavedSearches();
            savedSearchCombo.setValue(currentQuery);
            statusLabel.setText("Saved search for " + currentQuery + ".");
        } catch (Exception ex) {
            statusLabel.setText("Unable to save this search.");
            ex.printStackTrace();
        }
    }

    @FXML private void loadSavedSearch() {
        String selected = savedSearchCombo.getValue();
        if (selected == null || searching) { statusLabel.setText("Select a saved search first."); return; }
        try {
            SavedSearch saved = historyRepository.load().stream()
                .filter(item -> item.query().equalsIgnoreCase(selected))
                .findFirst().orElseThrow();
            currentQuery = saved.query();
            searchField.setText(currentQuery);
            showListings(saved.listings());
            statusLabel.setText("Loaded saved search (historical prices, not refreshed).");
        } catch (Exception ex) {
            statusLabel.setText("Unable to load the saved search.");
            ex.printStackTrace();
        }
    }

    @FXML private void deleteSavedSearch() {
        String selected = savedSearchCombo.getValue();
        if (selected == null || searching) { statusLabel.setText("Select a saved search first."); return; }
        try {
            historyRepository.delete(selected);
            reloadSavedSearches();
            savedSearchCombo.setValue(null);
            statusLabel.setText("Deleted saved search: " + selected);
        } catch (Exception ex) {
            statusLabel.setText("Unable to delete saved search.");
            ex.printStackTrace();
        }
    }

    private void openSource(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid product source URL.");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Browser unavailable.");
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) { notice("Unable to open source: " + ex.getMessage()); }
    }

    private void notice(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Product Search");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
