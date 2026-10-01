package com.jobpilot.generation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.application.ApplicationService;
import com.jobpilot.common.config.FeatureProperties;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.validation.FactBase;
import com.jobpilot.generation.validation.FactValidator;
import com.jobpilot.template.AtsChecker;
import com.jobpilot.template.DocumentRenderer;
import com.jobpilot.template.TemplateManifest;
import com.jobpilot.template.TemplateRegistry;

/**
 * Read, edit, partially regenerate and render generated documents. Edits and regenerated sections
 * are re-validated, so review flags always describe the current text; any content change drops
 * the old PDF (template + options are kept for the next render).
 */
@Service
public class DocumentService {

    public record DocumentDto(UUID id, UUID applicationId, DocumentType type, int version, String language,
                              String template, String templateVersion, Map<String, Object> templateOptions,
                              Integer matchScore, Integer atsScore, JsonNode atsReport, boolean hasPdf,
                              Instant createdAt, JsonNode content) {
    }

    /** PDF bytes plus a friendly download name. */
    public record Pdf(byte[] bytes, String filename) {
    }

    static final String DEFAULT_TEMPLATE = "ats-classic";

    private final GeneratedDocumentRepository documents;
    private final ApplicationService applications;
    private final GenerationPipeline pipeline;
    private final FactValidator validator;
    private final ObjectMapper json;
    private final TemplateRegistry templates;
    private final DocumentRenderer renderer;
    private final AtsChecker ats;
    private final PdfStorage pdfs;
    private final FeatureProperties features;
    private final DocumentTranslator translator;
    private final DocumentStore store;

