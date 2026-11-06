package grocery.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import grocery.model.Product;

public class PriceComparisonService {
    
    public double getNormalizedUnitPrice(Product product) {

        double size = product.getSize();

        if (size <= 0) {
            throw new IllegalArgumentException("Invalid product size");
        }

        String unit = product.getUnit()
                .trim().toLowerCase(Locale.ROOT);

        switch (unit) {
            case "ml":
                return product.getPrice() / (size / 1000.0);

            case "l":
                return product.getPrice() / size;

            case "g":
                return product.getPrice() / (size / 1000.0);

            case "kg":
                return product.getPrice() / size;

            case "pcs":
            case "ct":
            case "count":
                return product.getPrice() / size;

            case "loaf":
            case "block":
                return product.getPrice() / size;

            default:
                throw new IllegalArgumentException(
                    "Unsupported unit: " + unit
                );
        }
    }

    private String unitCategory(Product product) {
        String unit = product.getUnit()
                .trim().toLowerCase(Locale.ROOT);

        switch (unit) {
            case "ml":
            case "l":
                return "volume";

            case "g":
            case "kg":
                return "weight";

            case "pcs":
            case "ct":
            case "count":
                return "count";

            case "loaf":
            case "block":
                return unit;

            default:
                return "unknown";
        }
    }

    public boolean areComparable(Product first, Product second) {
        return first.getName().trim()
                    .equalsIgnoreCase(second.getName().trim())
            && first.getCategory()
                    .equalsIgnoreCase(second.getCategory())
            && !unitCategory(first).equals("unknown")
            && unitCategory(first).equals(unitCategory(second));
    }

    public List<Product> comparePrices(
            Product reference,
            List<Product> products) {

        List<Product> comparable = new ArrayList<>();

        for (Product product : products) {
            if (areComparable(reference, product)) {
                comparable.add(product);
            }
        }

        comparable.sort(
            Comparator.comparingDouble(this::getNormalizedUnitPrice)
        );

        return comparable;
    }
}
