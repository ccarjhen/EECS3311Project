
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

        
        // String prompt = """
        //     Search online grocery product listings in Ontario, Canada.

        //     Product: %s

        //     Search these retailers where relevant:
        //     - Walmart Canada
        //     - No Frills
        //     - Longo's
        //     - Real Canadian Superstore
        //     - Food Basics
        //     - Metro
        //     - FreshCo

        //     Perform up to 3 web searches when necessary.
        //     Find up to 3 relevant product listings, preferably from
        //     different retailers.

        //     For each listing report:
        //     - Product name
        //     - Brand
        //     - Store
        //     - Package size and unit
        //     - Listed price in CAD, if available
        //     - Source URL

        //     Prioritize retailer product pages.
        //     Do not invent prices, sizes, or product availability.
        //     Do not continue searching after a sufficient set of
        //     reliable listings has been found.
        //     If fewer than 3 listings can be verified, return fewer.

        //     Do not invent product listings or prices.
        //     Prefer retailer product pages.
        //     Avoid duplicate listings.
        //     If a retailer does not show a usable price,
        //     report the price as unavailable.
        //     """.formatted(productName);

        String prompt = """
            Find grocery products sold online in Ontario, Canada.

            Product: %s

            Retailers:
            - Walmart Canada
            - No Frills Canada
            - Longo's
            - Real Canadian Superstore
            - Food Basics
            - Metro
            - FreshCo

            Search official retailer product listings.

            Search instructions:
            - Use at most 3 web searches.
            - Prefer obtaining multiple products from the
            results of a single search.
            - Stop searching once sufficient information
            has been found.
            - Do not retry the same failed search.
            - Return up to 3 relevant products total.

            For each product provide:
            - Exact product name
            - Brand
            - Store
            - Package size and unit
            - Listed price in CAD
            - Source URL

            Rules:
            - Only use information supported by search results.
            - Do not invent prices, availability, or URLs.
            - If a price cannot be verified, omit that product.
            - If fewer than 3 products are found, return fewer.
            - Keep the response concise.
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
