package com.jobpilot.generation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.application.ApplicationService;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.validation.FactBase;
import com.jobpilot.generation.validation.FactValidator;

/**
 * Read, edit and partially regenerate generated documents. Edits and regenerated sections are
 * re-validated, so review flags always describe the current text.
 */
@Service
public class DocumentService {

    public record DocumentDto(UUID id, UUID applicationId, DocumentType type, int version, String language,
                              String template, Integer matchScore, Instant createdAt, JsonNode content) {
    }

    private final GeneratedDocumentRepository documents;
    private final ApplicationService applications;
    private final GenerationPipeline pipeline;
    private final FactValidator validator;
    private final ObjectMapper json;

    public DocumentService(GeneratedDocumentRepository documents, ApplicationService applications,
                           GenerationPipeline pipeline, FactValidator validator, ObjectMapper objectMapper) {
        this.documents = documents;
        this.applications = applications;
        this.pipeline = pipeline;
        this.validator = validator;
        this.json = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

    /** Re-reads the row (the LLM call may have taken minutes) and saves the new content. */
    private DocumentDto save(GeneratedDocument doc, JsonNode content) {
        GeneratedDocument fresh = documents.findById(doc.getId()).orElseThrow();
        fresh.setContent(content);
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
                d.getTemplate(), d.getMatchScore(), d.getCreatedAt(), withContent ? d.getContent() : null);
    }
}
