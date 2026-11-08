package grocery.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



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
            .put("max_tokens", 1500)
            .set("messages", mapper.createArrayNode().add(
                mapper.createObjectNode()
                    .put("role", "user")
                    .put("content", prompt)
            ));

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

}
 