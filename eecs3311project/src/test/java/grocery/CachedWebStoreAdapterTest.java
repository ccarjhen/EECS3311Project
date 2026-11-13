package grocery;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.adapter.CachedWebStoreAdapter;
import grocery.adapter.StoreAdapter;
import grocery.model.Product;
import grocery.model.WebProductListing;

class CachedWebStoreAdapterTest {

    private CachedWebStoreAdapter adapter;

    private WebProductListing listing(
            String name,
            String category,
            String store,
            double price,
            double size,
            String unit) {

        return new WebProductListing(
            name,
            "Test Brand",
            category,
            store,
            price,
            size,
            unit,
            "https://example.com/product"
        );
    }

    @BeforeEach
    void setUp() {

        Map<String, List<WebProductListing>> cache =
            Map.of(

                "Milk", List.of(
                    listing(
                        "2% Milk",
                        "Dairy",
                        "Store A",
                        5.99,
                        2.0,
                        "L"
                    ),

                    listing(
                        "Whole Milk",
                        "Dairy",
                        "Store B",
                        6.49,
                        2.0,
                        "L"
                    )
                ),

                "Bread", List.of(
                    listing(
                        "White Bread",
                        "Bakery",
                        "Store A",
                        2.49,
                        675,
                        "g"
                    )
                )
            );

        adapter = new CachedWebStoreAdapter(cache);
    }

    // --- TEST 1: RETRIEVE CACHED PRODUCTS ---

    @Test
    void testSearchReturnsCachedProducts() {

        List<Product> results = adapter.search("Milk");

        assertEquals(2, results.size());

        assertEquals(
            "2% Milk",
            results.get(0).getName()
        );

        assertEquals(
            "Whole Milk",
            results.get(1).getName()
        );
    }

    // --- TEST 2: CASE INSENSITIVE SEARCH ---

    @Test
    void testCaseInsensitiveSearch() {

        List<Product> results =
            adapter.search("mILk");

        assertEquals(2, results.size());
    }

    // --- TEST 3: TRIM SEARCH QUERY ---

    @Test
    void testSearchTrimsWhitespace() {

        List<Product> results =
            adapter.search("  Milk  ");

        assertEquals(2, results.size());
    }

    // --- TEST 4: UNKNOWN PRODUCT ---

    @Test
    void testUnknownProductReturnsEmptyList() {

        List<Product> results =
            adapter.search("Eggs");

        assertTrue(results.isEmpty());
    }

    // --- TEST 5: NULL AND EMPTY SEARCH ---

    @Test
    void testInvalidSearchReturnsEmptyList() {

        assertTrue(adapter.search(null).isEmpty());

        assertTrue(adapter.search("").isEmpty());

        assertTrue(adapter.search("   ").isEmpty());
    }

    // --- TEST 6: WEB LISTING CONVERSION ---

    @Test
    void testWebListingConvertedToProduct() {

        List<Product> results =
            adapter.search("Bread");

        assertEquals(1, results.size());

        Product product = results.get(0);

        assertEquals(
            "White Bread",
            product.getName()
        );

        assertEquals(
            "Test Brand",
            product.getBrand()
        );

        assertEquals(
            "Bakery",
            product.getCategory()
        );

        assertEquals(
            "Store A",
            product.getStore()
        );

        assertEquals(
            2.49,
            product.getPrice(),
            0.001
        );

        assertEquals(
            675,
            product.getSize(),
            0.001
        );

        assertEquals("g", product.getUnit());

        // Confirms the Adapter interface is implemented.
        assertInstanceOf(StoreAdapter.class, adapter);
    }
}
