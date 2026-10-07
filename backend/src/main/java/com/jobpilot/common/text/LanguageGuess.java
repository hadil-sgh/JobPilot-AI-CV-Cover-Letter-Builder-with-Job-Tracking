package com.jobpilot.common.text;

import java.util.regex.Pattern;

/**
 * Deterministic EN/FR detection from common function words. Small models mislabel the language
 * of a text (e.g. an English offer that asks for "fluent French"), so code decides.
 */
public final class LanguageGuess {

    private static final Pattern FRENCH = Pattern.compile(
            "\\b(nous|vous|votre|vos|je|j|mon|mes|avec|pour|dans|sur|une|des|les|du|au|aux|et|est|sont|été|"
                    + "poste|équipe|expérience|compétences|développement|recherchons|chez|ainsi|également)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ENGLISH = Pattern.compile(
            "\\b(we|you|your|i|my|the|and|with|for|in|on|of|to|is|are|was|have|has|team|experience|skills|"
                    + "role|looking|our|also|built|developed|using)\\b",
            Pattern.CASE_INSENSITIVE);

    private LanguageGuess() {
    }

    /** "en" or "fr" when the text clearly leans one way (≥ {@code minHits} hits and 2:1), else null. */
    public static String clear(String text, int minHits) {
        if (text == null || text.isBlank()) {
            return null;
        }
        long fr = FRENCH.matcher(text).results().count();
        long en = ENGLISH.matcher(text).results().count();
        if (fr >= minHits && fr >= 2 * en) {
            return "fr";
        }
        if (en >= minHits && en >= 2 * fr) {
            return "en";
        }
        return null;
    }

    /** Best guess, defaulting to English when unclear. */
    public static String guess(String text) {
        String clear = clear(text, 1);
        return clear == null ? "en" : clear;
    }
}
