package com.jobpilot.profile.cv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.jobpilot.common.error.ApiException;

/**
 * Prompt A (PROJECT.md 3.5) on the local Llama via Ollama, in JSON mode at temperature 0.
 * The CV text is wrapped in delimiter tags and treated as data. One retry on unparseable output.
 */
@Service
public class OllamaCvStructurer implements CvStructurer {

    private static final Logger log = LoggerFactory.getLogger(OllamaCvStructurer.class);

    private final ChatClient chat;
    private final String systemPrompt;
    private final ObjectMapper json;

    public OllamaCvStructurer(ChatClient.Builder builder, ObjectMapper objectMapper,
                              @Value("classpath:prompts/cv-structuring-system.txt") Resource prompt) throws IOException {
        this.chat = builder.build();
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
        this.json = lenientMapper(objectMapper);
    }

    @Override
    public CvDraft structure(String cvText) {
        String user = "<cv_text>\n" + escapeDelimiters(cvText) + "\n</cv_text>";
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = call(user);
            try {
                return json.treeToValue(CvJsonNormalizer.normalize(json.readTree(extractJsonObject(raw))), CvDraft.class);
            } catch (IOException | IllegalArgumentException e) {
                log.warn("CV structuring attempt {} returned invalid JSON: {}", attempt, e.getMessage());
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,
                "The AI model could not read this CV. Try again, or fill in your profile manually.");
    }

    private String call(String user) {
        try {
            return chat.prompt()
                    .system(systemPrompt)
                    .user(user)
                    .options(OllamaOptions.builder().format("json").temperature(0.0).build())
                    .call()
                    .content();
        } catch (ResourceAccessException e) {
            log.error("Ollama unreachable", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is not reachable. Is Ollama running?");
        } catch (RuntimeException e) {
            log.error("Ollama call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model failed to process the CV. Try again later.");
        }
    }

    /** Tolerant parsing for small-model output: see {@link CvJsonNormalizer} and {@link LenientStringDeserializer}. */
    static ObjectMapper lenientMapper(ObjectMapper base) {
        return base.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .registerModule(new SimpleModule().addDeserializer(String.class, new LenientStringDeserializer()));
    }

    /** The document must not be able to close our delimiter and inject text outside it. */
    static String escapeDelimiters(String text) {
        return text.replaceAll("(?i)</?\\s*cv_text\\s*>", "");
    }

    /** Tolerates code fences or chatter around the object even in JSON mode. */
    static String extractJsonObject(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty response");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in response");
        }
        return raw.substring(start, end + 1);
    }
}
