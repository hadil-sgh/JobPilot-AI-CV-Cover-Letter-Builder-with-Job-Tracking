package com.jobpilot.template;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Makes untrusted text (LLM output, profile, job ad) safe to place in a LaTeX document
 * (PROJECT.md 3.9 "LaTeX escaping (critical)"). Applied automatically to every FreeMarker
 * interpolation through {@link LatexOutputFormat}, so templates cannot forget it.
 */
public final class LatexEscaper {

    private LatexEscaper() {
    }

    /** Escapes \ & % $ # _ { } ~ ^ (and &lt; &gt; |), normalises quotes/dashes, strips control and invisible chars. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]", "")
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[\t\n\r]+", " ")
                .replace(' ', ' ')
                // Smart quotes → ASCII (cleaner ATS text); en/em/minus dashes → one en dash (XeTeX renders
                // Unicode directly); ellipsis → three dots.
                .replace('‘', '\'').replace('’', '\'').replace('‚', '\'')
                .replace('“', '"').replace('”', '"').replace('„', '"')
                .replace('—', '–').replace('−', '-')
                .replace("…", "...");
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\textbackslash{}");
                case '&', '%', '$', '#', '_', '{', '}' -> out.append('\\').append(c);
                case '~' -> out.append("\\textasciitilde{}");
                case '^' -> out.append("\\textasciicircum{}");
                case '<' -> out.append("\\textless{}");
                case '>' -> out.append("\\textgreater{}");
                case '|' -> out.append("\\textbar{}");
                case '"' -> out.append("\\textquotedbl{}");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * URL for {@code \href{...}}: only http(s) URLs; everything outside a conservative character
     * set is percent-encoded, then % and # are escaped for LaTeX. Returns null for anything else.
     */
    public static String escapeUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.strip();
        try {
            URI uri = URI.create(u.replace(" ", "%20"));
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (byte b : u.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "-._:/?&=+@,;!*'()%".indexOf(c) >= 0;
            if (safe && b >= 0) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString().replace("%", "\\%").replace("#", "\\#");
    }
}
