package com.jobpilot.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code templates/latex/<id>/manifest.json} (PROJECT.md 3.9). The manifest drives the UI: the
 * frontend builds the options form from {@link #options()}, so a new style needs no Angular change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateManifest(
        String id,
        String name,
        String version,
        String description,
        boolean atsSafe,
        String engine,
        int maxPages,
        List<String> languages,
        List<String> sections,
        LinkedHashMap<String, OptionSpec> options) {

    /** Option types: {@code enum} (values), {@code color} (#RRGGBB), {@code bool}, {@code list} (ordering of sections). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionSpec(String type, String label, List<String> values,
                             @JsonProperty("default") JsonNode defaultValue) {
    }

    public Map<String, OptionSpec> optionsOrEmpty() {
        return options == null ? Map.of() : options;
    }
}
