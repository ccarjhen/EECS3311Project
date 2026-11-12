package grocery.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import grocery.agent.ClaudeClient;
import grocery.model.Product;
import grocery.model.WebProductListing;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import grocery.service.WebSearchResult;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class ProductsController {

    // ========================================
    // FXML COMPONENTS
    // ========================================

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private TableView<Product> productsTable;

    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> brandColumn;
    @FXML private TableColumn<Product, String> storeColumn;
    @FXML private TableColumn<Product, String> sizeColumn;
    @FXML private TableColumn<Product, String> priceColumn;
    @FXML private TableColumn<Product, String> unitPriceColumn;

    @FXML private Label resultCountLabel;
    @FXML private Label statusLabel;

    // Store the current web-search results for sorting.
    private final List<Product> currentResults =
        new ArrayList<>();

    // Retain the original listings and source URLs.
    private List<WebProductListing> currentWebListings =
        List.of();

    // ========================================
    // INITIALIZE
    // ========================================

    @FXML
    public void initialize() {

        nameColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                data.getValue().getName()
            )
        );

        brandColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                data.getValue().getBrand()
            )
        );

        storeColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                data.getValue().getStore()
            )
        );

        sizeColumn.setCellValueFactory(data -> {
            Product product = data.getValue();

            return new ReadOnlyStringWrapper(
                String.format(
                    "%.2f %s",
                    product.getSize(),
                    product.getUnit()
                )
            );
        });

        priceColumn.setCellValueFactory(data ->
            new ReadOnlyStringWrapper(
                String.format(
                    "$%.2f",
                    data.getValue().getPrice()
                )
            )
        );

        unitPriceColumn.setCellValueFactory(data -> {
            Product product = data.getValue();

            if (product.getSize() <= 0) {
                return new ReadOnlyStringWrapper("N/A");
            }

            return new ReadOnlyStringWrapper(
                String.format(
                    "$%.2f/%s",
                    product.getUnitPrice(),
                    product.getUnit()
                )
            );
        });

        for (TableColumn<Product, ?> column
                : productsTable.getColumns()) {

            column.setResizable(false);
            column.setSortable(false);
            column.setReorderable(false);
        }

        productsTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        productsTable.setPlaceholder(
            new Label(
                "Enter a grocery product to search online."
            )
        );

        sortComboBox.getItems().setAll(
            "Price: Low to High",
            "Price: High to Low",
            "Product Name",
            "Store"
        );

        sortComboBox.setValue("Price: Low to High");

        sortComboBox.setOnAction(event ->
            displayResults()
        );

        resultCountLabel.setText("0 products found");
        statusLabel.setText("");
    }

    // ========================================
    // WEB PRODUCT SEARCH
    // ========================================

    @FXML
    private void searchProducts() {

        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            statusLabel.setText(
                "Please enter a product name."
            );
            return;
        }

        // Prevent overlapping searches on this page.
        searchField.setDisable(true);

        statusLabel.setText(
            "Searching online for " + query + "..."
        );

        Task<List<WebProductListing>> task = new Task<>() {

            @Override
            protected List<WebProductListing> call()
                    throws Exception {

                ClaudeClient claude =
                    new ClaudeClient();

                WebProductSearchService webSearch =
                    new WebProductSearchService(claude);

                WebListingExtractor extractor =
                    new WebListingExtractor(claude);

                // Retrieve web search results and citations.
                WebSearchResult research =
                    webSearch.search(query);

                // Convert web evidence into structured listings.
                List<WebProductListing> listings =
                    extractor.extract(
                        research,
                        "Other"
                    );

                // Keep up to three valid priced listings.
                return listings.stream()
                    .filter(p ->
                        Double.isFinite(p.price())
                        && p.price() > 0
                        && Double.isFinite(p.size())
                        && p.size() > 0
                    )
                    .distinct()
                    .limit(3)
                    .toList();
            }
        };

        // ====================================
        // SEARCH SUCCESS
        // ====================================

        task.setOnSucceeded(event -> {

            searchField.setDisable(false);

            currentWebListings = task.getValue();

            currentResults.clear();

            int index = 1;

            for (WebProductListing listing :
                    currentWebListings) {

                Product product = new Product(
                    "WEB-SEARCH-" + index++,
                    listing.name(),
                    listing.brand(),
                    listing.category(),
                    listing.store(),
                    listing.price(),
                    listing.size(),
                    listing.unit()
                );

                currentResults.add(product);
            }

            displayResults();

            if (currentResults.isEmpty()) {

                statusLabel.setText(
                    "No suitable priced online listings "
                    + "were found for " + query + "."
                );

            } else {

                statusLabel.setText(
                    "Showing online product listings for "
                    + query
                    + ". Prices and availability may differ."
                );
            }
        });

        // ====================================
        // SEARCH FAILURE
        // ====================================

        task.setOnFailed(event -> {

            searchField.setDisable(false);

            Throwable error = task.getException();

            currentResults.clear();
            currentWebListings = List.of();

            displayResults();

            statusLabel.setText(
                "Web search failed: "
                + (error == null
                    ? "Unknown error"
                    : error.getMessage())
            );

            if (error != null) {
                error.printStackTrace();
            }
        });

        // Run API requests outside the JavaFX UI thread.
        Thread thread = new Thread(
            task,
            "grocery-product-web-search"
        );

        thread.setDaemon(true);
        thread.start();
    }

    // ========================================
    // SORT AND DISPLAY
    // ========================================

    private void displayResults() {

        List<Product> sorted =
            new ArrayList<>(currentResults);

        String selectedSort =
            sortComboBox.getValue();

        if (selectedSort != null) {

            switch (selectedSort) {

                case "Price: Low to High":
                    sorted.sort(
                        Comparator.comparingDouble(
                            Product::getPrice
                        )
                    );
                    break;

                case "Price: High to Low":
                    sorted.sort(
                        Comparator.comparingDouble(
                            Product::getPrice
                        ).reversed()
                    );
                    break;

                case "Product Name":
                    sorted.sort(
                        Comparator.comparing(
                            Product::getName,
                            String.CASE_INSENSITIVE_ORDER
                        )
                    );
                    break;

                case "Store":
                    sorted.sort(
                        Comparator.comparing(
                            Product::getStore,
                            String.CASE_INSENSITIVE_ORDER
                        )
                    );
                    break;

                default:
                    break;
            }
        }

        productsTable.setItems(
            FXCollections.observableArrayList(sorted)
        );

        resultCountLabel.setText(
            sorted.size() + " products found"
        );
    }
}
