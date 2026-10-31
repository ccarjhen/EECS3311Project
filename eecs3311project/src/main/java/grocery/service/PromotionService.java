package grocery.service;

import java.time.LocalDate;

import grocery.model.Product;
import grocery.model.Promotion;


public class PromotionService {

    public String checkPromotions(Product product, int quantity, boolean hasMembership, LocalDate today){

        if(product == null || today == null || quantity < 1){
            throw new IllegalArgumentException("Invalid input");
        }

        if(!product.hasPromotion()){
            return "No promotion available for this product";
        }

        Promotion promotion = product.getPromotion();

        if(!promotion.isActive(today)){
            return "Promotion is not active today";
        }

        if(promotion.isMembershipRequired() && !hasMembership){
            return "Promotion requires membership";
        }

        if(quantity < promotion.getMinimumQuantity()){
            return "Promotion requires minimum quantity of " + promotion.getMinimumQuantity();
        }

        return "Eligible";
    }

    public double calculateDiscount(Product product, int quantity, boolean hasMembership, LocalDate today){

        String status = checkPromotions(product, quantity, hasMembership, today);

        if(!status.equals("Eligible")){
            return 0;
        }

        double regularTotal = product.getPrice() * quantity;

        return regularTotal * (product.getPromotion().getDiscountPercent() / 100);
    }

    public double getDiscountedTotal(Product product, int quantity, boolean hasMembership, LocalDate today){
        
        if(product == null || quantity < 1){
            throw new IllegalArgumentException("Invalid input");
        }

        double regularTotal = product.getPrice() * quantity;

        return regularTotal - calculateDiscount(product, quantity, hasMembership, today);

    }
}
