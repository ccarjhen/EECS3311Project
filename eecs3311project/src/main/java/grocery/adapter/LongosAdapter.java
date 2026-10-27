package grocery.adapter;
import grocery.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LongosAdapter implements StoreAdapter {

    private final List<Product> products = new ArrayList<>();

    public LongosAdapter() {
        // Initialize with some sample products
        products.add(new Product("L001", "Brown Eggs", "Conestoga Farms", "Dairy", "Longos", 12.09, 18, "ct"));
        products.add(new Product("L002", "Whole Milk", "Organic Valley", "Dairy", "Longos", 4.99, 1, "L"));
        products.add(new Product("L003", "White Bread", "Wonder", "Bakery", "Longos", 3.49, 1, "loaf"));
        products.add(new Product("L004", "Cheddar Cheese", "Cabot", "Dairy", "Longos", 5.99, 1, "block"));
        products.add(new Product("L005", "Bananas", "Dole", "Produce", "Longos", 1.29, 1, "kg"));

    }

    @Override
    public List<Product> search(String query) {
        List<Product> result = new ArrayList<>();

        if(query == null || query.isEmpty()) {
            return result;
        }

        String keyword = query.toLowerCase(Locale.ROOT).trim();

        for (Product product : products) {
            if (product.getName().toLowerCase(Locale.ROOT).contains(keyword)) {
                result.add(product);
            }
        }
        return result;
    }
}
