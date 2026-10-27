package grocery.adapter;
import grocery.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public class NoFrillsAdapter implements StoreAdapter {

    private List<Product> products = new ArrayList<>();

    public NoFrillsAdapter() {
        // Initialize the No Frills products list
        products.add(new Product("P001", "2% Milk", "Neilson", "Dairy", "No Frills", 6.49, 4, "L"));
        products.add(new Product("P002", "White Bread", "Wonder", "Bakery", "No Frills", 3.49, 675, "g"));
        products.add(new Product("P003", "Eggs", "Organic", "Dairy", "No Frills", 4.99, 12, "pcs"));
        products.add(new Product("P004", "Bananas", "Dole", "Produce", "No Frills", 1.29, 6, "pcs"));
        products.add(new Product("P005", "Chicken Breast", "Maple Leaf", "Meat", "No Frills", 9.99, 500, "g"));
        products.add(new Product("P006", "Orange Juice", "Tropicana", "Beverages", "No Frills", 4.99, 1, "L"));
        products.add(new Product("P007", "Apples", "Gala", "Produce", "No Frills", 2.99, 6, "pcs"));
        products.add(new Product("P008", "Yogurt", "Danone", "Dairy", "No Frills", 3.49, 500, "g"));
        products.add(new Product("P009", "Butter", "President", "Dairy", "No Frills", 5.49, 250, "g"));
        products.add(new Product("P010", "Cereal", "Kellogg's", "Breakfast", "No Frills", 4.99, 500, "g"));
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
