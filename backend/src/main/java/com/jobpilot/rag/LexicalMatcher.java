package com.jobpilot.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keyword side of hybrid retrieval: which meaningful terms of a requirement appear in a chunk.
 * nomic-embed-text similarities sit in a narrow band (~0.45–0.75) and short skill lists score low,
 * so exact term overlap ("Angular", "Docker", "SQL") is a strong, explainable extra signal.
 * Generic job-ad words ("experience", "knowledge", "years", "fluent"...) are ignored.
 */
public final class LexicalMatcher {

    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            // English
            "a", "an", "and", "or", "the", "of", "in", "on", "at", "to", "for", "with", "by", "as", "is", "are", "be",
            "your", "you", "our", "we", "their", "this", "that", "other", "another", "any", "such", "etc", "eg",
            "into", "from", "using", "use", "used", "equivalent", "similar", "related", "including",
            // French
            "de", "des", "du", "la", "le", "les", "un", "une", "et", "ou", "en", "au", "aux", "pour", "avec", "sur",
            "dans", "par", "vous", "votre", "vos", "nous", "notre", "nos", "autre", "equivalent", "similaire",
            // Generic job-ad vocabulary (appears everywhere, proves nothing)
            "experience", "experienced", "knowledge", "skill", "skills", "ability", "able", "good", "strong",
            "solid", "excellent", "great", "year", "years", "yr", "yrs", "minimum", "least", "plus", "working",
            "work", "fluent", "fluency", "proficient", "proficiency", "familiar", "familiarity", "understanding",
            "background", "hands", "count", "required", "requirement", "preferred", "nice", "have", "must",
            "bonne", "bonnes", "connaissance", "connaissances", "maitrise", "annee", "annees", "ans", "minimum",
            "competence", "competences", "souhaite", "souhaitee", "requis", "idealement"));

    private LexicalMatcher() {
    }

    /** Normalised, de-duplicated meaningful terms of a text. */
    public static List<String> terms(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : tokens(text)) {
            if (!STOPWORDS.contains(token) && (token.length() > 1 || token.equals("c") || token.equals("r"))) {
                out.add(token);
            }
        }
        return new ArrayList<>(out);
    }

    /** Requirement terms that also occur in the chunk. */
    public static List<String> matched(List<String> requirementTerms, String chunkText) {
        Set<String> chunk = new LinkedHashSet<>(tokens(chunkText));
        return requirementTerms.stream().filter(chunk::contains).toList();
    }

    static List<String> tokens(String text) {
        if (text == null) {
            return List.of();
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        // Keep tech spellings together: c++, c#, node.js, ci/cd, .net
        for (String raw : s.split("[^a-z0-9+#./]+")) {
            String t = raw.replaceAll("^[./]+|[./]+$", "");
            if (t.isEmpty()) {
                continue;
            }
            out.add(stem(t));
        }
        return out;
    }

    /** Minimal plural folding (databases → database, frameworks → framework). */
    static String stem(String t) {
        if (t.length() > 4 && t.endsWith("ies")) {
            return t.substring(0, t.length() - 3) + "y";
        }
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss") && !t.endsWith("us") && !t.endsWith(".js")) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }
}
