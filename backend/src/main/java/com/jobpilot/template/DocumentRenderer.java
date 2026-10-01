package com.jobpilot.template;

import java.util.Map;

/**
 * Turns a document model into a file (PROJECT.md 3.9 extension point 2). {@link LatexRenderer}
 * today; an HtmlRenderer or DocxRenderer can be added later without touching callers.
 */
public interface DocumentRenderer {

    /** @param kind "cv" or "letter" (selects {@code <kind>.tex.ftl}) */
    Rendered render(String templateId, String kind, Map<String, Object> model, Map<String, Object> requestedOptions);

    record Rendered(TemplateManifest template, Map<String, Object> options, String source, byte[] pdf) {
    }
}
