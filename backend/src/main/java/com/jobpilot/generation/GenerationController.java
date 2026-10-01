package com.jobpilot.generation;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobpilot.auth.AuthUser;
import com.jobpilot.generation.DocumentService.DocumentDto;
import com.jobpilot.generation.GenerationService.JobDto;
import com.jobpilot.template.TemplateManifest;
import com.jobpilot.template.TemplateRegistry;

/** Generation jobs and generated documents (PROJECT.md 2.6). */
@RestController
public class GenerationController {

    public record UpdateRequest(@NotNull JsonNode content) {
    }

    public record RegenerateRequest(@NotBlank @Size(max = 40) String section) {
    }

    private final GenerationService generation;
    private final DocumentService documents;
    private final TemplateRegistry templates;

    public GenerationController(GenerationService generation, DocumentService documents, TemplateRegistry templates) {
        this.generation = generation;
        this.documents = documents;
        this.templates = templates;
    }

    /** Starts async generation of a CV + letter; poll the returned job. */
    @PostMapping("/api/applications/{id}/generate")
    public ResponseEntity<JobDto> generate(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(generation.start(user.id(), id));
    }

    @GetMapping("/api/generation-jobs/{id}")
    public JobDto job(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return generation.get(user.id(), id);
    }

    /** Latest generation job of an application (204 if none yet). */
    @GetMapping("/api/applications/{id}/generation")
    public ResponseEntity<JobDto> latestJob(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return generation.latest(user.id(), id).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/api/applications/{id}/documents")
    public List<DocumentDto> documents(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return documents.list(user.id(), id);
    }

    @GetMapping("/api/documents/{id}")
    public DocumentDto document(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return documents.get(user.id(), id);
    }

    @PutMapping("/api/documents/{id}")
    public DocumentDto update(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                              @Valid @RequestBody UpdateRequest req) {
        return documents.update(user.id(), id, req.content());
    }

    public record RenderRequest(@Size(max = 50) String template, Map<String, Object> options) {
    }

    /** Renders the document to PDF with a LaTeX template and runs the ATS self-check. */
    @PostMapping("/api/documents/{id}/render")
    public DocumentDto render(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                              @Valid @RequestBody RenderRequest req) {
        return documents.render(user.id(), id, req.template(), req.options());
    }

    @GetMapping("/api/documents/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        DocumentService.Pdf pdf = documents.pdf(user.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf.bytes());
    }

    @GetMapping("/api/templates")
    public List<TemplateManifest> templates() {
        return templates.list();
    }

    @PostMapping("/api/documents/{id}/regenerate-section")
    public DocumentDto regenerate(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                  @Valid @RequestBody RegenerateRequest req) {
        return documents.regenerateSection(user.id(), id, req.section());
    }
}
