package grocery.agent;

public record ShoppingRequest(String prompt, double budget) {

    public ShoppingRequest {
        if (prompt == null || prompt.isBlank()){
            throw new IllegalArgumentException("Shopping request cannot be empty");
        }
        if(!Double.isFinite(budget) || budget <= 0){
            throw new IllegalArgumentException("Budget must be greater than zero");
        }
    }
}
