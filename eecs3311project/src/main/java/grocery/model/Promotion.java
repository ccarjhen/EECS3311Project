package grocery.model;
import java.time.LocalDate;

public class Promotion {
    private String description;
    private double discountPercent;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean membershipRequired;
    private int minimumQuantity;

    public Promotion(String description, double discountPercent, LocalDate startDate, LocalDate endDate, boolean membershipRequired, int minimumQuantity) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Invalid discount percent");
        }

        if(startDate == null || endDate == null || endDate.isBefore(startDate)){
            throw new IllegalArgumentException("Invalid promotion dates");
        }

        if(minimumQuantity < 1){
            throw new IllegalArgumentException("Invalid minimum quantity");
        }
        
        this.description = description;
        this.discountPercent = discountPercent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.membershipRequired = membershipRequired;
        this.minimumQuantity = minimumQuantity;
    }

    public boolean isActive(LocalDate today){
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public String getDescription(){
        return description;
    }

    public double getDiscountPercent(){
        return discountPercent;
    }

    public LocalDate getStartDate(){
        return startDate;
    }

    public LocalDate getEndDate(){
        return endDate;
    }

    public boolean isMembershipRequired(){
        return membershipRequired;
    }

    public int getMinimumQuantity(){
        return minimumQuantity;
    }

}
