package grocery;

import java.util.List;
import grocery.adapter.WalmartAdapter;
import grocery.adapter.NoFrillsAdapter;
import java.util.List;

import grocery.adapter.LongosAdapter;
import grocery.adapter.StoreAdapter;
import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;


public class Main {
    public static void main(String[] args) {
        // System.out.println("EECS3311 Grocery Project!");

        // Product milk = new Product(
        //     "P001", "2% Milk", "Neilson", "Dairy", "Longos", 6.49, 4, "L"
        // );

        // Product bread = new Product(
        //     "P002", "White Bread", "Wonder", "Bakery", "Walmart", 3.49, 675, "g"
        // );

        // ShoppingItem milkItem = new ShoppingItem("Milk", "Dairy", 2);
        // milkItem.setProduct(milk);

        // ShoppingItem breadItem = new ShoppingItem("Bread", "Bakery", 1);
        // breadItem.setProduct(bread);

        // ShoppingPlan plan = new ShoppingPlan("Weekly Groceries", 50.00);

        // plan.addItem(milkItem);
        // plan.addItem(breadItem);

        // for (ShoppingItem item : plan.getShoppingItems()) {
        //     System.out.println(item.getName() + "  x" + item.getQuantity() + " = $" + item.getSubtotal());
        // }

        // System.out.println("Total: $" + plan.calculateTotal());
        // System.out.println("On Budget: " + plan.isOnBudget());
        
        // milkItem.checkPurchased();
        // System.out.println("Milk purchased: " + milkItem.isPurchased());

        // // Initialize the store adapter for Longos
        // StoreAdapter store = new LongosAdapter();

        // List<Product> results = store.search("Milk");

        // for (Product product : results) {
        //     System.out.println(product);
        // }

        StoreAdapter longos = new LongosAdapter();
        StoreAdapter walmart = new WalmartAdapter();
        StoreAdapter noFrills = new NoFrillsAdapter();

        ProductSearchService searchService = new ProductSearchService(List.of(longos, walmart, noFrills));

        List<Product> results = searchService.search("Milk");

        System.out.println("=== Search Results ===");
        for (Product product : results) {
            System.out.println(product);
        }

        System.out.println("Total results: " + results.size());
    }
}