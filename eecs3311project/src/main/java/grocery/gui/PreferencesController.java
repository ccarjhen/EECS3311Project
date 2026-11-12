package grocery.gui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.model.UserPreferences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class PreferencesController {

    @FXML private ListView<String> storesList;
    @FXML private ListView<String> brandsList;
    @FXML private ListView<String> allergiesList;
    @FXML private ListView<String> dietList;
    @FXML private ListView<String> excludedList;

    @FXML private TextField storeField;
    @FXML private TextField brandField;
    @FXML private TextField allergyField;
    @FXML private TextField dietField;
    @FXML private TextField excludedField;

    @FXML private Label statusLabel;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Path SAVE_PATH =
        Path.of(
            System.getProperty("user.home"),
            ".ai-grocery-companion",
            "preferences.json"
        );

    @FXML
    public void initialize() {
        loadPreferences();
    }

    // =====================================
    // ADD / REMOVE HELPERS
    // =====================================

    private void addItem(
            TextField field,
            ListView<String> list) {

        String value = field.getText().trim();

        if (value.isEmpty()) {
            statusLabel.setText("Please enter a value.");
            return;
        }

        boolean exists = list.getItems().stream()
            .anyMatch(s -> s.equalsIgnoreCase(value));

        if (exists) {
            statusLabel.setText("That entry already exists.");
            return;
        }

        list.getItems().add(value);
        list.getItems().sort(
            String.CASE_INSENSITIVE_ORDER
        );

        field.clear();
        statusLabel.setText("Unsaved changes.");
    }

    private void removeItem(ListView<String> list) {

        String selected =
            list.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText(
                "Select an entry to remove."
            );
            return;
        }

        list.getItems().remove(selected);
        statusLabel.setText("Unsaved changes.");
    }

    // =====================================
    // PREFERRED STORES
    // =====================================

    @FXML
    private void addStore() {
        addItem(storeField, storesList);
    }

    @FXML
    private void removeStore() {
        removeItem(storesList);
    }

    // =====================================
    // PREFERRED BRANDS
    // =====================================

    @FXML
    private void addBrand() {
        addItem(brandField, brandsList);
    }

    @FXML
    private void removeBrand() {
        removeItem(brandsList);
    }

    // =====================================
    // ALLERGIES
    // =====================================

    @FXML
    private void addAllergy() {
        addItem(allergyField, allergiesList);
    }

    @FXML
    private void removeAllergy() {
        removeItem(allergiesList);
    }

    // =====================================
    // DIETARY RESTRICTIONS
    // =====================================

    @FXML
    private void addDiet() {
        addItem(dietField, dietList);
    }

    @FXML
    private void removeDiet() {
        removeItem(dietList);
    }

    // =====================================
    // EXCLUDED ITEMS
    // =====================================

    @FXML
    private void addExcluded() {
        addItem(excludedField, excludedList);
    }

    @FXML
    private void removeExcluded() {
        removeItem(excludedList);
    }

    // =====================================
    // BUILD USER PREFERENCES MODEL
    // =====================================

    private UserPreferences buildPreferences() {

        UserPreferences preferences =
            new UserPreferences();

        for (String store : storesList.getItems()) {
            preferences.addPreferredStore(store);
        }

        for (String brand : brandsList.getItems()) {
            preferences.addPreferredBrand(brand);
        }

        for (String allergy : allergiesList.getItems()) {
            preferences.addAllergy(allergy);
        }

        for (String restriction : dietList.getItems()) {
            preferences.addDietaryRestriction(restriction);
        }

        for (String item : excludedList.getItems()) {
            preferences.addExcludedItem(item);
        }

        return preferences;
    }

    // =====================================
    // SAVE TO JSON
    // =====================================

    @FXML
    private void savePreferences() {

        UserPreferences preferences =
            buildPreferences();

        Map<String, List<String>> data =
            new TreeMap<>();

        data.put("preferredStores",
            List.copyOf(preferences.getPreferredStores()));

        data.put("preferredBrands",
            List.copyOf(preferences.getPreferredBrands()));

        data.put("allergies",
            List.copyOf(preferences.getAllergies()));

        data.put("dietaryRestrictions",
            List.copyOf(preferences.getDietaryRestrictions()));

        data.put("excludedItems",
            List.copyOf(preferences.getExcludedItems()));

        try {
            Files.createDirectories(
                SAVE_PATH.getParent()
            );

            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(SAVE_PATH.toFile(), data);

            statusLabel.setText(
                "Preferences saved successfully!"
            );

        } catch (IOException e) {
            statusLabel.setText(
                "Unable to save preferences: "
                + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    // =====================================
    // LOAD SAVED PREFERENCES
    // =====================================

    private void loadPreferences() {

        if (!Files.exists(SAVE_PATH)) {
            statusLabel.setText(
                "No saved preferences yet."
            );
            return;
        }

        try {
            Map<String, List<String>> data =
                mapper.readValue(
                    SAVE_PATH.toFile(),
                    new TypeReference<
                        Map<String, List<String>>
                    >() {}
                );

            loadList(storesList,
                data.get("preferredStores"));

            loadList(brandsList,
                data.get("preferredBrands"));

            loadList(allergiesList,
                data.get("allergies"));

            loadList(dietList,
                data.get("dietaryRestrictions"));

            loadList(excludedList,
                data.get("excludedItems"));

            statusLabel.setText(
                "Saved preferences loaded."
            );

        } catch (IOException | RuntimeException e) {
            statusLabel.setText(
                "Unable to load saved preferences."
            );
            e.printStackTrace();
        }
    }

    private void loadList(
            ListView<String> list,
            List<String> values) {

        list.getItems().clear();

        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    list.getItems().add(value);
                }
            }

            list.getItems().sort(
                String.CASE_INSENSITIVE_ORDER
            );
        }
    }
}
