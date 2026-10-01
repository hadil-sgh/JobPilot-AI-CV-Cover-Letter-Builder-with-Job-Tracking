package com.jobpilot.template;

import java.io.IOException;
import java.io.Writer;

import freemarker.core.CommonMarkupOutputFormat;
import freemarker.core.CommonTemplateMarkupOutputModel;

/**
 * FreeMarker output format for LaTeX: with auto-escaping on, every {@code [=value]} in a template
 * goes through {@link LatexEscaper}. Values that are already LaTeX (e.g. an escaped URL) are passed
 * as {@link Markup} via {@link #fromMarkup(String)} and are not escaped twice.
 */
public final class LatexOutputFormat extends CommonMarkupOutputFormat<LatexOutputFormat.Markup> {

    public static final LatexOutputFormat INSTANCE = new LatexOutputFormat();

    private LatexOutputFormat() {
    }

    @Override
    public String getName() {
        return "LaTeX";
    }

    @Override
    public String getMimeType() {
        return "application/x-latex";
    }

    @Override
    public void output(String textToEsc, Writer out) throws IOException {
        out.write(LatexEscaper.escape(textToEsc));
    }

    @Override
    public String escapePlainText(String plainTextContent) {
        return LatexEscaper.escape(plainTextContent);
    }

    @Override
    public boolean isLegacyBuiltInBypassed(String builtInName) {
        return false;
    }

    @Override
    protected Markup newTemplateMarkupOutputModel(String plainTextContent, String markupContent) {
        return new Markup(plainTextContent, markupContent);
    }

    /** Already-safe LaTeX fragment. */
    public static final class Markup extends CommonTemplateMarkupOutputModel<Markup> {

        Markup(String plainTextContent, String markupContent) {
            super(plainTextContent, markupContent);
        }

        @Override
        public LatexOutputFormat getOutputFormat() {
            return INSTANCE;
        }
    }
}
