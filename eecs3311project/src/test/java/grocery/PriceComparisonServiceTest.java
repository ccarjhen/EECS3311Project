package grocery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.model.Product;
import grocery.service.PriceComparisonService;

class PriceComparisonServiceTest {

    private PriceComparisonService service;

    @BeforeEach
    void setUp() {
        service = new PriceComparisonService();
    }

    private Product product(
            String id,
            String name,
            String category,
            String store,
            double price,
            double size,
            String unit) {

        return new Product(
            id,
            name,
            "Test Brand",
            category,
            store,
            price,
            size,
            unit
        );
    }

    // --- TEST 1: GRAMS TO KILOGRAMS ---

    @Test
    void testNormalizeGrams() {

        Product rice = product(
            "R1", "Rice", "Grains",
            "Store A", 3.00, 500, "g"
        );

        assertEquals(
            6.00,
            service.getNormalizedUnitPrice(rice),
            0.001
        );
    }

    // --- TEST 2: MILLILITRES TO LITRES ---

    @Test
    void testNormalizeMillilitres() {

        Product milk = product(
            "M1", "Milk", "Dairy",
            "Store A", 2.50, 500, "ml"
        );

        assertEquals(
            5.00,
            service.getNormalizedUnitPrice(milk),
            0.001
        );
    }

    // --- TEST 3: KILOGRAMS AND LITRES ---

    @Test
    void testNormalizeBaseUnits() {

        Product rice = product(
            "R1", "Rice", "Grains",
            "Store A", 8.00, 2, "kg"
        );

        Product milk = product(
            "M1", "Milk", "Dairy",
            "Store A", 5.00, 2, "L"
        );

        assertEquals(
            4.00,
            service.getNormalizedUnitPrice(rice),
            0.001
        );

        assertEquals(
            2.50,
            service.getNormalizedUnitPrice(milk),
            0.001
        );
    }

    // --- TEST 4: INVALID PACKAGE SIZE ---

    @Test
    void testInvalidPackageSize() {

        Product invalid = product(
            "X1", "Rice", "Grains",
            "Store A", 3.00, 0, "kg"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.getNormalizedUnitPrice(invalid)
        );
    }

    // --- TEST 5: DIFFERENT MEASUREMENT UNITS ---

    @Test
    void testComparableProductsWithDifferentUnits() {

        Product rice500g = product(
            "R1", "Rice", "Grains",
            "Store A", 3.00, 500, "g"
        );

        Product rice1kg = product(
            "R2", "Rice", "Grains",
            "Store B", 5.00, 1, "kg"
        );

        assertTrue(
            service.areComparable(rice500g, rice1kg)
        );
    }

    // --- TEST 6: INCOMPATIBLE PRODUCTS ---

    @Test
    void testIncompatibleProducts() {

        Product rice = product(
            "R1", "Rice", "Grains",
            "Store A", 3.00, 500, "g"
        );

        Product milk = product(
            "M1", "Milk", "Dairy",
            "Store B", 5.00, 1, "L"
        );

        Product riceLiquid = product(
            "R2", "Rice", "Grains",
            "Store B", 2.00, 1, "L"
        );

        assertFalse(service.areComparable(rice, milk));

        assertFalse(
            service.areComparable(rice, riceLiquid)
        );
    }

    // --- TEST 7: SORT BY NORMALIZED UNIT PRICE ---

    @Test
    void testComparePricesSortsCheapestFirst() {

        Product rice500g = product(
            "R1", "Rice", "Grains",
            "Store A", 3.00, 500, "g"
        );

        Product rice1kg = product(
            "R2", "Rice", "Grains",
            "Store B", 5.00, 1, "kg"
        );

        Product rice2kg = product(
            "R3", "Rice", "Grains",
            "Store C", 8.00, 2, "kg"
        );

        Product milk = product(
            "M1", "Milk", "Dairy",
            "Store D", 2.00, 1, "L"
        );

        List<Product> results = service.comparePrices(
            rice500g,
            List.of(
                rice500g,
                rice1kg,
                rice2kg,
                milk
            )
        );

        // Milk must be excluded.
        assertEquals(3, results.size());

        // Normalized prices:
        // Rice 2 kg = $4/kg
        // Rice 1 kg = $5/kg
        // Rice 500 g = $6/kg

        assertEquals("R3", results.get(0).getProdId());
        assertEquals("R2", results.get(1).getProdId());
        assertEquals("R1", results.get(2).getProdId());
    }
}
