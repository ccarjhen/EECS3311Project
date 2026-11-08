package grocery.agent;

public record RequestedItem(String name, String category, int quantity) {

    public RequestedItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Item name cannot be empty"
            );
        }

        if (category == null || category.isBlank()) {
            category = "Other";
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "Item quantity must be positive"
            );
        }
    }

}
