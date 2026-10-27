package grocery.model;

public class ShoppingItem {

    private String name;
    private String category;
    private int quantity;
    private boolean purchased;
    private Product product;

    public ShoppingItem(String name, String category, int quantity) {
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.purchased = false;
        this.product = null;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public void checkPurchased() {
        this.purchased = true;
    }

    public void setPurchased(boolean purchased) { 
        this.purchased = purchased;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0 ){
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        this.quantity = quantity;
    }

    public double getSubtotal() {
        if(product == null){
            return 0;
        }
        return product.getPrice() * quantity;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public Product getProduct() {
        return product;
    }
}