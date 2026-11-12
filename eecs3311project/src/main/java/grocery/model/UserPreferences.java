package grocery.model;

import java.util.HashSet;
import java.util.Set;

public class UserPreferences {

    private Set<String> preferredStores = new HashSet<>();
    private Set<String> preferredBrands = new HashSet<>();
    private Set<String> dietaryRestrictions = new HashSet<>();
    private Set<String> allergies = new HashSet<>();
    private Set<String> excludedItems = new HashSet<>();

    public void addPreferredStore(String store) {
        preferredStores.add(store);
    }

    public void removePreferredStore(String store) {
        preferredStores.remove(store);
    }

    public Set<String> getPreferredStores() {
        return Set.copyOf(preferredStores);
    }

    public void addPreferredBrand(String brand) {
        preferredBrands.add(brand);
    }

    public void removePreferredBrand(String brand) {
        preferredBrands.remove(brand);
    }

    public Set<String> getPreferredBrands() {
        return Set.copyOf(preferredBrands);
    }

    public void addDietaryRestriction(String restriction) {
        dietaryRestrictions.add(restriction);
    }

    public void removeDietaryRestriction(String restriction) {
        dietaryRestrictions.remove(restriction);
    }

    public Set<String> getDietaryRestrictions() {
        return Set.copyOf(dietaryRestrictions);
    }

    public void addAllergy(String allergy) {
        allergies.add(allergy);
    }

    public void removeAllergy(String allergy) {
        allergies.remove(allergy);
    }

    public Set<String> getAllergies() {
        return Set.copyOf(allergies);
    }

    public void addExcludedItem(String item) {
        excludedItems.add(item);
    }

    public void removeExcludedItem(String item) {
        excludedItems.remove(item);
    }

    public Set<String> getExcludedItems() {
        return Set.copyOf(excludedItems);
    }

    public boolean isExcludedItem(String item) {
        return excludedItems.contains(item);
    }
}
