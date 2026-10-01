package com.jobpilot.template;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/** FreeMarker (auto-escaped) → .tex → latex-worker → PDF. */
@Component
public class LatexRenderer implements DocumentRenderer {

    private final TemplateRegistry registry;
    private final LatexWorkerClient worker;

    public LatexRenderer(TemplateRegistry registry, LatexWorkerClient worker) {
        this.registry = registry;
        this.worker = worker;
    }

    @Override
    public Rendered render(String templateId, String kind, Map<String, Object> model,
                           Map<String, Object> requestedOptions) {
        TemplateManifest manifest = registry.get(templateId);
        Map<String, Object> options = registry.resolveOptions(templateId, requestedOptions);
        String tex = registry.render(templateId, kind + ".tex.ftl", Map.of("doc", model, "opt", templateOptions(options)));

        Map<String, String> files = new LinkedHashMap<>(registry.assets(templateId));
        files.put(kind + ".tex", tex);
        return new Rendered(manifest, options, tex, worker.compile(kind + ".tex", files));
    }

    /** Options as the template sees them (adds derived values such as the colour without '#'). */
    static Map<String, Object> templateOptions(Map<String, Object> options) {
        Map<String, Object> opt = new LinkedHashMap<>(options);
        Object color = options.get("accentColor");
        opt.put("accentHex", color == null ? "1F3A5F" : color.toString().substring(1));
        return opt;
    }
}
