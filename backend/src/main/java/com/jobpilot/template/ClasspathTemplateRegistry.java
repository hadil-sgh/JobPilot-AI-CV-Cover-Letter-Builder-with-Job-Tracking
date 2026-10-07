package com.jobpilot.template;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.ClassTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.template.TemplateManifest.OptionSpec;

/**
 * Discovers {@code classpath:templates/latex/<id>/manifest.json} at startup. FreeMarker uses the
 * square-bracket syntax ({@code [=x]}, {@code [#if]}) — neither "[=" nor "[#" occurs in LaTeX —
 * and the {@link LatexOutputFormat} so every interpolation is escaped. Templates cannot call Java
 * ({@code ?new}/{@code ?api} disabled).
 */
@Component
public class ClasspathTemplateRegistry implements TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClasspathTemplateRegistry.class);
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,40}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> KNOWN_SECTIONS =
            Set.of("summary", "experience", "projects", "education", "skills", "certifications", "languages");

    private final Map<String, TemplateManifest> manifests = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> assets = new LinkedHashMap<>();
    private final Configuration freemarker;

    public ClasspathTemplateRegistry(ObjectMapper json) throws IOException {
        this.freemarker = configuration();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource r : resolver.getResources("classpath*:templates/latex/*/manifest.json")) {
            TemplateManifest m = json.readValue(r.getInputStream(), TemplateManifest.class);
            if (m.id() == null || !ID.matcher(m.id()).matches()) {
                throw new IllegalStateException("Invalid template id in " + r);
            }
            for (String s : m.sections()) {
                if (!KNOWN_SECTIONS.contains(s)) {
                    throw new IllegalStateException("Template " + m.id() + " declares unknown section " + s);
                }
            }
            manifests.put(m.id(), m);
            Map<String, String> files = new LinkedHashMap<>();
            for (Resource sty : resolver.getResources("classpath*:templates/latex/" + m.id() + "/*.sty")) {
                files.put(sty.getFilename(), sty.getContentAsString(StandardCharsets.UTF_8));
            }
            assets.put(m.id(), files);
        }
        log.info("Loaded LaTeX templates: {}", manifests.keySet());
    }

    static Configuration configuration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setTemplateLoader(new ClassTemplateLoader(ClasspathTemplateRegistry.class, "/templates/latex"));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        cfg.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        cfg.setOutputFormat(LatexOutputFormat.INSTANCE);
        cfg.setAutoEscapingPolicy(Configuration.ENABLE_IF_DEFAULT_AUTO_ESCAPING_POLICY);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setAPIBuiltinEnabled(false);
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setLocale(Locale.ROOT);
        cfg.setNumberFormat("computer");
        return cfg;
    }

    @Override
    public List<TemplateManifest> list() {
        return manifests.values().stream().sorted(Comparator.comparing(TemplateManifest::name)).toList();
    }

    @Override
    public TemplateManifest get(String id) {
        TemplateManifest m = id == null ? null : manifests.get(id);
        if (m == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown template \"" + id + "\"");
        }
        return m;
    }

    @Override
    public Map<String, Object> resolveOptions(String id, Map<String, Object> requested) {
        TemplateManifest m = get(id);
        Map<String, Object> req = requested == null ? Map.of() : requested;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, OptionSpec> e : m.optionsOrEmpty().entrySet()) {
            String key = e.getKey();
            OptionSpec spec = e.getValue();
            Object value = req.containsKey(key) && req.get(key) != null ? req.get(key) : defaultOf(spec);
            out.put(key, validate(m, key, spec, value));
        }
        return out;
    }

    private static Object defaultOf(OptionSpec spec) {
        JsonNode d = spec.defaultValue();
        if (d == null || d.isNull()) {
            return null;
        }
        if (d.isArray()) {
            List<String> list = new ArrayList<>();
            d.forEach(n -> list.add(n.asText()));
            return list;
        }
        return d.isBoolean() ? d.asBoolean() : d.asText();
    }

    private static Object validate(TemplateManifest m, String key, OptionSpec spec, Object value) {
        switch (spec.type()) {
            case "enum" -> {
                String v = String.valueOf(value);
                if (spec.values() == null || !spec.values().contains(v)) {
                    throw bad(key, "must be one of " + spec.values());
                }
                return v;
            }
            case "color" -> {
                String v = String.valueOf(value);
                if (!COLOR.matcher(v).matches()) {
                    throw bad(key, "must be a colour like #1F3A5F");
                }
                return v.toUpperCase(Locale.ROOT);
            }
            case "bool" -> {
                if (value instanceof Boolean b) {
                    return b;
                }
                throw bad(key, "must be true or false");
            }
            case "list" -> {
                if (!(value instanceof Collection<?> c)) {
                    throw bad(key, "must be a list");
                }
                // Only this template's sections, no duplicates; sections left out are appended.
                Set<String> ordered = new LinkedHashSet<>();
                for (Object o : c) {
                    String s = String.valueOf(o);
                    if (m.sections().contains(s)) {
                        ordered.add(s);
                    }
                }
                ordered.addAll(m.sections());
                return new ArrayList<>(ordered);
            }
            default -> throw new IllegalStateException("Unknown option type " + spec.type() + " in " + m.id());
        }
    }

    private static ApiException bad(String key, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Option \"" + key + "\" " + message);
    }

    @Override
    public String render(String id, String file, Map<String, Object> model) {
        get(id);
        try {
            StringWriter out = new StringWriter();
            freemarker.getTemplate(id + "/" + file).process(model, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Rendering template " + id + "/" + file + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> assets(String id) {
        get(id);
        return assets.getOrDefault(id, Map.of());
    }

    /** For callers that need to pass pre-built LaTeX (e.g. escaped URLs) without double escaping. */
    public static Object markup(String latex) {
        try {
            return LatexOutputFormat.INSTANCE.fromMarkup(latex);
        } catch (freemarker.template.TemplateModelException e) {
            throw new IllegalStateException(e);
        }
    }
}
