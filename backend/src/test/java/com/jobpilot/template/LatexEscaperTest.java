package com.jobpilot.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LatexEscaperTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "R&D | R\\&D",
            "50% faster | 50\\% faster",
            "$100 | \\$100",
            "C# and F# | C\\# and F\\#",
            "snake_case | snake\\_case",
            "{braces} | \\{braces\\}",
            "a~b | a\\textasciitilde{}b",
            "x^2 | x\\textasciicircum{}2",
            "C:\\path | C:\\textbackslash{}path",
            "a<b>c | a\\textless{}b\\textgreater{}c"})
    void escapesEverySpecialCharacter(String in, String expected) {
        assertThat(LatexEscaper.escape(in)).isEqualTo(expected);
    }

    @Test
    void neutralisesInjectionAttempts() {
        String attack = "\\input{/etc/passwd} \\immediate\\write18{rm -rf /} \\def\\x{y}";
        String out = LatexEscaper.escape(attack);
        // No backslash survives except inside our own \text...{} replacements.
        assertThat(out.replace("\\textbackslash{}", "").replace("\\{", "").replace("\\}", "")).doesNotContain("\\");
    }

    @Test
    void normalisesQuotesDashesAndStripsControlChars() {
        assertThat(LatexEscaper.escape("“Hi” it’s 2020—2024…")).isEqualTo("\\textquotedbl{}Hi\\textquotedbl{} it's 2020–2024...");
        assertThat(LatexEscaper.escape("a\u0000b\u200Bc\nd")).isEqualTo("abc d");
        assertThat(LatexEscaper.escape(null)).isEmpty();
        assertThat(LatexEscaper.escape("a|b")).isEqualTo("a\\textbar{}b");
        assertThat(LatexEscaper.escape("Français: é à ç")).isEqualTo("Français: é à ç");
    }

    @Test
    void urlsMustBeHttpAndAreSafeForHref() {
        assertThat(LatexEscaper.escapeUrl("https://github.com/ada")).isEqualTo("https://github.com/ada");
        assertThat(LatexEscaper.escapeUrl("https://x.dev/a b#frag%20")).isEqualTo("https://x.dev/a\\%20b\\%23frag\\%20");
        assertThat(LatexEscaper.escapeUrl("https://x.dev/}\\href{evil")).isNull(); // not a valid URI: rejected outright
        assertThat(LatexEscaper.escapeUrl("javascript:alert(1)")).isNull();
        assertThat(LatexEscaper.escapeUrl("file:///etc/passwd")).isNull();
        assertThat(LatexEscaper.escapeUrl("not a url")).isNull();
    }
}
