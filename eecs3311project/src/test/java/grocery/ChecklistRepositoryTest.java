package grocery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.service.ChecklistRepository;

class ChecklistRepositoryTest {

    @TempDir
    Path tempDir;

    private ChecklistRepository repository;
    private Path file;
    private ShoppingPlan testPlan;

    // --- SETUP ---

    @BeforeEach
    void setUp() {

        file = tempDir.resolve("checklists.json");

        repository = new ChecklistRepository(file);

        testPlan = new ShoppingPlan(
            "Grocery Checklist",
            50.00
        );

        ShoppingItem milk =
            new ShoppingItem("Milk", "Dairy", 2);

        milk.setProduct(new Product(
            "M001",
            "2% Milk",
            "Test Brand",
            "Dairy",
            "Store A",
            5.99,
            2.0,
            "L"
        ));

        milk.setPurchased(true);

        ShoppingItem bread =
            new ShoppingItem("Bread", "Bakery", 1);

        bread.setProduct(new Product(
            "B001",
            "White Bread",
            "Test Brand",
            "Bakery",
            "Store B",
            2.49,
            1.0,
            "loaf"
        ));

        testPlan.addItem(milk);
        testPlan.addItem(bread);
    }

    // --- TEST 1 — EMPTY REPOSITORY ---

    @Test
    void testEmptyRepository() throws IOException {

        assertTrue(repository.listNames().isEmpty());

        assertFalse(repository.exists("Grocery Checklist"));
    }

    // --- TEST 2 — SAVE CHECKLIST ---

    @Test
    void testSaveChecklist() throws IOException {

        repository.save("My Groceries", testPlan);

        assertTrue(repository.exists("My Groceries"));

        assertEquals(
            1,
            repository.listNames().size()
        );
    }

    // --- TEST 3 — LOAD CHECKLIST ---

    @Test
    void testLoadChecklist() throws IOException {

        repository.save("My Groceries", testPlan);

        ShoppingPlan loaded =
            repository.load("My Groceries");

        assertEquals("My Groceries", loaded.getName());

        assertEquals(
            2,
            loaded.getShoppingItems().size()
        );

        assertEquals(
            50.00,
            loaded.getEstimatedBudget(),
            0.001
        );
    }

    // --- TEST 4 — PRESERVE QUANTITY ---

    @Test
    void testPreserveItemQuantity() throws IOException {

        repository.save("My Groceries", testPlan);

        ShoppingPlan loaded =
            repository.load("My Groceries");

        ShoppingItem milk = loaded.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Milk"))
            .findFirst()
            .orElseThrow();

        assertEquals(2, milk.getQuantity());
    }

    // --- TEST 5 — PRESERVE PURCHASED STATUS ---

    @Test
    void testPreservePurchasedStatus()
            throws IOException {

        repository.save("My Groceries", testPlan);

        ShoppingPlan loaded =
            repository.load("My Groceries");

        ShoppingItem milk = loaded.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Milk"))
            .findFirst()
            .orElseThrow();

        ShoppingItem bread = loaded.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Bread"))
            .findFirst()
            .orElseThrow();

        assertTrue(milk.isPurchased());
        assertFalse(bread.isPurchased());
    }

    // --- TEST 6 — PRESERVE PRODUCT INFORMATION ---

    @Test
    void testPreserveProductInformation()
            throws IOException {

        repository.save("My Groceries", testPlan);

        ShoppingPlan loaded =
            repository.load("My Groceries");

        ShoppingItem milk = loaded.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Milk"))
            .findFirst()
            .orElseThrow();

        Product product = milk.getProduct();

        assertNotNull(product);

        assertEquals("M001", product.getProdId());
        assertEquals("2% Milk", product.getName());
        assertEquals("Store A", product.getStore());

        assertEquals(
            5.99,
            product.getPrice(),
            0.001
        );

        assertEquals(2.0, product.getSize(), 0.001);
        assertEquals("L", product.getUnit());
    }

    // --- TEST 7 — RENAME CHECKLIST ---

    @Test
    void testRenameChecklist() throws IOException {

        repository.save("Old Name", testPlan);

        repository.rename("Old Name", "New Name");

        assertFalse(repository.exists("Old Name"));
        assertTrue(repository.exists("New Name"));

        ShoppingPlan loaded =
            repository.load("New Name");

        assertEquals("New Name", loaded.getName());
        assertEquals(2, loaded.getShoppingItems().size());
    }

    // --- TEST 8 — DELETE CHECKLIST ---

    @Test
    void testDeleteChecklist() throws IOException {

        repository.save("My Groceries", testPlan);

        assertTrue(repository.exists("My Groceries"));

        repository.delete("My Groceries");

        assertFalse(repository.exists("My Groceries"));

        assertTrue(repository.listNames().isEmpty());
    }

    // --- TEST 9 — LOAD UNKNOWN CHECKLIST ---

    @Test
    void testLoadNonexistentChecklist() {

        assertThrows(
            IllegalArgumentException.class,
            () -> repository.load("Unknown Checklist")
        );
    }

    // --- TEST 10 — RESTART SIMULATION ---

    @Test
    void testPersistenceAfterRestart()
            throws IOException {

        repository.save("My Groceries", testPlan);

        // New instance reading the same JSON file.
        ChecklistRepository reopened =
            new ChecklistRepository(file);

        List<String> names = reopened.listNames();

        assertEquals(1, names.size());
        assertTrue(names.contains("My Groceries"));

        ShoppingPlan loaded =
            reopened.load("My Groceries");

        assertEquals(
            2,
            loaded.getShoppingItems().size()
        );

        assertEquals(
            50.00,
            loaded.getEstimatedBudget(),
            0.001
        );

        ShoppingItem milk = loaded.getShoppingItems()
            .stream()
            .filter(item -> item.getName().equals("Milk"))
            .findFirst()
            .orElseThrow();

        assertTrue(milk.isPurchased());
        assertEquals(2, milk.getQuantity());
        assertNotNull(milk.getProduct());
    }
}
