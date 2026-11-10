
package grocery.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import grocery.agent.ClaudeClient;

public class WebProductSearchService {

    private final ClaudeClient claudeClient;

    public WebProductSearchService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public WebSearchResult search(String productName)
            throws Exception {

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                "Product name cannot be empty"
            );
        }

        
        String prompt = """
            Search online grocery product listings in Ontario, Canada.

            Product: %s

            Search these retailers where relevant:
            - Walmart Canada
            - No Frills
            - Longo's
            - Real Canadian Superstore
            - Food Basics
            - Metro
            - FreshCo

            Return at most 5 relevant product listings
            across different retailers.

            For each listing report:
            - Product name
            - Brand
            - Store
            - Package size and unit
            - Listed price in CAD, if available
            - Source URL

            Do not invent product listings or prices.
            Prefer retailer product pages.
            Avoid duplicate listings.
            If a retailer does not show a usable price,
            report the price as unavailable.
            """.formatted(productName);


        JsonNode response = claudeClient.searchWeb(prompt);

        StringBuilder summary = new StringBuilder();
        Set<String> urls = new LinkedHashSet<>();

        boolean searchPerformed = false;
        List<String> searchErrors = new ArrayList<>();

        for (JsonNode block : response.path("content")) {

            String type = block.path("type").asText();

            if ("server_tool_use".equals(type) &&
                "web_search".equals(block.path("name").asText())) {

                searchPerformed = true;
            }

            if ("text".equals(type)) {
                summary.append(block.path("text").asText());
                summary.append("\n");

                // Read citations attached to each text block
                for (JsonNode citation : block.path("citations")) {

                    if ("web_search_result_location".equals(
                            citation.path("type").asText())) {

                        String url = citation.path("url").asText();

                        if (!url.isBlank()) {
                            urls.add(url);
                        }
                    }
                }
            }

            if ("web_search_tool_result".equals(type)) {

                JsonNode content = block.path("content");

                if (content.isObject() &&
                    "web_search_tool_result_error".equals(
                        content.path("type").asText())) {

                    searchErrors.add(
                        content.path("error_code").asText()
                    );
                }

                // Collect source URLs from search result blocks
                if (content.isArray()) {
                    for (JsonNode result : content) {

                        String url = result.path("url").asText();

                        if (!url.isBlank()) {
                            urls.add(url);
                        }
                    }
                }
            }
        }

        if (!searchErrors.isEmpty()) {
            throw new IllegalStateException(
                "Web search error: " +
                String.join(", ", searchErrors)
            );
        }

        if (!searchPerformed) {
            throw new IllegalStateException(
                "Claude did not execute a web search"
            );
        }

        return new WebSearchResult(
            productName,
            summary.toString().trim(),
            List.copyOf(urls),
            searchPerformed
        );
    }
}
