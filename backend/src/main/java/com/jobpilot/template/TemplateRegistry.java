package com.jobpilot.template;

import java.util.List;
import java.util.Map;

/**
 * Template catalogue (PROJECT.md 3.9 extension point 1). Today: folders on the classpath; later a
 * DB- or user-backed implementation can replace it without touching callers.
 * New look = new folder {@code templates/latex/<id>/} with manifest.json, cv.tex.ftl,
 * letter.tex.ftl and any .sty files.
 */
public interface TemplateRegistry {

    List<TemplateManifest> list();

    /** @throws com.jobpilot.common.error.ApiException 404 if unknown */
    TemplateManifest get(String id);

    /** Requested options merged over manifest defaults, validated against the manifest (400 if invalid). */
    Map<String, Object> resolveOptions(String id, Map<String, Object> requested);

    /** Renders {@code <id>/<file>} (e.g. "cv.tex.ftl") with LaTeX auto-escaping. */
    String render(String id, String file, Map<String, Object> model);

    /** Static support files sent along to the compiler (e.g. the template's .sty), by file name. */
    Map<String, String> assets(String id);
}
