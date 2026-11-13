package grocery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import grocery.model.WebProductListing;
import grocery.service.ProductSearchHistoryRepository;
import grocery.service.ProductSearchHistoryRepository.SavedSearch;

class ProductSearchHistoryRepositoryTest {

    @TempDir
    Path tempDir;

    private ProductSearchHistoryRepository repository;
    private Path historyFile;

    @BeforeEach
    void setUp() {

        historyFile = tempDir.resolve(
            "product-search-history.json"
        );

        repository = new ProductSearchHistoryRepository(
            historyFile
        );
    }

    // Test fixture: sample listing, not a real web search.
    private WebProductListing product(
            String name,
            double price) {

        return new WebProductListing(
            name,
            "Test Brand",
            "Dairy",
            "Test Store",
            price,
            1.0,
            "L",
            "https://example.com/product"
        );
    }

    // --- TEST 1: EMPTY HISTORY ---

    @Test
    void testEmptyHistory() throws IOException {

        assertTrue(repository.load().isEmpty());
    }

    // --- TEST 2: SAVE SEARCH ---

    @Test
    void testSaveSearch() throws IOException {

        repository.save(
            "Milk",
            List.of(product("2% Milk", 5.99))
        );

        List<SavedSearch> saved = repository.load();

        assertEquals(1, saved.size());
        assertEquals("Milk", saved.get(0).query());
        assertEquals(1, saved.get(0).listings().size());
        assertNotNull(saved.get(0).savedAt());
    }

    // --- TEST 3: RELOAD FROM DISK ---

    @Test
    void testReloadSavedSearch() throws IOException {

        repository.save(
            "Bread",
            List.of(product("White Bread", 2.49))
        );

        // Simulates reopening the application.
        ProductSearchHistoryRepository reopened =
            new ProductSearchHistoryRepository(
                historyFile
            );

        List<SavedSearch> loaded = reopened.load();

        assertEquals(1, loaded.size());
        assertEquals("Bread", loaded.get(0).query());

        assertEquals(
            "White Bread",
            loaded.get(0).listings().get(0).name()
        );

        assertEquals(
            2.49,
            loaded.get(0).listings().get(0).price(),
            0.001
        );
    }

    // --- TEST 4: OVERWRITE SAVED SEARCH ---

    @Test
    void testOverwriteSearch() throws IOException {

        repository.save(
            "Milk",
            List.of(product("Old Milk", 4.99))
        );

        repository.save(
            "milk",
            List.of(product("New Milk", 5.49))
        );

        List<SavedSearch> saved = repository.load();

        // Same query, case-insensitive.
        assertEquals(1, saved.size());

        assertEquals(
            "New Milk",
            saved.get(0).listings().get(0).name()
        );

        assertEquals(
            5.49,
            saved.get(0).listings().get(0).price(),
            0.001
        );
    }

    // --- TEST 5: DELETE SEARCH ---    

    @Test
    void testDeleteSavedSearch() throws IOException {

        repository.save(
            "Bread",
            List.of(product("Bread", 2.49))
        );

        repository.save(
            "Milk",
            List.of(product("Milk", 5.99))
        );

        repository.delete("Bread");

        List<SavedSearch> remaining = repository.load();

        assertEquals(1, remaining.size());
        assertEquals("Milk", remaining.get(0).query());
    }

    // --- TEST 6: INVALID QUERY ---

    @Test
    void testBlankQueryRejected() {

        assertThrows(
            IllegalArgumentException.class,
            () -> repository.save(
                "   ",
                List.of()
            )
        );
    }

    // --- TEST 7: HISTORY LIMIT ---

    @Test
    void testMaximumHistorySize() throws IOException {

        for (int i = 1; i <= 35; i++) {

            repository.save(
                "Search " + i,
                List.of(product("Product " + i, i))
            );
        }

        List<SavedSearch> saved = repository.load();

        assertEquals(30, saved.size());

        // Most recently saved search appears first.
        assertEquals("Search 35", saved.get(0).query());

        // Oldest retained search appears last.
        assertEquals("Search 6", saved.get(29).query());
    }
}
