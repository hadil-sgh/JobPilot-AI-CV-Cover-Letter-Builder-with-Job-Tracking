package com.jobpilot.common.llm;

import java.io.IOException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import com.jobpilot.common.error.ApiException;

/**
 * One place for "ask the local LLM for JSON": JSON mode, temperature 0, tolerant parsing, one
 * retry on unparseable output, and user-safe error messages when Ollama is down or fails.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatClient chat;
    private final ObjectMapper json;

    public LlmClient(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chat = builder.build();
        this.json = LlmJson.lenientMapper(objectMapper);
    }

    /**
     * @param repair optional structural fix-up applied to the parsed tree before binding
     * @param what   short noun for error messages, e.g. "this CV"
     */
    public <T> T callJson(String system, String user, Class<T> type, Function<JsonNode, JsonNode> repair, String what) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = call(system, user, what);
            try {
                JsonNode tree = json.readTree(LlmJson.extractJsonObject(raw));
                return json.treeToValue(repair.apply(tree), type);
            } catch (IOException | IllegalArgumentException e) {
                log.warn("LLM JSON for {} (attempt {}) was invalid: {}", type.getSimpleName(), attempt, e.getMessage());
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model could not read " + what + ". Please try again.");
    }

    private String call(String system, String user, String what) {
        try {
            return chat.prompt()
                    .system(system)
                    .user(user)
                    .options(OllamaOptions.builder().format("json").temperature(0.0).build())
                    .call()
                    .content();
        } catch (ResourceAccessException e) {
            log.error("Ollama unreachable", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is not reachable. Is Ollama running?");
        } catch (RuntimeException e) {
            log.error("Ollama call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model failed to process " + what + ". Try again later.");
        }
    }
}
