package grocery.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import grocery.model.Product;
import grocery.model.WebProductListing;

public class CachedWebStoreAdapter implements StoreAdapter {

    private final Map<String, List<WebProductListing>> candidates;

    public CachedWebStoreAdapter(
            Map<String, List<WebProductListing>> candidates) {

        this.candidates =
            candidates == null ? Map.of() : Map.copyOf(candidates);
    }

    @Override
    public List<Product> search(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        // The map keys are the original shopping-item names.
        // This avoids requiring the full phrase to appear
        // inside every retailer product name.
        List<WebProductListing> matches =
            candidates.entrySet()
                .stream()
                .filter(e ->
                    e.getKey().equalsIgnoreCase(query.trim())
                )
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());

        List<Product> products = new ArrayList<>();

        int index = 0;

        for (WebProductListing listing : matches) {
            products.add(new Product(
                "WEB-CACHED-" + (++index),
                listing.name(),
                listing.brand(),
                listing.category(),
                listing.store(),
                listing.price(),
                listing.size(),
                listing.unit()
            ));
        }

        return products;
    }
}
