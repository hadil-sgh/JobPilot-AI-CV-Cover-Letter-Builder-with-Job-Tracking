package com.jobpilot.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Automated ATS self-check on the compiled PDF (PROJECT.md 3.9): extracts the text the way an
 * applicant tracking system would (PDFBox) and verifies it is usable.
 * <ol>
 *   <li>section headings are found, in order (30 pts);</li>
 *   <li>email and phone appear as plain text (25 pts);</li>
 *   <li>text is clean: no ligature glyphs (ﬁ ﬂ…), no replacement/garbage characters (25 pts);</li>
 *   <li>page count within the template's limit (20 pts).</li>
 * </ol>
 */
@Component
public class AtsChecker {

    public record Check(String id, String label, boolean passed, String detail, int weight) {
    }

    public record Report(int score, int pageCount, List<Check> checks, String extractedPreview) {
    }

    public Report check(byte[] pdf, List<String> headingsInOrder, List<String> plainTexts, int maxPages) {
        String text;
        int pages;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        } catch (IOException e) {
            return new Report(0, 0, List.of(new Check("readable", "PDF is readable", false, e.getMessage(), 100)), "");
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        String lower = flat.toLowerCase(Locale.ROOT);

        List<Check> checks = new ArrayList<>();
        checks.add(headings(lower, headingsInOrder));
        checks.add(contact(flat, plainTexts));
        checks.add(textQuality(text));
        checks.add(new Check("pages", "Fits in " + maxPages + " page" + (maxPages > 1 ? "s" : ""), pages <= maxPages,
                pages + " page" + (pages > 1 ? "s" : ""), 20));

        int score = checks.stream().filter(Check::passed).mapToInt(Check::weight).sum();
        return new Report(score, pages, checks, flat.length() > 300 ? flat.substring(0, 300) + "…" : flat);
    }

    static Check headings(String lowerText, List<String> headings) {
        if (headings.isEmpty()) {
            return new Check("headings", "Standard section headings in order", true, "No sections expected", 30);
        }
        int from = 0;
        List<String> problems = new ArrayList<>();
        for (String h : headings) {
            int at = lowerText.indexOf(h.toLowerCase(Locale.ROOT), from);
            if (at < 0) {
                problems.add(lowerText.contains(h.toLowerCase(Locale.ROOT)) ? "\"" + h + "\" out of order" : "\"" + h + "\" not found");
            } else {
                from = at + h.length();
            }
        }
        return new Check("headings", "Standard section headings in order", problems.isEmpty(),
                problems.isEmpty() ? String.join(" → ", headings) : String.join(", ", problems), 30);
    }

    static Check contact(String text, List<String> plainTexts) {
        String digitsOnly = text.replaceAll("[^0-9+]", "");
        List<String> missing = new ArrayList<>();
        for (String p : plainTexts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            boolean phone = p.matches("[+0-9 ().-]{6,}");
            boolean found = phone ? digitsOnly.contains(p.replaceAll("[^0-9+]", "")) : text.contains(p);
            if (!found) {
                missing.add(p);
            }
        }
        return new Check("contact", "Contact details are plain, extractable text", missing.isEmpty(),
                missing.isEmpty() ? "Email and phone found" : "Not found in the text: " + String.join(", ", missing), 25);
    }

    static Check textQuality(String text) {
        List<String> problems = new ArrayList<>();
        if (text.codePoints().anyMatch(c -> c >= 0xFB00 && c <= 0xFB06)) {
            problems.add("ligature glyphs (e.g. \"ﬁ\") would be read as unknown characters");
        }
        if (text.indexOf('�') >= 0 || text.contains("(cid:")) {
            problems.add("unmapped glyphs (garbled text)");
        }
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (text.strip().length() < 100 || letters < text.strip().length() * 0.5) {
            problems.add("too little readable text");
        }
        return new Check("text", "Text is clean and machine-readable", problems.isEmpty(),
                problems.isEmpty() ? text.strip().length() + " characters extracted" : String.join("; ", problems), 25);
    }
}
