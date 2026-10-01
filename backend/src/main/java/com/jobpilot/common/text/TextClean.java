package com.jobpilot.common.text;

/** Small, dependency-free text sanitising helpers used for user- and LLM-provided strings. */
public final class TextClean {

    /** Zero-width, bidi-override and BOM characters: invisible, and a classic way to hide text. */
    private static final String INVISIBLE = "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]";

    private TextClean() {
    }

    /** Removes control/invisible characters, collapses whitespace, trims, caps length; blank → null. */
    public static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String s = value.replaceAll(INVISIBLE, "")
                .replaceAll("[\\p{Cntrl}&&[^\n]]", " ")
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll(" *\n+ *", "\n")
                .strip();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) {
            return null;
        }
        return s.length() > max ? s.substring(0, max).strip() : s;
    }

    /** Cleans each entry (single line), drops blanks and case-insensitive duplicates, caps the count. */
    public static java.util.List<String> cleanList(java.util.List<String> values, int maxCount, int maxLength) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (values == null) {
            return out;
        }
        for (String v : values) {
            String c = clean(v, maxLength);
            if (c != null && seen.add(c.toLowerCase(java.util.Locale.ROOT))) {
                out.add(c.replace('\n', ' '));
                if (out.size() == maxCount) {
                    break;
                }
            }
        }
        return out;
    }

    /** Multi-line text: keeps paragraphs (max one blank line), drops control/invisible characters. */
    public static String normalizeBlock(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll(INVISIBLE, "")
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[ \t ]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
