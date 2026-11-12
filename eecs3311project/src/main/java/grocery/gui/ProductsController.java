package grocery.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.model.Product;
import grocery.service.ProductSearchService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class ProductsController {

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

    private final ProductSearchService searchService =
        new ProductSearchService(
            List.of(
                new LongosAdapter(),
                new WalmartAdapter(),
                new NoFrillsAdapter()
            )
        );

    private List<Product> currentResults = new ArrayList<>();

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

            String size = String.format(
                "%.2f %s",
                product.getSize(),
                product.getUnit()
            );

            return new ReadOnlyStringWrapper(size);
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

        // Fixed, non-sortable columns.
        for (TableColumn<Product, ?> column
                : productsTable.getColumns()) {

            column.setResizable(false);
            column.setReorderable(false);
            column.setSortable(false);
        }

        productsTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY
        );

        productsTable.setPlaceholder(
            new Label("No matching products found.")
        );

        // Sorting options.
        sortComboBox.getItems().addAll(
            "Price: Low to High",
            "Price: High to Low",
            "Product Name",
            "Store"
        );

        sortComboBox.setValue("Price: Low to High");

        sortComboBox.setOnAction(event ->
            displayResults()
        );
    }

    @FXML
    private void searchProducts() {

        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            statusLabel.setText(
                "Please enter a product name."
            );
            return;
        }

        try {
            currentResults = new ArrayList<>(
                searchService.search(query)
            );

            displayResults();

            if (currentResults.isEmpty()) {
                statusLabel.setText(
                    "No matching products were found for: "
                    + query
                );
            } else {
                statusLabel.setText(
                    "Showing sample catalogue results for: "
                    + query
                );
            }

        } catch (RuntimeException e) {
            statusLabel.setText(
                "Unable to search products: "
                + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    private void displayResults() {

        List<Product> sorted =
            new ArrayList<>(currentResults);

        String selectedSort = sortComboBox.getValue();

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
