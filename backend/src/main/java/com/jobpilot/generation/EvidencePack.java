package com.jobpilot.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobpilot.profile.ItemType;

/**
 * The profile facts the LLM may use, each with a short reference (E1 experience, P1 project,
 * D1 education/degree, C1 certification, S1 skill group). Short refs instead of UUIDs: small
 * models copy "E2" reliably, not 36-char ids.
 */
public record EvidencePack(List<Item> items, List<Requirement> requirements) {

    public record Item(String ref, UUID itemId, ItemType type, String text, double relevance,
                       List<String> supports) {
    }

    /** One job requirement with its best evidence (hybrid score; -1 when nothing matched). */
    public record Requirement(String text, boolean mustHave, double bestScore, List<String> refs) {
    }

    public Map<String, Item> byRef() {
        return items.stream().collect(Collectors.toMap(Item::ref, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    public List<Item> ofType(ItemType type) {
        return items.stream().filter(i -> i.type() == type).toList();
    }

    /** Evidence block for prompts C/D. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            sb.append('[').append(i.ref()).append("] ").append(i.text().strip());
            if (!i.supports().isEmpty()) {
                sb.append("\n(relevant to: ").append(String.join("; ", i.supports())).append(')');
            }
            sb.append("\n\n");
        }
        return sb.toString().strip();
    }

    public static String prefix(ItemType type) {
        return switch (type) {
            case EXPERIENCE -> "E";
            case PROJECT -> "P";
            case EDUCATION -> "D";
            case CERTIFICATION -> "C";
            case SKILL -> "S";
        };
    }
}
