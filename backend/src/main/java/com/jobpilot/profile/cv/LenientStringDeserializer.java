package com.jobpilot.profile.cv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * For LLM output only: accepts any JSON value where a string is expected. Objects/arrays are
 * flattened to their text leaves joined with ", " (e.g. {"city":"Tunis","country":"TN"} →
 * "Tunis, TN"), so one oddly-shaped field does not discard the whole answer.
 */
final class LenientStringDeserializer extends StdDeserializer<String> {

    LenientStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken().isScalarValue()) {
            return p.getValueAsString();
        }
        JsonNode node = p.readValueAsTree();
        List<String> parts = new ArrayList<>();
        collect(node, parts);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void collect(JsonNode node, List<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String text = node.asText().strip();
            if (!text.isEmpty()) {
                out.add(text);
            }
            return;
        }
        node.forEach(child -> collect(child, out));
    }
}
