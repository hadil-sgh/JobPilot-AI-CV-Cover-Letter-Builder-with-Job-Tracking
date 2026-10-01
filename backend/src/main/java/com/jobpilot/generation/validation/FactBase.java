package com.jobpilot.generation.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobpilot.generation.GenerationContext;
import com.jobpilot.job.JobAnalysis;
import com.jobpilot.rag.LexicalMatcher;

/**
 * What the generated text is allowed to state, derived deterministically from the profile and the
 * job: profile tokens, profile numbers, the job's "claimable" terms (keywords + technology-looking
 * words of the requirements) and the names that may follow "at/for/chez...".
 */
public record FactBase(Set<String> profileTokens, Set<String> numbers, Set<String> jobTerms,
                       Set<String> allowedNameTokens) {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d])(\\d+(?:[.,]\\d+)?)");
    /** Mid-sentence capitalised word, ALL-CAPS acronym, or token with digits/symbols: likely a technology. */
    private static final Pattern TECHY = Pattern.compile("(?<=\\S\\s)\\p{Lu}[\\p{L}\\d+#.]*|\\b\\p{Lu}{2,}\\b|\\S*[\\d+#]\\S*");

    public static FactBase of(GenerationContext ctx) {
        String profileText = ctx.profile().allText();
        Set<String> profileTokens = new HashSet<>(LexicalMatcher.tokens(profileText));

        Set<String> numbers = numbersIn(profileText);
        numbers.addAll(numbersIn(ctx.company() + " " + ctx.roleTitle()));

        JobAnalysis a = ctx.analysis();
        Set<String> jobTerms = new HashSet<>();
        if (a.keywords() != null) {
            jobTerms.addAll(LexicalMatcher.terms(String.join(" , ", a.keywords())));
        }
        for (List<String> list : java.util.Arrays.asList(a.requirements(), a.niceToHave())) {
            if (list == null) {
                continue;
            }
            for (String sentence : list) {
                Matcher m = TECHY.matcher(sentence);
                while (m.find()) {
                    jobTerms.addAll(LexicalMatcher.terms(m.group()));
                }
            }
        }

        Set<String> names = new HashSet<>(profileTokens);
        names.addAll(LexicalMatcher.tokens(ctx.company()));
        names.addAll(LexicalMatcher.tokens(ctx.roleTitle()));
        return new FactBase(profileTokens, numbers, jobTerms, names);
    }

    static Set<String> numbersIn(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            out.add(normalizeNumber(m.group(1)));
        }
        return out;
    }

    /** "02" → "2", "2,50" → "2.5", "40" → "40". */
    static String normalizeNumber(String n) {
        String s = n.replace(',', '.');
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        s = s.replaceFirst("^0+(?=\\d)", "");
        return s;
    }
}
