package grocery.adapter;
import grocery.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public class WalmartAdapter implements StoreAdapter {

    private final List<Product> products = new ArrayList<>();

    public WalmartAdapter() {
        // Initialize the Walmart products list
        products.add(new Product("P001", "2% Milk", "Neilson", "Dairy", "Walmart", 6.49, 4, "L"));
        products.add(new Product("P002", "White Bread", "Wonder", "Bakery", "Walmart", 3.49, 675, "g"));
        products.add(new Product("P003", "Eggs", "Organic", "Dairy", "Walmart", 4.99, 12, "pcs"));
        products.add(new Product("P004", "Bananas", "Dole", "Produce", "Walmart", 1.29, 6, "pcs"));
        products.add(new Product("P005", "Chicken Breast", "Maple Leaf", "Meat", "Walmart", 9.99, 500, "g"));
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
