package grocery.model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import grocery.observer.PlanObserver;

public class ShoppingPlan {
    private String name;
    private double estimatedBudget;
    private List<ShoppingItem> shoppingItems;
    private List<PlanObserver> observers = new ArrayList<>();

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
        notifyObservers();
    }

    public void removeItem(ShoppingItem item) {
        if (item == null){
            throw new IllegalArgumentException("Item cannot be null");
        }
        shoppingItems.remove(item);
        notifyObservers();
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

    // Observer methods
    public void addObserver(PlanObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(PlanObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (PlanObserver observer : List.copyOf(observers)) {
            observer.onPlanChanged(this);
        }
    }

    public void markItemPurchased(ShoppingItem item) {

        if (!shoppingItems.contains(item)) {
            throw new IllegalArgumentException(
                "Item does not belong to this plan"
            );
        }

        item.checkPurchased();
        notifyObservers();
    }

    public void changeItemQuantity(ShoppingItem item, int quantity) {
        if (!shoppingItems.contains(item)) {
            throw new IllegalArgumentException(
                "Item does not belong to this plan"
            );
        }

        item.setQuantity(quantity);
        notifyObservers();
    }


    public void setItemPurchased(
            ShoppingItem item,
            boolean purchased) {

        if (!shoppingItems.contains(item)) {
            throw new IllegalArgumentException(
                "Item does not belong to this plan."
            );
        }

        item.setPurchased(purchased);
        notifyObservers();
    }

}   