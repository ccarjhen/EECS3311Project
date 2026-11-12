package grocery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.model.Product;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

public class ChecklistRepository {

    private static final Path FILE = Path.of(
        System.getProperty("user.home"),
        ".ai-grocery-companion",
        "checklists.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    // Data formats for saving JSON

    public record SavedProduct(
        String id,
        String name,
        String brand,
        String category,
        String store,
        double price,
        double size,
        String unit
    ) {}

    public record SavedItem(
        String name,
        String category,
        int quantity,
        boolean purchased,
        SavedProduct product
    ) {}

    public record SavedChecklist(
        double budget,
        List<SavedItem> items
    ) {}

    // Read all saved checklists

    private Map<String, SavedChecklist> readAll()
            throws IOException {

        if (!Files.exists(FILE)) {
            return new LinkedHashMap<>();
        }

        Map<String, SavedChecklist> data =
            mapper.readValue(
                FILE.toFile(),
                new TypeReference<
                    LinkedHashMap<String, SavedChecklist>
                >() {}
            );

        return data == null
            ? new LinkedHashMap<>()
            : data;
    }

    // Write all checklists to JSON

    private void writeAll(
            Map<String, SavedChecklist> data)
            throws IOException {

        Files.createDirectories(FILE.getParent());

        mapper.writerWithDefaultPrettyPrinter()
              .writeValue(FILE.toFile(), data);
    }

    // Get saved checklist names

    public List<String> listNames() throws IOException {
        return new ArrayList<>(readAll().keySet());
    }

    // Check whether a name exists

    public boolean exists(String name) throws IOException {
        return readAll().containsKey(name);
    }

    // Save a shopping plan as a checklist

    public void save(String name, ShoppingPlan plan)
            throws IOException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Checklist name cannot be empty."
            );
        }

        if (plan == null) {
            throw new IllegalArgumentException(
                "Shopping plan cannot be null."
            );
        }

        List<SavedItem> items = new ArrayList<>();

        for (ShoppingItem item : plan.getShoppingItems()) {

            Product product = item.getProduct();
            SavedProduct savedProduct = null;

            if (product != null) {
                savedProduct = new SavedProduct(
                    product.getProdId(),
                    product.getName(),
                    product.getBrand(),
                    product.getCategory(),
                    product.getStore(),
                    product.getPrice(),
                    product.getSize(),
                    product.getUnit()
                );
            }

            items.add(new SavedItem(
                item.getName(),
                item.getCategory(),
                item.getQuantity(),
                item.isPurchased(),
                savedProduct
            ));
        }

        Map<String, SavedChecklist> data = readAll();

        data.put(
            name,
            new SavedChecklist(
                plan.getEstimatedBudget(),
                items
            )
        );

        writeAll(data);
    }

    // Load a saved checklist

    public ShoppingPlan load(String name)
            throws IOException {

        SavedChecklist saved = readAll().get(name);

        if (saved == null) {
            throw new IllegalArgumentException(
                "Checklist not found: " + name
            );
        }

        ShoppingPlan plan = new ShoppingPlan(
            name,
            saved.budget()
        );

        for (SavedItem savedItem : saved.items()) {

            ShoppingItem item = new ShoppingItem(
                savedItem.name(),
                savedItem.category(),
                savedItem.quantity()
            );

            item.setPurchased(savedItem.purchased());

            SavedProduct p = savedItem.product();

            if (p != null) {
                Product product = new Product(
                    p.id(),
                    p.name(),
                    p.brand(),
                    p.category(),
                    p.store(),
                    p.price(),
                    p.size(),
                    p.unit()
                );

                item.setProduct(product);
            }

            plan.addItem(item);
        }

        return plan;
    }

    // Rename a saved checklist

    public void rename(
            String oldName,
            String newName)
            throws IOException {

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException(
                "New name cannot be empty."
            );
        }

        Map<String, SavedChecklist> data = readAll();

        if (!data.containsKey(oldName)) {
            throw new IllegalArgumentException(
                "Checklist not found."
            );
        }

        if (!oldName.equals(newName)
                && data.containsKey(newName)) {
            throw new IllegalArgumentException(
                "A checklist with this name already exists."
            );
        }

        SavedChecklist checklist = data.remove(oldName);

        data.put(newName, checklist);

        writeAll(data);
    }

    // Delete a saved checklist

    public void delete(String name) throws IOException {

        Map<String, SavedChecklist> data = readAll();

        data.remove(name);

        writeAll(data);
    }
}
