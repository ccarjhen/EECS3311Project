
package grocery.service;

import java.util.List;

public record WebSearchResult(
    String productQuery,
    String summary,
    List<String> sourceUrls,
    boolean searchPerformed
) {
    public WebSearchResult {
        sourceUrls = List.copyOf(sourceUrls);
    }
}
