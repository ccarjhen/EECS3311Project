
package grocery.util;

import java.util.Locale;

public final class StoreNameNormalizer {

    private StoreNameNormalizer() {
    }

    public static String normalize(String store) {

        if (store == null || store.isBlank()) {
            return "Unknown";
        }

        String name = store.trim()
            .toLowerCase(Locale.ROOT);

        return switch (name) {
            case "walmart", "walmart canada" -> "Walmart";
            case "no frills", "no frills canada" -> "No Frills";
            case "longos", "longo's" -> "Longos";
            case "metro", "metro canada" -> "Metro";
            case "food basics", "foodbasics" -> "Food Basics";
            case "freshco", "freshco canada" -> "FreshCo";
            case "real canadian superstore",
                 "superstore" -> "Real Canadian Superstore";
            default -> store.trim();
        };
    }
}
