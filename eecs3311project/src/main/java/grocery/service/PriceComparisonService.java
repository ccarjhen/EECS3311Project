package grocery.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import grocery.model.Product;

public class PriceComparisonService {
    public List<Product> comparePrices(List<Product> products) {
        List<Product> sorted = new ArrayList<>(products);

        sorted.sort(Comparator.comparingDouble(Product::getUnitPrice));

        return sorted;
    }
}
