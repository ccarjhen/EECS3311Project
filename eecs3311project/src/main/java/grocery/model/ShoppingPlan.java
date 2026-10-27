package grocery.model;
import java.util.List;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class ShoppingPlan {
    private String name;
    private double estimatedBudget;
    private List<ShoppingItem> shoppingItems;

    public ShoppingPlan(String name, double estimatedBudget) {
        this.name = name;
        this.estimatedBudget = estimatedBudget;
        this.shoppingItems = new ArrayList<>();
    }

    public void addItem(ShoppingItem item) {
        if (item == null){
            throw new IllegalArgumentException("Item cannot be null");
        }
        shoppingItems.add(item);
    }

    public void removeItem(ShoppingItem item) {
        if (item == null){
            throw new IllegalArgumentException("Item cannot be null");
        }
        shoppingItems.remove(item);
    }

    public double calculateTotal() {
        double total = 0;

        for (ShoppingItem item : shoppingItems) {
            total = total + item.getSubtotal();
        }
        return total;
    }

    public boolean isOnBudget() {
        return calculateTotal() <= estimatedBudget;
    }

    public String getName() {
        return name;
    }

    public double getEstimatedBudget() {
        return estimatedBudget;
    }

    public List<ShoppingItem> getShoppingItems() {
        return Collections.unmodifiableList(shoppingItems);
    }
}