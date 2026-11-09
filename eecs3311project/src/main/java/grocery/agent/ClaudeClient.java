package grocery.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;



public class ClaudeClient implements LLMClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public ClaudeClient(){
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY is not configured");
        }

        String configuredModel = System.getenv("ANTHROPIC_MODEL");

        this.model = configuredModel == null || configuredModel.isBlank() ? "claude-haiku-5-5" : configuredModel;

        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

        this.mapper = new ObjectMapper();
    }

    @Override
    public String ask(String prompt) throws Exception {


        JsonNode payload = mapper.createObjectNode()
            .put("model", model)
            .put("max_tokens", 3000)
            .set("messages", mapper.createArrayNode().add(
                mapper.createObjectNode()
                    .put("role", "user")
                    .put("content", prompt)
            ));


        JsonNode webSearchTool = mapper.createObjectNode()
            .put("type", "web_search_20250305")
            .put("name", "web_search")
            .put("max_uses", 3);


        ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
            .set("tools", mapper.createArrayNode()
                .add(webSearchTool));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(payload)
            ))
            .build();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Claude API request failed (HTTP " +
                response.statusCode() + ")"
            );
        }

        JsonNode result = mapper.readTree(response.body());

        if (result.path("stop_reason")
                .asText().equals("max_tokens")) {
            throw new IllegalStateException(
                "Claude response was truncated"
            );
        }

        JsonNode content = result.path("content");

        if (!content.isArray()) {
            throw new IllegalStateException(
                "Invalid Claude API response"
            );
        }

        StringBuilder answer = new StringBuilder();

        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                answer.append(block.path("text").asText());
            }
        }

        if (answer.isEmpty()) {
            throw new IllegalStateException(
                "Claude returned no text"
            );
        }

        

        return answer.toString();
    }


    public JsonNode searchWeb(String prompt) throws Exception {

        // 1. Create request JSON
        ObjectNode payload = mapper.createObjectNode();

        payload.put("model", model);
        payload.put("max_tokens", 3000);

        payload.set("messages",
            mapper.createArrayNode().add(
                mapper.createObjectNode()
                    .put("role", "user")
                    .put("content", prompt)
            )
        );

        // 2. Enable Claude web search
        ObjectNode webSearchTool = mapper.createObjectNode();

        webSearchTool.put("type", "web_search_20250305");
        webSearchTool.put("name", "web_search");
        webSearchTool.put("max_uses", 3);

        payload.set(
            "tools",
            mapper.createArrayNode().add(webSearchTool)
        );

        // 3. Send to Anthropic
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(payload)
            ))
            .build();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Claude web search failed. HTTP " +
                response.statusCode() + ": " +
                response.body()
            );
        }

        // 4. Return complete response, including citations
        JsonNode result = mapper.readTree(response.body());

        String stopReason = result.path("stop_reason").asText();

        if ("max_tokens".equals(stopReason)) {
            throw new IllegalStateException(
                "Claude web search response was truncated"
            );
        }

        if ("pause_turn".equals(stopReason)) {
            throw new IllegalStateException(
                "Claude web search paused before completion"
            );
        }

        return result;
    }

}
 