package com.jobpilot.job;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jobpilot.common.text.LanguageGuess;
import com.jobpilot.common.text.TextClean;

/** Sanitises prompt-B output: trims/caps every field, normalises language to "en"/"fr". */
final class JobAnalysisCleaner {

    private static final Set<String> SENIORITY = Set.of("intern", "junior", "mid", "senior", "lead", "principal");
    private JobAnalysisCleaner() {
    }

    static JobAnalysis clean(JobAnalysis a, String jdText) {
        return new JobAnalysis(
                TextClean.clean(a.title(), 200),
                TextClean.clean(a.company(), 200),
                language(a.language(), jdText),
                seniority(a.seniority()),
                TextClean.cleanList(a.requirements(), 25, 300),
                TextClean.cleanList(a.niceToHave(), 20, 300),
                TextClean.cleanList(a.keywords(), 40, 60),
                TextClean.cleanList(a.responsibilities(), 20, 300),
                TextClean.clean(a.tone(), 60));
    }

    /**
     * The offer's language drives the language of the generated CV/letter, so the model's answer
     * is not trusted when the text clearly says otherwise (llama3 answered "fr" for an English
     * offer asking for "fluent French"). A clear word-count majority (2:1) wins; otherwise the
     * model's normalised answer ("English", "fr-FR", "Français"...) is used; default "en".
     */
    static String language(String raw, String jdText) {
        String clear = LanguageGuess.clear(jdText, 5);
        if (clear != null) {
            return clear;
        }
        String s = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return s.startsWith("fr") ? "fr" : "en";
    }

    static String seniority(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip().toLowerCase(Locale.ROOT);
        for (String level : List.of("intern", "junior", "senior", "lead", "principal")) {
            if (s.contains(level) || (level.equals("intern") && s.contains("stag"))) {
                return level;
            }
        }
        if (s.contains("mid") || s.contains("confirm") || s.contains("intermediate")) {
            return "mid";
        }
        return SENIORITY.contains(s) ? s : null;
    }
}
