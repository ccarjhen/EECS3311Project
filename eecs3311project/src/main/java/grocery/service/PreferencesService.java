package grocery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.model.UserPreferences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PreferencesService {

    private static final Path FILE = Path.of(
        System.getProperty("user.home"),
        ".ai-grocery-companion",
        "preferences.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    public UserPreferences load() throws IOException {

        UserPreferences preferences =
            new UserPreferences();

        if (!Files.exists(FILE)) {
            return preferences;
        }

        Map<String, List<String>> data =
            mapper.readValue(
                FILE.toFile(),
                new TypeReference<
                    Map<String, List<String>>
                >() {}
            );

        if (data == null) {
            throw new IOException(
                "Preferences file is empty or invalid."
            );
        }

        for (String store :
                data.getOrDefault("preferredStores", List.of())) {
            preferences.addPreferredStore(store);
        }

        for (String brand :
                data.getOrDefault("preferredBrands", List.of())) {
            preferences.addPreferredBrand(brand);
        }

        for (String allergy :
                data.getOrDefault("allergies", List.of())) {
            preferences.addAllergy(allergy);
        }

        for (String restriction :
                data.getOrDefault("dietaryRestrictions", List.of())) {
            preferences.addDietaryRestriction(restriction);
        }

        for (String item :
                data.getOrDefault("excludedItems", List.of())) {
            preferences.addExcludedItem(item);
        }

        return preferences;
    }
}
