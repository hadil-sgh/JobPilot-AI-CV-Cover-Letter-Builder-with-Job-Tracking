package com.jobpilot.common.llm;

import java.io.IOException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
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

    static final String OUT_OF_MEMORY = "requires more system memory";

    private final ChatClient chat;
    private final ObjectMapper json;
    /** Smaller model used when the main one cannot be loaded for lack of RAM (empty = no fallback). */
    private final String fallbackModel;
    /** Model for simple tasks (job analysis, match judgment); empty = the main model. */
    private final String fastModel;

    public LlmClient(ChatClient.Builder builder, ObjectMapper objectMapper,
                     @Value("${jobpilot.llm.fallback-model:}") String fallbackModel,
                     @Value("${jobpilot.llm.fast-model:}") String fastModel) {
        this.chat = builder.build();
        this.json = LlmJson.lenientMapper(objectMapper);
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.strip();
        this.fastModel = fastModel == null ? "" : fastModel.strip();
    }

    /** Same as {@link #callJson(String, String, Class, Function, String)} on the fast model (simple extraction/judgment). */
    public <T> T callJsonFast(String system, String user, Class<T> type, Function<JsonNode, JsonNode> repair, String what) {
        return callJson(system, user, type, repair, what, 0.0, fastModel.isEmpty() ? null : fastModel);
    }

    /**
     * @param repair optional structural fix-up applied to the parsed tree before binding
     * @param what   short noun for error messages, e.g. "this CV"
     */
    public <T> T callJson(String system, String user, Class<T> type, Function<JsonNode, JsonNode> repair, String what) {
        return callJson(system, user, type, repair, what, 0.0);
    }

    /** Same, with a sampling temperature (e.g. higher to get a different version on "regenerate"). */
    public <T> T callJson(String system, String user, Class<T> type, Function<JsonNode, JsonNode> repair, String what,
                          double temperature) {
        return callJson(system, user, type, repair, what, temperature, null);
    }

    private <T> T callJson(String system, String user, Class<T> type, Function<JsonNode, JsonNode> repair, String what,
                           double temperature, String model) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = call(system, user, what, temperature, model);
            try {
                JsonNode tree = json.readTree(LlmJson.extractJsonObject(raw));
                return json.treeToValue(repair.apply(tree), type);
            } catch (IOException | IllegalArgumentException e) {
                log.warn("LLM JSON for {} (attempt {}) was invalid: {}", type.getSimpleName(), attempt, e.getMessage());
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model could not read " + what + ". Please try again.");
    }

    static boolean causeMessageContains(Throwable e, String text) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private String call(String system, String user, String what, double temperature, String model) {
        try {
            return callModel(system, user, temperature, model);
        } catch (RuntimeException e) {
            if (!causeMessageContains(e, OUT_OF_MEMORY) || fallbackModel.isEmpty() || fallbackModel.equals(model)) {
                throw mapError(e, what);
            }
            // Not enough free RAM for the main model right now: answer with the smaller one instead of failing.
            log.warn("Main model cannot be loaded (not enough free RAM); using fallback model {}", fallbackModel);
            try {
                return callModel(system, user, temperature, fallbackModel);
            } catch (RuntimeException again) {
                throw mapError(again, what);
            }
        }
    }

    /** @param model null = the configured default (spring.ai.ollama.chat.options.model) */
    private String callModel(String system, String user, double temperature, String model) {
        OllamaOptions.Builder options = OllamaOptions.builder().format("json").temperature(temperature);
        if (model != null) {
            options.model(model);
        }
        return chat.prompt().system(system).user(user).options(options.build()).call().content();
    }

    private static ApiException mapError(RuntimeException e, String what) {
        if (e instanceof ApiException api) {
            return api;
        }
        if (e instanceof ResourceAccessException) {
            if (causeMessageContains(e, "timed out")) {
                log.error("Ollama did not answer in time", e);
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI model took too long to answer (over 9 minutes). "
                        + "This usually means the computer is low on free memory: close some apps and try again, "
                        + "or use a smaller model (OLLAMA_CHAT_MODEL).");
            }
            log.error("Ollama unreachable", e);
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is not reachable. Is Ollama running?");
        }
        if (causeMessageContains(e, OUT_OF_MEMORY)) {
            log.error("Ollama cannot load the model: not enough free RAM ({})", e.getMessage());
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Your computer does not have enough free memory "
                    + "to load the AI model right now. Close some apps (browser tabs, Docker containers) and try again.");
        }
        log.error("Ollama call failed", e);
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI model failed to process " + what + ". Try again later.");
    }
}
