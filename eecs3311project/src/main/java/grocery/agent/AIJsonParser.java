package grocery.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AIJsonParser {
    public static JsonNode parseAIJson(
        ObjectMapper mapper,
        String response) throws Exception {

            String cleaned = response.trim();

            if (cleaned.startsWith("```")) {

                int newline = cleaned.indexOf('\n');

                if (newline < 0) {
                    throw new IllegalArgumentException(
                        "Invalid AI JSON response"
                    );
                }

                cleaned = cleaned.substring(newline + 1).trim();

                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(
                        0, cleaned.length() - 3
                    ).trim();
                }
            }

            return mapper.readTree(cleaned);
        }

}
