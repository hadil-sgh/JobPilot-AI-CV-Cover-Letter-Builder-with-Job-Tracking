package com.jobpilot.profile.cv;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Repairs common shape mistakes small LLMs make in prompt-A output, so one malformed field does
 * not throw away a whole (slow) answer:
 * <ul>
 *   <li>a section entry given as a plain string → object with that string as its main field
 *       ({@code "projects": ["JobTrack"]} → {@code [{"name": "JobTrack"}]});</li>
 *   <li>a single object/string where a list is expected → one-element list;</li>
 *   <li>a string list field given as one string → one-element list.</li>
 * </ul>
 * Values are not interpreted here; {@link CvDraftMapper} still sanitises everything.
 */
public final class CvJsonNormalizer {

    /** Section name → field that receives a bare string entry. */
    private static final Map<String, String> SECTION_MAIN_FIELD = Map.of(
            "experience", "title",
            "education", "degree",
            "projects", "name",
            "skills", "items",
            "languages", "name",
            "certifications", "name");

    private static final String[] STRING_LISTS = {"bullets", "tags", "items"};

    private CvJsonNormalizer() {
    }

    public static JsonNode normalize(JsonNode root) {
        if (!(root instanceof ObjectNode obj)) {
            return root;
        }
        SECTION_MAIN_FIELD.forEach((section, mainField) -> {
            JsonNode value = obj.get(section);
            if (value == null || value.isNull()) {
                return;
            }
            ArrayNode list = asArray(value);
            ArrayNode fixed = JsonNodeFactory.instance.arrayNode();
            for (JsonNode entry : list) {
                if (entry.isTextual()) {
                    ObjectNode o = JsonNodeFactory.instance.objectNode();
                    if ("items".equals(mainField)) {
                        o.set("items", JsonNodeFactory.instance.arrayNode().add(entry.asText()));
                    } else {
                        o.put(mainField, entry.asText());
                    }
                    fixed.add(o);
                } else if (entry instanceof ObjectNode o) {
                    for (String field : STRING_LISTS) {
                        JsonNode v = o.get(field);
                        if (v != null && !v.isNull() && !v.isArray()) {
                            o.set(field, v.isTextual() ? JsonNodeFactory.instance.arrayNode().add(v.asText())
                                    : JsonNodeFactory.instance.arrayNode());
                        }
                    }
                    fixed.add(o);
                }
            }
            obj.set(section, fixed);
        });
        JsonNode contact = obj.get("contact");
        if (contact instanceof ObjectNode c && c.has("links") && !c.get("links").isArray()) {
            c.set("links", asArray(c.get("links")));
        }
        if (contact != null && !contact.isObject()) {
            obj.remove("contact");
        }
        return obj;
    }

    private static ArrayNode asArray(JsonNode value) {
        if (value.isArray()) {
            return (ArrayNode) value;
        }
        return JsonNodeFactory.instance.arrayNode().add(value);
    }
}
