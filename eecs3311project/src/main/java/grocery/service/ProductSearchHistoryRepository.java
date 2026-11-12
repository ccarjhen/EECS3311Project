package grocery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import grocery.model.WebProductListing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Stores snapshots, not current prices or guaranteed retailer availability. */
public class ProductSearchHistoryRepository {
    private static final Path FILE = Path.of(
        System.getProperty("user.home"), ".ai-grocery-companion", "product-search-history.json");
    private final ObjectMapper mapper = new ObjectMapper();

    public record SavedSearch(String query, String savedAt, List<WebProductListing> listings) {}

    public List<SavedSearch> load() throws IOException {
        if (!Files.exists(FILE)) return new ArrayList<>();
        List<SavedSearch> saved = mapper.readValue(FILE.toFile(),
            new TypeReference<List<SavedSearch>>() {});
        return saved == null ? new ArrayList<>() : saved;
    }

    public void save(String query, List<WebProductListing> listings) throws IOException {
        if (query == null || query.isBlank())
            throw new IllegalArgumentException("Search query cannot be blank.");
        List<SavedSearch> saved = new ArrayList<>(load());
        saved.removeIf(s -> s.query().equalsIgnoreCase(query));
        saved.add(0, new SavedSearch(query.trim(), Instant.now().toString(), List.copyOf(listings)));
        if (saved.size() > 30) saved = new ArrayList<>(saved.subList(0, 30));
        write(saved);
    }

    public void delete(String query) throws IOException {
        List<SavedSearch> saved = new ArrayList<>(load());
        saved.removeIf(s -> s.query().equalsIgnoreCase(query));
        write(saved);
    }

    private void write(List<SavedSearch> saved) throws IOException {
        Files.createDirectories(FILE.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), saved);
    }
}
