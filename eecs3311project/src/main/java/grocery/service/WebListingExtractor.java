
package grocery.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grocery.agent.LLMClient;
import grocery.model.WebProductListing;

public class WebListingExtractor {

    private final LLMClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebListingExtractor(LLMClient llm) {
        this.llm = llm;
    }

    public List<WebProductListing> extract(
            WebSearchResult searchResult,
            String category) throws Exception {

        if (!searchResult.searchPerformed()
                || searchResult.sourceUrls().isEmpty()) {
            return List.of();
        }

        String prompt = """
            Extract grocery listings from the research below.

            Return ONLY valid JSON:

            {
              "products": [
                {
                  "name": "2%% Milk",
                  "brand": "Brand",
                  "category": "%s",
                  "store": "Store",
                  "price": 6.49,
                  "size": 4,
                  "unit": "L",
                  "sourceUrl": "https://example.com/product"
                }
              ]
            }

            Rules:
            - Return at most 3 distinct product listings per requested item.
            - Prefer products with clear price, size, retailer, and source information.
            - Avoid duplicate products from the same store.
            - Do not invent missing product details.

            IMPORTANT OUTPUT RULES:
            - Return a raw JSON object only.
            - The first character must be {
            - The last character must be }
            - Do not wrap JSON in ```json or ```
            - Do not include explanations before or after JSON.

            Allowed URLs:
            %s

            Research:
            %s
            """.formatted(
                category,
                String.join("\n", searchResult.sourceUrls()),
                searchResult.summary()
            );

            String response = llm.ask(prompt);

            // Remove surrounding Markdown code fences
            String cleanedResponse = response.trim();

            if (cleanedResponse.startsWith("```")) {

                // Remove the opening fence, including optional "json"
                int firstNewline = cleanedResponse.indexOf('\n');

                if (firstNewline == -1) {
                    throw new IllegalStateException(
                        "AI returned an invalid JSON code block"
                    );
                }

                cleanedResponse = cleanedResponse
                    .substring(firstNewline + 1)
                    .trim();

                // Remove closing fence
                if (cleanedResponse.endsWith("```")) {
                    cleanedResponse = cleanedResponse
                        .substring(0, cleanedResponse.length() - 3)
                        .trim();
                }
            }

            // Parse the cleaned JSON
            JsonNode root = mapper.readTree(cleanedResponse);

            JsonNode products = root.path("products");


        if (!products.isArray()) {
            throw new IllegalStateException(
                "Invalid product extraction response"
            );
        }

        Set<String> allowedUrls =
            new HashSet<>(searchResult.sourceUrls());

        List<WebProductListing> listings =
            new ArrayList<>();

        for (JsonNode node : products) {

            String url = node.path("sourceUrl").asText("");

            // Reject URLs not present in search evidence
            if (!allowedUrls.contains(url)) {
                continue;
            }

            JsonNode price = node.path("price");
            JsonNode size = node.path("size");

            if (!price.isNumber() || !size.isNumber()) {
                continue;
            }

            try {
                listings.add(new WebProductListing(
                    node.path("name").asText(""),
                    node.path("brand").asText("Unknown"),
                    category,
                    node.path("store").asText(""),
                    price.asDouble(),
                    size.asDouble(),
                    node.path("unit").asText(""),
                    url
                ));
            } catch (IllegalArgumentException ex) {
                // Skip invalid listings.
            }
        }

        return listings;
    }
}

/*
How the extraction works
WebListingExtractor sends Claude a second request, this time asking it to transform the web research into JSON.

The returned product is accepted as a candidate only when:
- Its required fields are valid.
- Its price and size are positive numbers.
- Its source URL is present in the web search results.
The candidate still requires price confirmation. 
The source URL could support the product but not the specific price, 
or the retailer could show a different price for a different store location.
*/
