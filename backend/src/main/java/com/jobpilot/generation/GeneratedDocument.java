package com.jobpilot.generation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A generated CV or letter version. {@code content} is the structured, editable JSON
 * ({@link com.jobpilot.generation.content.CvContent} / {@code LetterContent}); LaTeX/PDF
 * fields are filled in Phase 5.
 */
@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false)
    private int version;

    @Column(length = 5)
    private String language;

    @Column(length = 50)
    private String template;

    @Column(name = "template_version", length = 20)
    private String templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_options", columnDefinition = "jsonb")
    private Map<String, Object> templateOptions = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode content;

    @Column(name = "latex_source", columnDefinition = "text")
    private String latexSource;

    @Column(name = "pdf_path", length = 500)
    private String pdfPath;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "ats_score")
    private Integer atsScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ats_report", columnDefinition = "jsonb")
    private JsonNode atsReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GeneratedDocument() {
    }

    public GeneratedDocument(UUID applicationId, DocumentType type, int version, String language, JsonNode content) {
        this.applicationId = applicationId;
        this.type = type;
        this.version = version;
        this.language = language;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public DocumentType getType() {
        return type;
    }

    public int getVersion() {
        return version;
    }

    public String getLanguage() {
        return language;
    }

    public String getTemplate() {
        return template;
    }

    public JsonNode getContent() {
        return content;
    }

    public void setContent(JsonNode content) {
        this.content = content;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
