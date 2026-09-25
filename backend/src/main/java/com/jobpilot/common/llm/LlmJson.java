package com.jobpilot.common.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/** Helpers for turning (untrusted, sometimes sloppy) LLM output into Java objects. */
public final class LlmJson {

    private LlmJson() {
    }

    /**
     * A copy of the app mapper that tolerates small-model mistakes: unknown fields, a single value
     * where a list is expected, and objects/arrays where text is expected (flattened).
     */
    public static ObjectMapper lenientMapper(ObjectMapper base) {
        return base.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .registerModule(new SimpleModule().addDeserializer(String.class, new LenientStringDeserializer()));
    }

    /** Extracts the outermost JSON object, tolerating code fences or chatter around it. */
    public static String extractJsonObject(String raw) {
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

    /** Removes every opening/closing form of a delimiter tag so data cannot escape its block. */
    public static String stripDelimiter(String text, String tag) {
        return text.replaceAll("(?i)<\\s*/?\\s*" + java.util.regex.Pattern.quote(tag) + "\\s*>", "");
    }
}
