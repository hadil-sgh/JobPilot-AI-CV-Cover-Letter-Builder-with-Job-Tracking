package com.jobpilot.profile.cv;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient parsing of CV dates ("2021", "2021-03", "03/2021", "Mar 2021", "mars 2021") to the first
 * day of the month. Ongoing markers ("Present", "Current", "Aujourd'hui"...) and anything
 * unrecognised return empty, which is stored as NULL.
 */
public final class CvDates {

    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})(?:-\\d{1,2})?$");
    private static final Pattern SLASH = Pattern.compile("^(\\d{1,2})[/.](\\d{4})$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern MONTH_NAME = Pattern.compile("^([\\p{L}.]+)\\s+(\\d{4})$");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("janv", 1), Map.entry("janvier", 1), Map.entry("january", 1),
            Map.entry("feb", 2), Map.entry("fev", 2), Map.entry("fevr", 2), Map.entry("fevrier", 2), Map.entry("february", 2),
            Map.entry("mar", 3), Map.entry("mars", 3), Map.entry("march", 3),
            Map.entry("apr", 4), Map.entry("avr", 4), Map.entry("avril", 4), Map.entry("april", 4),
            Map.entry("may", 5), Map.entry("mai", 5),
            Map.entry("jun", 6), Map.entry("juin", 6), Map.entry("june", 6),
            Map.entry("jul", 7), Map.entry("juil", 7), Map.entry("juillet", 7), Map.entry("july", 7),
            Map.entry("aug", 8), Map.entry("aout", 8), Map.entry("august", 8),
            Map.entry("sep", 9), Map.entry("sept", 9), Map.entry("septembre", 9), Map.entry("september", 9),
            Map.entry("oct", 10), Map.entry("octobre", 10), Map.entry("october", 10),
            Map.entry("nov", 11), Map.entry("novembre", 11), Map.entry("november", 11),
            Map.entry("dec", 12), Map.entry("decembre", 12), Map.entry("december", 12));

    private CvDates() {
    }

    public static Optional<LocalDate> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = stripAccents(raw.trim().toLowerCase(Locale.ROOT));
        Matcher m;
        if ((m = ISO.matcher(s)).matches()) {
            return of(m.group(1), m.group(2));
        }
        if ((m = SLASH.matcher(s)).matches()) {
            return of(m.group(2), m.group(1));
        }
        if ((m = YEAR.matcher(s)).matches()) {
            return of(m.group(1), "1");
        }
        if ((m = MONTH_NAME.matcher(s)).matches()) {
            Integer month = MONTHS.get(m.group(1).replace(".", ""));
            return month == null ? Optional.empty() : of(m.group(2), month.toString());
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> of(String year, String month) {
        int y = Integer.parseInt(year);
        int mo = Integer.parseInt(month);
        if (y < 1950 || y > 2100 || mo < 1 || mo > 12) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.of(y, mo, 1));
    }

    private static String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
