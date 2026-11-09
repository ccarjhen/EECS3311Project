
package grocery.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import grocery.model.Product;
import grocery.model.WebProductListing;

public class WebStoreAdapter implements StoreAdapter {

    private final List<Product> approvedProducts =
        new ArrayList<>();

    // Only call this after reviewing the listing.
    public void addApprovedListing(WebProductListing listing) {

        String id = "WEB-" + (approvedProducts.size() + 1);

        Product product = new Product(
            id,
            listing.name(),
            listing.brand(),
            listing.category(),
            listing.store(),
            listing.price(),
            listing.size(),
            listing.unit()
        );

        approvedProducts.add(product);
    }

    @Override
    public List<Product> search(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String keyword =
            query.trim().toLowerCase(Locale.ROOT);

        List<Product> results = new ArrayList<>();

        for (Product product : approvedProducts) {

            if (product.getName()
                    .toLowerCase(Locale.ROOT)
                    .contains(keyword)) {

                results.add(product);
            }
        }

        return results;
    }
}
