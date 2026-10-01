package com.jobpilot.rag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileItem;

/**
 * Splits a profile into retrieval chunks (PROJECT.md 3.3): one per experience / project /
 * education / certification, one per skill group, one for headline + summary, one for spoken
 * languages. Each chunk is self-contained text so it reads well as evidence for the LLM.
 */
@Component
public class ProfileChunker {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public List<Chunk> chunk(Profile profile) {
        List<Chunk> chunks = new ArrayList<>();

        StringBuilder summary = new StringBuilder();
        append(summary, profile.getHeadline());
        append(summary, profile.getSummary());
        if (!summary.isEmpty()) {
            chunks.add(new Chunk(null, "Profile summary: " + summary.toString().strip(), meta("SUMMARY", null)));
        }

        if (!profile.getLanguages().isEmpty()) {
            String langs = profile.getLanguages().stream()
                    .map(l -> l.level() == null ? l.name() : l.name() + " (" + l.level() + ")")
                    .collect(Collectors.joining(", "));
            chunks.add(new Chunk(null, "Spoken languages: " + langs, meta("LANGUAGES", null)));
        }

        profile.getItems().stream()
                .sorted(Comparator.comparing(ProfileItem::getType).thenComparingInt(ProfileItem::getSortOrder))
                .map(this::chunk)
                .filter(c -> c != null)
                .forEach(chunks::add);
        return chunks;
    }

    private Chunk chunk(ProfileItem i) {
        String text = switch (i.getType()) {
            case EXPERIENCE -> heading("Experience", i.getTitle(), "at", i.getOrganization(), dates(i)) + body(i);
            case EDUCATION -> heading("Education", i.getTitle(), "at", i.getOrganization(), dates(i)) + body(i);
            case PROJECT -> heading("Project", i.getTitle(), null, null, null)
                    + (i.getTags().isEmpty() ? "" : "\nTechnologies: " + String.join(", ", i.getTags())) + body(i);
            case CERTIFICATION -> heading("Certification", i.getTitle(), "by", i.getOrganization(),
                    i.getStartDate() == null ? null : MONTH.format(i.getStartDate()));
            case SKILL -> i.getTags().isEmpty() ? null
                    : "Skills (" + (i.getTitle() == null ? "general" : i.getTitle()) + "): " + String.join(", ", i.getTags());
        };
        if (text == null || text.isBlank()) {
            return null;
        }
        return new Chunk(i.getId(), text.strip(), meta(i.getType().name(), i));
    }

    private static String heading(String kind, String title, String joiner, String org, String when) {
        StringBuilder sb = new StringBuilder(kind).append(": ").append(title == null ? "(untitled)" : title);
        if (org != null) {
            sb.append(' ').append(joiner).append(' ').append(org);
        }
        if (when != null) {
            sb.append(" (").append(when).append(')');
        }
        return sb.toString();
    }

    private static String body(ProfileItem i) {
        StringBuilder sb = new StringBuilder();
        if (i.getDescription() != null) {
            sb.append('\n').append(i.getDescription());
        }
        i.getBullets().forEach(b -> sb.append("\n- ").append(b));
        return sb.toString();
    }

    private static String dates(ProfileItem i) {
        if (i.getStartDate() == null && i.getEndDate() == null) {
            return null;
        }
        return fmt(i.getStartDate(), "?") + " to " + fmt(i.getEndDate(), "present");
    }

    private static String fmt(LocalDate d, String fallback) {
        return d == null ? fallback : MONTH.format(d);
    }

    private static Map<String, Object> meta(String type, ProfileItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (i != null) {
            m.put("item_id", i.getId() == null ? null : i.getId().toString());
            m.put("org", i.getOrganization());
            m.put("start", i.getStartDate() == null ? null : i.getStartDate().toString());
            m.put("end", i.getEndDate() == null ? null : i.getEndDate().toString());
        }
        return m;
    }

    private static void append(StringBuilder sb, String s) {
        if (s != null && !s.isBlank()) {
            sb.append(s.strip()).append(s.strip().endsWith(".") ? " " : ". ");
        }
    }
}
