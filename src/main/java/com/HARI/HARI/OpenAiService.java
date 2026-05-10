package com.HARI.HARI;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAiService {

    private static final URI RESPONSES_API_URL = URI.create("https://api.openai.com/v1/responses");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiService(
            @Value("${hari.openai.api-key:}") String apiKey,
            @Value("${hari.openai.model:gpt-4.1-mini}") String model) {
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String ask(String userMessage, String context) {
        if (!isConfigured()) {
                    return """
                    Hari's real AI model is not connected yet.
                    Set OPENAI_API_KEY, restart Hari, then ask again.
                    """;
        }

        try {
            String requestBody = objectMapper.writeValueAsString(createRequestBody(userMessage, context));
            HttpRequest request = HttpRequest.newBuilder(RESPONSES_API_URL)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "The AI model returned an error. Status: " + response.statusCode() + "\n" + extractError(response.body());
            }

            return extractText(response.body()).orElse("The AI model replied, but Hari could not read the text response.");
        } catch (IOException error) {
            return "Hari could not call the AI model: " + error.getMessage();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "Hari's AI request was interrupted.";
        }
    }

    private Map<String, Object> createRequestBody(String userMessage, String context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", """
                You are Hari, a helpful personal agentic AI built by the user.
                Be clear, friendly, and practical. Help the user learn and build.
                If the user asks to save notes, remember things, manage tasks, calculate, or show memory,
                tell them Hari has local commands for those actions.
                """);
        body.put("input", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "input_text",
                                        "text", context + "\n\nUser message: " + userMessage)))));
        return body;
    }

    private Optional<String> extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return Optional.of(outputText.asText());
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode itemText = contentItem.path("text");
                    if (itemText.isTextual()) {
                        text.append(itemText.asText());
                    }
                }
            }
            if (!text.isEmpty()) {
                return Optional.of(text.toString());
            }
        }

        return Optional.empty();
    }

    private String extractError(String responseBody) {
        try {
            JsonNode message = objectMapper.readTree(responseBody).path("error").path("message");
            if (message.isTextual()) {
                return message.asText();
            }
        } catch (IOException ignored) {
            return responseBody;
        }
        return responseBody;
    }
}
