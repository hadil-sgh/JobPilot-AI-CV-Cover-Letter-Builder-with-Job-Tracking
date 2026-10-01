package com.jobpilot.generation.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.jobpilot.common.text.LanguageGuess;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.content.ReviewFlag;
import com.jobpilot.rag.LexicalMatcher;

/**
 * Deterministic anti-hallucination check (PROJECT.md 3.6) on everything the LLM wrote.
 * Employers, schools, titles and dates are copied from the profile by the assembler, so rules 1–2
 * of the doc hold by construction; here we check the free text:
 * <ol>
 *   <li>every number (percentages, counts, years) appears in the profile;</li>
 *   <li>technologies / keywords taken from the job ad appear in the profile;</li>
 *   <li>names after "at / for / with / chez / au sein de…" are the candidate's own organisations
 *       (or the target company);</li>
 *   <li>every skill in the skills section exists in the profile.</li>
 * </ol>
 */
@Component
public class FactValidator {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d])(\\d+(?:[.,]\\d+)?)\\s*(%|\\+)?");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}+#./]+");
    private static final Pattern ORG_AFTER = Pattern.compile(
            "\\b(?i:at|for|with|joined|chez|pour|avec|rejoint|rejoindre|au sein de|auprès de)\\s+"
                    + "(\\p{Lu}[\\p{L}\\d&.'-]*(?:\\s+\\p{Lu}[\\p{L}\\d&.'-]*){0,3})");

    public List<ReviewFlag> validateCv(CvContent cv, FactBase facts) {
        List<ReviewFlag> flags = new ArrayList<>();
        check("summary", cv.summary(), facts, flags);
        checkLanguage("summary", cv.summary(), facts, flags);
        for (CvContent.Entry e : nullSafe(cv.experience())) {
            for (String b : nullSafe(e.bullets())) {
                check("experience:" + e.ref(), b, facts, flags);
            }
        }
        for (CvContent.Entry e : nullSafe(cv.projects())) {
            check("projects:" + e.ref(), e.description(), facts, flags);
            for (String b : nullSafe(e.bullets())) {
                check("projects:" + e.ref(), b, facts, flags);
            }
        }
        for (CvContent.SkillGroup g : nullSafe(cv.skills())) {
            for (String skill : nullSafe(g.items())) {
                List<String> tokens = LexicalMatcher.terms(skill);
                if (!tokens.isEmpty() && !facts.profileTokens().containsAll(tokens)) {
                    flags.add(new ReviewFlag("skills", "Skill \"" + skill + "\" is not in your profile"));
                }
            }
        }
        return dedupe(flags);
    }

    public List<ReviewFlag> validateLetter(LetterContent letter, FactBase facts) {
        List<ReviewFlag> flags = new ArrayList<>();
        check("letter", letter.greeting(), facts, flags);
        for (String p : nullSafe(letter.paragraphs())) {
            check("letter", p, facts, flags);
            checkLanguage("letter", p, facts, flags);
        }
        check("letter", letter.closing(), facts, flags);
        return dedupe(flags);
    }

    void check(String section, String text, FactBase facts, List<ReviewFlag> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher n = NUMBER.matcher(text);
        while (n.find()) {
            if (!facts.numbers().contains(FactBase.normalizeNumber(n.group(1)))) {
                out.add(new ReviewFlag(section, "The number \"" + n.group().strip() + "\" is not in your profile"));
            }
        }

        Matcher w = WORD.matcher(text);
        while (w.find()) {
            String original = w.group().replaceAll("^[./]+|[./]+$", "");
            for (String t : LexicalMatcher.tokens(original)) {
                if (facts.jobTerms().contains(t) && !facts.profileTokens().contains(t)) {
                    // Quote the word as written ("Kubernetes"), not its folded token ("kubernete").
                    out.add(new ReviewFlag(section, "Mentions \"" + original + "\" from the job ad, which is not in your profile"));
                }
            }
        }

        Matcher o = ORG_AFTER.matcher(text);
        while (o.find()) {
            String name = o.group(1);
            List<String> nameTokens = LexicalMatcher.terms(name); // drops "'s", stopwords
            if (!nameTokens.isEmpty() && !facts.allowedNameTokens().containsAll(nameTokens)) {
                out.add(new ReviewFlag(section, "Mentions \"" + name
                        + "\", which is not one of your employers, schools or the target company"));
            }
        }
    }

    /**
     * Small models sometimes drift into the profile's or the job ad's language. Only longer texts are
     * checked (a clear majority of EN/FR function words), so short bullets never trigger it.
     */
    static void checkLanguage(String section, String text, FactBase facts, List<ReviewFlag> out) {
        String expected = "fr".equals(facts.language()) ? "fr" : "en";
        String actual = LanguageGuess.clear(text, 4);
        if (actual != null && !actual.equals(expected)) {
            out.add(new ReviewFlag(section, "Written in " + name(actual) + ", but this document is in " + name(expected)));
        }
    }

    private static String name(String lang) {
        return "fr".equals(lang) ? "French" : "English";
    }

    private static List<ReviewFlag> dedupe(List<ReviewFlag> flags) {
        Set<String> seen = new LinkedHashSet<>();
        List<ReviewFlag> out = new ArrayList<>();
        for (ReviewFlag f : flags) {
            if (seen.add(f.section() + "|" + f.message().toLowerCase(Locale.ROOT))) {
                out.add(f);
            }
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
