package com.jobpilot.generation;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;

/** Persists generated documents as new versions (short transactions only). */
@Component
public class DocumentStore {

    public record Saved(UUID cvId, UUID letterId) {
    }

    private final GeneratedDocumentRepository documents;
    private final ObjectMapper json;

    public DocumentStore(GeneratedDocumentRepository documents, ObjectMapper json) {
        this.documents = documents;
        this.json = json;
    }

    /** Saves {@code content} as the next version of {@code source}'s type, in another language. */
    @Transactional
    public GeneratedDocument saveDerivedVersion(GeneratedDocument source, String language, Object content) {
        GeneratedDocument doc = new GeneratedDocument(source.getApplicationId(), source.getType(),
                documents.maxVersion(source.getApplicationId(), source.getType()) + 1, language, json.valueToTree(content));
        doc.copyTemplateFrom(source);
        return documents.save(doc);
    }

    @Transactional
    public Saved saveNewVersions(UUID applicationId, CvContent cv, LetterContent letter) {
        GeneratedDocument cvDoc = new GeneratedDocument(applicationId, DocumentType.CV,
                documents.maxVersion(applicationId, DocumentType.CV) + 1, cv.language(), json.valueToTree(cv));
        cvDoc.setMatchScore(cv.match() == null ? null : cv.match().score());
        GeneratedDocument letterDoc = new GeneratedDocument(applicationId, DocumentType.COVER_LETTER,
                documents.maxVersion(applicationId, DocumentType.COVER_LETTER) + 1, letter.language(),
                json.valueToTree(letter));
        return new Saved(documents.save(cvDoc).getId(), documents.save(letterDoc).getId());
    }
}
