package grocery.model;

public class Product {
    private String prodId;
    private String name;
    private String brand;
    private String category;
    private String store;
    private double price;
    private double size;
    private String unit;

    public Product(String prodId, String name, String brand, String category, String store, double price, double size, String unit) {
        this.prodId = prodId;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.store = store;
        this.price = price;
        this.size = size;
        this.unit = unit;
    }

    public String getProdId() {
        return prodId;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public String getStore() {
        return store;
    }

    public double getPrice() {
        return price;
    }

    public double getSize() {
        return size;
    }

    public String getUnit() {
        return unit;
    }

    @Override 
    public String toString() {
        return name + " | " + store + " | $" + price;
    }

}

