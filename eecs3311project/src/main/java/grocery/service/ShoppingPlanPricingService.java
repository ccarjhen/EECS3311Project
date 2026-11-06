package grocery.service;

import java.time.LocalDate;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

// Service for calculating the total price of a shopping plan, considering promotions.
public class ShoppingPlanPricingService {
    private final PromotionService promotionService;

    public ShoppingPlanPricingService(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    public double calculateItemTotal(ShoppingItem item, boolean hasMembership, LocalDate date) {
        Product product = item.getProduct();

        if (product == null) {
            throw new IllegalStateException(
                "No product selected for: " + item.getName()
            );
        }

        return promotionService.getDiscountedTotal(product, item.getQuantity(), hasMembership, date);
    }

    public double calculatePlanTotal(ShoppingPlan plan, boolean hasMembership, LocalDate date) {
        double total = 0;

        for (ShoppingItem item : plan.getShoppingItems()) {
            total += calculateItemTotal(item, hasMembership, date);
        }

        return Math.round(total * 100.0) / 100.0 ;
    }

    public boolean isWithinBudget(ShoppingPlan plan, boolean hasMembership, LocalDate date) {
        return calculatePlanTotal(plan, hasMembership, date) <= plan.getEstimatedBudget();
    }
}
