package grocery.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
import javafx.scene.layout.FlowPane;

/** Accumulates distinct web listings and keeps each row connected to its original source. */
public class ProductsController {
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> nameColumn, brandColumn, storeColumn,
            sizeColumn, priceColumn, unitPriceColumn;
    @FXML private TableColumn<Product, Product> sourceColumn;
    @FXML private Label resultCountLabel, statusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button searchButton, addToPlanButton;
    @FXML private FlowPane recentSearchesBox;

    private final ProductSearchHistoryRepository historyRepository = new ProductSearchHistoryRepository();
    private final Map<String, WebProductListing> displayed = new LinkedHashMap<>();
    private final Map<Product, WebProductListing> sourceByProduct = new IdentityHashMap<>();
    private final List<Product> currentResults = new ArrayList<>();
    private List<WebProductListing> lastSearchListings = List.of();
    private String currentQuery = "";
    private boolean searching;
    // null means All; otherwise show only matching saved-query listings.
    private String activeFilter = null;
    private final Map<String, List<WebProductListing>> savedListingsByQuery = new LinkedHashMap<>();
    private MainController mainController;

    public void setMainController(MainController controller) { mainController = controller; }

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        brandColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getBrand()));
        storeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getStore()));
        sizeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                String.format("%.2f %s", d.getValue().getSize(), d.getValue().getUnit())));
        priceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                String.format("$%.2f", d.getValue().getPrice())));
        unitPriceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().getSize() <= 0 ? "N/A"
                    : String.format("$%.2f/%s", d.getValue().getUnitPrice(), d.getValue().getUnit())));
        sourceColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        sourceColumn.setCellFactory(column -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink("View Source");
            {
                link.setOnAction(e -> {
                    WebProductListing listing = sourceByProduct.get(getItem());
                    if (listing != null) openSource(listing.sourceUrl());
                });
            }
            @Override protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setGraphic(empty || !sourceByProduct.containsKey(product) ? null : link);
            }
        });

        productsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (TableColumn<Product, ?> column : productsTable.getColumns()) {
            column.setResizable(true);
            column.setSortable(false);
            column.setReorderable(false);
        }
        nameColumn.setPrefWidth(285);
        brandColumn.setPrefWidth(130);
        storeColumn.setPrefWidth(130);
        sizeColumn.setPrefWidth(130);
        priceColumn.setPrefWidth(100);
        unitPriceColumn.setPrefWidth(125);
        sourceColumn.setPrefWidth(115);
        productsTable.setPlaceholder(new Label("Search for groceries to compare online listings."));

        sortComboBox.getItems().setAll("Price: Low to High", "Price: High to Low", "Product Name", "Store");
        sortComboBox.setValue("Price: Low to High");
        sortComboBox.setOnAction(e -> displayResults());
        productsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, previous, selected) -> addToPlanButton.setDisable(selected == null));
        addToPlanButton.setDisable(true);
        setSearching(false);
        displayResults();
        loadSavedHistoryIntoAll();
        displayRecentSearchButtons();
    }

    private void setSearching(boolean busy) {
        searching = busy;
        searchField.setDisable(busy);
        searchButton.setDisable(busy);
        loadingIndicator.setVisible(busy);
        loadingIndicator.setManaged(busy);
    }

    @FXML private void searchProducts() { performSearch(); }

    private void performSearch() {
        if (searching) return;
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("Enter a product name first.");
            return;
        }
        setSearching(true);
        statusLabel.setText("Searching retailer websites for " + query + "...");

        Task<List<WebProductListing>> task = new Task<>() {
            @Override protected List<WebProductListing> call() throws Exception {
                ClaudeClient claude = new ClaudeClient();
                WebProductSearchService search = new WebProductSearchService(claude);
                WebListingExtractor extractor = new WebListingExtractor(claude);
                WebSearchResult result = search.search(query);
                return extractor.extract(result, "Other").stream()
                        .filter(p -> Double.isFinite(p.price()) && p.price() > 0
                                && Double.isFinite(p.size()) && p.size() > 0)
                        .distinct().limit(3).toList();
            }
        };
        task.setOnSucceeded(e -> {
            setSearching(false);
            currentQuery = query;
            lastSearchListings = List.copyOf(task.getValue());
            savedListingsByQuery.put(query.toLowerCase(Locale.ROOT), lastSearchListings);
            appendWebListings(lastSearchListings);
            activeFilter = query;
            displayResults();
            if (lastSearchListings.isEmpty()) {
                statusLabel.setText("No priced, source-backed products found for " + query + ". Previous results retained.");
                displayRecentSearchButtons();
                return;
            }
            try {
                // Recent searches are saved automatically, including the associated source URLs.
                historyRepository.save(query, lastSearchListings);
                displayRecentSearchButtons();
                statusLabel.setText("Added/updated " + lastSearchListings.size()
                        + " listing(s) for " + query + ". Online prices may change.");
            } catch (Exception ex) {
                displayRecentSearchButtons();
                statusLabel.setText("Results added, but search history could not be saved: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        task.setOnFailed(e -> {
            setSearching(false);
            statusLabel.setText("Search failed for " + query + ". Existing results were kept.");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread worker = new Thread(task, "grocery-search-" + UUID.randomUUID());
        worker.setDaemon(true);
        worker.start();
    }

    private static String listingKey(WebProductListing p) {
        // Stable enough to de-duplicate the same retailer offering after opening history or refreshing.
        return p.sourceUrl().trim().toLowerCase(Locale.ROOT) + "|"
                + p.name().trim().toLowerCase(Locale.ROOT) + "|"
                + p.store().trim().toLowerCase(Locale.ROOT);
    }

    private void appendWebListings(List<WebProductListing> listings) {
        for (WebProductListing listing : listings) {
            String key = listingKey(listing);
            // A fresh result for the same listing replaces the old snapshot without wiping other searches.
            WebProductListing previous = displayed.put(key, listing);
            if (previous != null && previous.equals(listing)) continue;
            currentResults.removeIf(product -> {
                WebProductListing linked = sourceByProduct.get(product);
                if (linked != null && listingKey(linked).equals(key)) {
                    sourceByProduct.remove(product);
                    return true;
                }
                return false;
            });
            Product product = new Product("WEB-SEARCH-" + UUID.randomUUID(), listing.name(),
                    listing.brand(), listing.category(), listing.store(), listing.price(),
                    listing.size(), listing.unit());
            currentResults.add(product);
            sourceByProduct.put(product, listing);
        }
        displayResults();
    }

    private void displayResults() {
        List<Product> sorted = new ArrayList<>();
        for (Product product : currentResults) {
            if (activeFilter == null || isVisibleInQuery(product, activeFilter)) {
                sorted.add(product);
            }
        }
        String sort = sortComboBox.getValue();
        if ("Price: Low to High".equals(sort))
            sorted.sort(Comparator.comparingDouble(Product::getPrice));
        else if ("Price: High to Low".equals(sort))
            sorted.sort(Comparator.comparingDouble(Product::getPrice).reversed());
        else if ("Product Name".equals(sort))
            sorted.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
        else if ("Store".equals(sort))
            sorted.sort(Comparator.comparing(Product::getStore, String.CASE_INSENSITIVE_ORDER));
        productsTable.setItems(FXCollections.observableArrayList(sorted));
        resultCountLabel.setText(sorted.size() + " products shown"
                + (activeFilter == null ? " (All)" : " (" + activeFilter + ")"));
    }

    @FXML private void addSelectedProductToPlan() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { notice("Select a product first."); return; }
        if (mainController == null || mainController.getCurrentPlan() == null) {
            notice("Generate a shopping plan before adding products.");
            return;
        }
        WebProductListing listing = sourceByProduct.get(selected);
        if (listing == null) { notice("This product has no saved source."); return; }
        try {
            mainController.addSearchedProduct(listing);
            mainController.showPlan();
        } catch (RuntimeException ex) { notice(ex.getMessage()); }
    }

    // Load each locally saved search once. No Claude requests are issued for history.
    private void loadSavedHistoryIntoAll() {
        try {
            for (SavedSearch saved : historyRepository.load()) {
                savedListingsByQuery.put(saved.query().toLowerCase(Locale.ROOT),
                        List.copyOf(saved.listings()));
                appendWebListings(saved.listings());
            }
            activeFilter = null;
            displayResults();
        } catch (Exception ex) {
            statusLabel.setText("Unable to load saved product searches: " + ex.getMessage());
        }
    }

    private boolean isVisibleInQuery(Product product, String query) {
        WebProductListing listing = sourceByProduct.get(product);
        if (listing == null) return false;
        List<WebProductListing> matching = savedListingsByQuery.getOrDefault(
                query.toLowerCase(Locale.ROOT), List.of());
        String key = listingKey(listing);
        return matching.stream().anyMatch(p -> listingKey(p).equals(key));
    }

    private void displayRecentSearchButtons() {
        recentSearchesBox.getChildren().clear();
        Button allButton = new Button("All");
        allButton.getStyleClass().add("recent-search-button");
        if (activeFilter == null) allButton.getStyleClass().add("selected");
        allButton.setOnAction(event -> {
            activeFilter = null;
            displayResults();
            displayRecentSearchButtons();
            statusLabel.setText("Showing all retrieved and saved product listings.");
        });
        recentSearchesBox.getChildren().add(allButton);

        try {
            for (SavedSearch saved : historyRepository.load()) {
                Button tab = new Button(saved.query());
                tab.getStyleClass().add("recent-search-button");
                if (saved.query().equalsIgnoreCase(activeFilter))
                    tab.getStyleClass().add("selected");
                tab.setOnAction(e -> loadSearchByName(saved.query()));
                recentSearchesBox.getChildren().add(tab);
            }
        } catch (Exception ex) {
            statusLabel.setText("Unable to load recent searches: " + ex.getMessage());
        }
    }

    private void loadSearchByName(String query) {
        if (searching || query == null) return;
        try {
            SavedSearch saved = historyRepository.load().stream()
                    .filter(s -> s.query().equalsIgnoreCase(query)).findFirst().orElseThrow();
            currentQuery = saved.query();
            lastSearchListings = List.copyOf(saved.listings());
            savedListingsByQuery.put(query.toLowerCase(Locale.ROOT), lastSearchListings);
            searchField.setText(currentQuery);
            appendWebListings(lastSearchListings); // Preserve the All collection.
            activeFilter = currentQuery;
            displayResults();
            displayRecentSearchButtons();
            statusLabel.setText("Showing " + currentQuery
                    + " saved products. Prices may be outdated; All retains other searches.");
        } catch (Exception ex) {
            statusLabel.setText("Unable to load search: " + ex.getMessage());
        }
    }

    @FXML private void deleteSavedSearch() {
        if (searching) return;
        if (activeFilter == null) {
            statusLabel.setText("Select a saved search tab before removing it.");
            return;
        }
        String query = activeFilter;
        try {
            historyRepository.delete(query);
            savedListingsByQuery.remove(query.toLowerCase(Locale.ROOT));
            // Rebuild All from the remaining saved history (not the deleted search).
            displayed.clear();
            currentResults.clear();
            sourceByProduct.clear();
            loadSavedHistoryIntoAll();
            currentQuery = "";
            lastSearchListings = List.of();
            activeFilter = null;
            displayResults();
            displayRecentSearchButtons();
            statusLabel.setText("Removed saved search '" + query + "'.");
        } catch (Exception ex) {
            statusLabel.setText("Unable to delete saved search: " + ex.getMessage());
        }
    }

    private void openSource(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid HTTPS source URL");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Desktop browser not available");
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) { notice("Could not open product source: " + ex.getMessage()); }
    }

    private void notice(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Product Search");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
