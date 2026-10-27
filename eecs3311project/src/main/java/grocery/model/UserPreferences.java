package grocery.model;

import java.util.HashSet;
import java.util.Set;

public class UserPreferences {
    private Set<String> preferredStores = new HashSet<>();
    private Set<String> prefferedBrands = new HashSet<>();
    private Set<String> dietaryRestrictions = new HashSet<>();
    private Set<String> allergies = new HashSet<>();
    private Set<String> excludedItems = new HashSet<>();

    public void addPreferredStore(String store) {
        preferredStores.add(store);
    }

    public void addPreferredBrand(String brand) {
        prefferedBrands.add(brand);
    }

    public void addDietaryRestriction(String restriction) {
        dietaryRestrictions.add(restriction);
    }

    public void addAllergy(String allergy) {
        allergies.add(allergy);
    }

    public void addExcludedItem(String item) {
        excludedItems.add(item);
    }

    public boolean isExcludedItem(String item) {
        return excludedItems.contains(item);
    }

}