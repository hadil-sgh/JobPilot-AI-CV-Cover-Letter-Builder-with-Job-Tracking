package com.jobpilot.job;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.common.llm.LlmJson;
import com.jobpilot.common.text.TextClean;

/**
 * Turns a pasted job description into safe plain text for prompt B (PROJECT.md 3.7):
 * HTML → text (scripts/styles dropped), invisible/control characters removed, our {@code <job>}
 * delimiter removed, lines that address the AI ("ignore previous instructions"...) dropped and
 * reported, and a length limit enforced (~5k tokens, leaving room in the 8k context).
 */
@Component
public class JdSanitizer {

    public static final int MIN_CHARS = 100;
    public static final int MAX_CHARS = 20_000;

    public record SanitizedJd(String text, List<String> warnings) {
    }

    private static final List<Pattern> INJECTION = List.of(
            p("\\b(ignore|disregard|forget|override)\\b.{0,40}\\b(previous|prior|above|earlier|all|any|system)\\b.{0,20}\\b(instruction|prompt|rule|message)s?\\b"),
            p("\\b(system|developer)\\s+(prompt|message|instruction)s?\\b"),
            p("\\byou are (now )?(an? )?(ai|assistant|chatgpt|llm|language model)\\b"),
            p("\\b(reveal|print|repeat|show)\\b.{0,30}\\b(your|the)\\s+(prompt|instructions|system)\\b"),
            p("^\\s*(system|assistant|user)\\s*:"),
            p("</?\\s*(system|assistant|instructions?)\\s*>"),
            p("\\b(respond|reply|answer|output)\\b.{0,20}\\bonly\\b.{0,20}\\b(with|the following)\\b"));

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public SanitizedJd sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Paste a job description first");
        }
        String text = looksLikeHtml(raw) ? htmlToText(raw) : raw;
        text = LlmJson.stripDelimiter(text, "job");
        text = TextClean.normalizeBlock(text);

        List<String> warnings = new ArrayList<>();
        StringBuilder kept = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (INJECTION.stream().anyMatch(pt -> pt.matcher(line).find())) {
                warnings.add("Removed a line that looked like instructions to the AI: \"" + preview(line) + "\"");
            } else {
                kept.append(line).append('\n');
            }
        }
        String result = TextClean.normalizeBlock(kept.toString());

        if (result.length() < MIN_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This job description is too short. Paste the full offer (at least " + MIN_CHARS + " characters).");
        }
        if (result.length() > MAX_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This job description is too long (max "
                    + MAX_CHARS + " characters, about 3,000 words). Remove the company boilerplate and try again.");
        }
        return new SanitizedJd(result, warnings);
    }

    static boolean looksLikeHtml(String s) {
        return s.matches("(?s).*<\\s*(html|body|div|p|br|li|ul|span|h[1-6]|script|style|table)\\b.*");
    }

    static String htmlToText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, iframe, svg, template, head").remove();
        doc.select("br").after("\n");
        doc.select("li").prepend("- "); // before the newline below, so each item reads "\n- text"
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, section, article").prepend("\n");
        return doc.body() == null ? "" : doc.body().wholeText();
    }

    private static String preview(String line) {
        String s = line.strip();
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
