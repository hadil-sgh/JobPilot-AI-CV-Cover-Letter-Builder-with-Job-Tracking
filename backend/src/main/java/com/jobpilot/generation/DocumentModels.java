package com.jobpilot.generation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;
import com.jobpilot.template.ClasspathTemplateRegistry;
import com.jobpilot.template.LatexEscaper;

/**
 * Builds the template model (plain maps/lists, no nulls) from stored content: localised headings
 * and dates, section order, contact lines. Text stays plain — the template's output format escapes
 * it; only URLs are pre-escaped (as markup) because {@code \href} needs a different escaping.
 */
public final class DocumentModels {

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            "en", Map.of("summary", "Summary", "experience", "Experience", "projects", "Projects",
                    "education", "Education", "skills", "Skills", "certifications", "Certifications",
                    "languages", "Languages", "present", "Present", "subject", "Application for the position of "),
            "fr", Map.of("summary", "Profil", "experience", "Expérience professionnelle", "projects", "Projets",
                    "education", "Formation", "skills", "Compétences", "certifications", "Certifications",
                    "languages", "Langues", "present", "Aujourd'hui", "subject", "Objet : candidature au poste de "));

    private DocumentModels() {
    }

    public static String lang(String language) {
        return "fr".equals(language) ? "fr" : "en";
    }

    public static String label(String language, String key) {
        return LABELS.get(lang(language)).get(key);
    }

    /** CV model; {@code sectionOrder} comes from the resolved template options. */
    public static Map<String, Object> cv(CvContent c, List<String> sectionOrder) {
        String lang = lang(c.language());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("header", header(c.header()));

        List<Map<String, Object>> sections = new ArrayList<>();
        for (String key : sectionOrder) {
            Map<String, Object> s = section(c, key, lang);
            if (s != null) {
                sections.add(s);
            }
        }
        doc.put("sections", sections);

        String name = nz(c.header().fullName());
        List<String> skills = nullSafe(c.skills()).stream().flatMap(g -> nullSafe(g.items()).stream()).limit(15).toList();
        doc.put("meta", Map.of("title", name.isEmpty() ? "CV" : name + " - CV", "author", name,
                "subject", nz(c.header().headline()), "keywords", String.join(", ", skills), "lang", lang));
        return doc;
    }

    /** Headings of the sections that will actually be printed, in order (for the ATS check). */
    @SuppressWarnings("unchecked")
    public static List<String> headings(Map<String, Object> cvModel) {
        return ((List<Map<String, Object>>) cvModel.get("sections")).stream().map(s -> (String) s.get("title")).toList();
    }

    private static Map<String, Object> section(CvContent c, String key, String lang) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("key", key);
        s.put("title", label(lang, key));
        switch (key) {
            case "summary" -> {
                if (nz(c.summary()).isEmpty()) {
                    return null;
                }
                s.put("text", c.summary());
            }
            case "skills" -> {
                List<Map<String, Object>> groups = nullSafe(c.skills()).stream()
                        .filter(g -> !nullSafe(g.items()).isEmpty())
                        .map(g -> Map.<String, Object>of("group", nz(g.group()), "items", g.items()))
                        .toList();
                if (groups.isEmpty()) {
                    return null;
                }
                s.put("groups", groups);
            }
            case "languages" -> {
                List<ProfileLanguage> langs = nullSafe(c.languages());
                if (langs.isEmpty()) {
                    return null;
                }
                s.put("text", langs.stream().map(l -> l.level() == null || l.level().isBlank() ? l.name()
                        : l.name() + " (" + l.level() + ")").collect(Collectors.joining(" · ")));
            }
            case "experience", "education", "projects", "certifications" -> {
                List<CvContent.Entry> entries = switch (key) {
                    case "experience" -> c.experience();
                    case "education" -> c.education();
                    case "projects" -> c.projects();
                    default -> c.certifications();
                };
                if (nullSafe(entries).isEmpty()) {
                    return null;
                }
                s.put("entries", nullSafe(entries).stream().map(e -> entry(e, key, lang)).toList());
            }
            default -> {
                return null;
            }
        }
        return s;
    }

    private static Map<String, Object> entry(CvContent.Entry e, String key, String lang) {
        String heading = nz(e.title());
        if (!nz(e.organization()).isEmpty()) {
            heading = heading.isEmpty() ? e.organization() : heading + ", " + e.organization();
        }
        String detail = "";
        if ("projects".equals(key)) {
            detail = nz(e.description());
            if (!nullSafe(e.tags()).isEmpty()) {
                detail = (detail.isEmpty() ? "" : detail + " · ") + String.join(", ", e.tags());
            }
        }
        String dates = "certifications".equals(key) ? month(e.start(), lang) : range(e.start(), e.end(), lang);
        return Map.of("heading", heading, "dates", dates, "detail", detail,
                "bullets", "certifications".equals(key) ? List.of() : nullSafe(e.bullets()));
    }

    /** Letter model; the header (name, contact) comes from the profile. */
    public static Map<String, Object> letter(LetterContent l, CvContent.Header header, LocalDate today) {
        String lang = lang(l.language());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("header", header(header));
        doc.put("company", nz(l.company()));
        doc.put("date", today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale(lang))));
        doc.put("subject", nz(l.roleTitle()).isEmpty() ? "" : label(lang, "subject") + l.roleTitle());
        doc.put("greeting", nz(l.greeting()));
        doc.put("paragraphs", nullSafe(l.paragraphs()));
        doc.put("closing", nz(l.closing()));
        String name = nz(header.fullName());
        doc.put("meta", Map.of("title", (name + " - " + ("fr".equals(lang) ? "Lettre de motivation" : "Cover letter")).strip(),
                "author", name, "subject", nz(l.roleTitle()), "lang", lang));
        return doc;
    }

    private static Map<String, Object> header(CvContent.Header h) {
        List<String> contact = new ArrayList<>();
        for (String s : new String[] {h.email(), h.phone(), h.location()}) {
            if (!nz(s).isEmpty()) {
                contact.add(s);
            }
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (ProfileLink link : nullSafe(h.links())) {
            String url = LatexEscaper.escapeUrl(link.url());
            if (url != null) {
                // Visible URL text (PROJECT.md 3.9: links stay readable for ATS), escaped by the template.
                String visible = link.url().replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "");
                links.add(Map.of("url", ClasspathTemplateRegistry.markup(url), "text", visible));
            }
        }
        return Map.of("fullName", nz(h.fullName()), "headline", nz(h.headline()), "contact", contact, "links", links);
    }

    static String range(LocalDate start, LocalDate end, String lang) {
        if (start == null && end == null) {
            return "";
        }
        String from = start == null ? "" : month(start, lang);
        String to = end == null ? label(lang, "present") : month(end, lang);
        return from.isEmpty() ? to : from + " – " + to;
    }

    static String month(LocalDate d, String lang) {
        if (d == null) {
            return "";
        }
        String s = d.format(DateTimeFormatter.ofPattern("MMM yyyy", locale(lang)));
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Locale locale(String lang) {
        return "fr".equals(lang) ? Locale.FRENCH : Locale.ENGLISH;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
