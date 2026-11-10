
package grocery.model;

// This record performs structural validation. It doesn't independently establish that the price is accurate.

public record WebProductListing(
    String name,
    String brand,
    String category,
    String store,
    double price,
    double size,
    String unit,
    String sourceUrl
) {
    public WebProductListing {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invalid product name");
        }

        if (store == null || store.isBlank()) {
            throw new IllegalArgumentException("Invalid store");
        }

        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("Invalid price");
        }

        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("Invalid size");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Invalid unit");
        }

        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Invalid category");
        }

        if (sourceUrl == null ||
            !sourceUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Invalid source URL");
        }
    }
}