    public DocumentService(GeneratedDocumentRepository documents, ApplicationService applications,
                           GenerationPipeline pipeline, FactValidator validator, ObjectMapper objectMapper,
                           TemplateRegistry templates, DocumentRenderer renderer, AtsChecker ats, PdfStorage pdfs,
                           FeatureProperties features, DocumentTranslator translator, DocumentStore store) {
        this.documents = documents;
        this.applications = applications;
        this.pipeline = pipeline;
        this.validator = validator;
        this.json = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.templates = templates;
        this.renderer = renderer;
        this.ats = ats;
        this.pdfs = pdfs;
        this.features = features;
        this.translator = translator;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public DocumentDto get(UUID userId, UUID id) {
        return toDto(owned(userId, id), true);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list(UUID userId, UUID applicationId) {
        applications.owned(userId, applicationId);
        return documents.findByApplicationIdOrderByTypeAscVersionDesc(applicationId).stream()
                .map(d -> toDto(d, false)).toList();
    }

    /** Saves user edits in place (facts stay locked to the profile) and re-runs the fact check. */
    public DocumentDto update(UUID userId, UUID id, JsonNode edited) {
        GeneratedDocument doc = owned(userId, id);
        GenerationContext ctx = pipeline.loadContext(userId, doc.getApplicationId());
        FactBase facts = FactBase.of(ctx);
        JsonNode content;
        if (doc.getType() == DocumentType.CV) {
            CvContent merged = DocumentEdits.applyUserEdits(read(doc.getContent(), CvContent.class), read(edited, CvContent.class));
            content = json.valueToTree(merged.withReview(validator.validateCv(merged, facts)));
        } else {
            LetterContent merged = DocumentEdits.applyUserEdits(read(doc.getContent(), LetterContent.class),
                    read(edited, LetterContent.class));
            content = json.valueToTree(merged.withReview(validator.validateLetter(merged, facts)));
        }
        return save(doc, content);
    }

    /**
     * Regenerates one section with some creativity (synchronous, ~1–2 min on a small GPU).
     * CV sections: summary, skills, experience:E1, projects:P1. Letter: "letter".
     */
    public DocumentDto regenerateSection(UUID userId, UUID id, String section) {
        GeneratedDocument doc = owned(userId, id);
        boolean cv = doc.getType() == DocumentType.CV;
        if (cv ? !DocumentEdits.isCvSection(section) : !"letter".equals(section)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown section \"" + section + "\"");
        }
        GenerationContext ctx = pipeline.loadContext(userId, doc.getApplicationId());
        EvidencePack pack = pipeline.buildPack(ctx);
        FactBase facts = FactBase.of(ctx);
        JsonNode content;
        if (cv) {
            CvContent stored = read(doc.getContent(), CvContent.class);
            CvContent fresh = pipeline.writeCv(ctx, pack, stored.match(), 0.7);
            CvContent merged = DocumentEdits.mergeSection(stored, fresh, section);
            content = json.valueToTree(merged.withReview(validator.validateCv(merged, facts)));
        } else {
            LetterContent fresh = pipeline.writeLetter(ctx, pack, 0.7);
            content = json.valueToTree(fresh.withReview(validator.validateLetter(fresh, facts)));
        }
        return save(doc, content);
    }

    /**
     * Translates a document (EN ⇄ FR) into a new version, re-checked by the fact validator in the
     * target language. Synchronous: one or two LLM calls (~1–2 min on a small GPU).
     */
    public DocumentDto translate(UUID userId, UUID id, String language) {
        GeneratedDocument doc = owned(userId, id);
        if (!"en".equals(language) && !"fr".equals(language)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Language must be \"en\" or \"fr\"");
        }
        if (language.equals(DocumentModels.lang(doc.getLanguage()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This document is already in that language");
        }
        GenerationContext ctx = pipeline.loadContext(userId, doc.getApplicationId());
        GenerationContext target = new GenerationContext(ctx.userId(), ctx.applicationId(), ctx.company(),
                ctx.roleTitle(), ctx.analysis(), language, ctx.profile());
        FactBase facts = FactBase.of(target);
        Object content;
        if (doc.getType() == DocumentType.CV) {
            CvContent cv = translator.translateCv(read(doc.getContent(), CvContent.class), language);
            content = cv.withReview(validator.validateCv(cv, facts));
        } else {
            LetterContent letter = translator.translateLetter(read(doc.getContent(), LetterContent.class), language);
            content = letter.withReview(validator.validateLetter(letter, facts));
        }
        return toDto(store.saveDerivedVersion(doc, language, content), true);
    }

    /**
     * Renders with a LaTeX template (PROJECT.md 3.9), runs the ATS self-check on the PDF and stores
     * template, options, LaTeX source, PDF and ATS result. Synchronous: a few seconds.
     */
    public DocumentDto render(UUID userId, UUID id, String templateId, Map<String, Object> requestedOptions) {
        GeneratedDocument doc = owned(userId, id);
        String template = templateId == null || templateId.isBlank()
                ? (doc.getTemplate() == null ? DEFAULT_TEMPLATE : doc.getTemplate()) : templateId;
        TemplateManifest manifest = templates.get(template);
        if (manifest.languages() != null && !manifest.languages().contains(DocumentModels.lang(doc.getLanguage()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Template \"" + manifest.name() + "\" does not support this language");
        }
        Map<String, Object> options = templates.resolveOptions(template, requestedOptions);
        GenerationContext ctx = pipeline.loadContext(userId, doc.getApplicationId());

        List<String> headings = new ArrayList<>();
        List<String> contact = new ArrayList<>();
        DocumentRenderer.Rendered rendered;
        if (doc.getType() == DocumentType.CV) {
            CvContent cv = read(doc.getContent(), CvContent.class);
            @SuppressWarnings("unchecked")
            List<String> order = (List<String>) options.getOrDefault("sectionOrder", manifest.sections());
            Map<String, Object> model = DocumentModels.cv(cv, order);
            headings.addAll(DocumentModels.headings(model));
            contact.add(cv.header().email());
            contact.add(cv.header().phone());
            rendered = renderer.render(template, "cv", model, options);
        } else {
            LetterContent letter = read(doc.getContent(), LetterContent.class);
            ProfileSnapshot p = ctx.profile();
            CvContent.Header header = new CvContent.Header(p.fullName(), p.headline(), p.email(), p.phone(),
                    p.location(), p.links());
            contact.add(header.email());
            rendered = renderer.render(template, "letter", DocumentModels.letter(letter, header, LocalDate.now()), options);
        }

        Integer atsScore = null;
        JsonNode atsReport = null;
        if (features.atsCheck()) {
            AtsChecker.Report report = ats.check(rendered.pdf(), headings, contact,
                    doc.getType() == DocumentType.CV ? manifest.maxPages() : 1);
            atsScore = report.score();
            atsReport = json.valueToTree(report);
        }
        String path = pdfs.save(doc.getApplicationId(), doc.getId(), rendered.pdf());
        GeneratedDocument fresh = documents.findById(doc.getId()).orElseThrow();
        fresh.rendered(manifest.id(), manifest.version(), rendered.options(), rendered.source(), path, atsScore, atsReport);
        return toDto(documents.save(fresh), true);
    }

    @Transactional(readOnly = true)
    public Pdf pdf(UUID userId, UUID id) {
        GeneratedDocument doc = owned(userId, id);
        if (doc.getPdfPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No PDF yet: render the document first");
        }
        String company = applications.owned(userId, doc.getApplicationId()).getCompany();
        String kind = doc.getType() == DocumentType.CV ? "CV" : ("fr".equals(doc.getLanguage()) ? "Lettre" : "Cover letter");
        String name = (kind + " - " + company + " - v" + doc.getVersion()).replaceAll("[^\\p{L}\\p{N} ._-]", "").strip();
        return new Pdf(pdfs.load(doc.getPdfPath()), name + ".pdf");
    }

    /** Re-reads the row (an LLM call may have taken minutes) and saves the new content. */
    private DocumentDto save(GeneratedDocument doc, JsonNode content) {
        GeneratedDocument fresh = documents.findById(doc.getId()).orElseThrow();
        fresh.setContent(content);
        fresh.contentChanged();
        return toDto(documents.save(fresh), true);
    }

    private GeneratedDocument owned(UUID userId, UUID id) {
        return documents.findOwned(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            T value = json.treeToValue(node, type);
            if (value == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Document content is missing");
            }
            return value;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document content has an invalid format");
        }
    }

    private static DocumentDto toDto(GeneratedDocument d, boolean withContent) {
        return new DocumentDto(d.getId(), d.getApplicationId(), d.getType(), d.getVersion(), d.getLanguage(),
                d.getTemplate(), d.getTemplateVersion(), d.getTemplateOptions(), d.getMatchScore(), d.getAtsScore(),
                d.getAtsReport(), d.getPdfPath() != null, d.getCreatedAt(), withContent ? d.getContent() : null);
    }
}
