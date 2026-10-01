package com.jobpilot.generation;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

/** Generation jobs and generated documents (PROJECT.md 2.6). */
@RestController
public class GenerationController {

    public record UpdateRequest(@NotNull JsonNode content) {
    }

    public record RegenerateRequest(@NotBlank @Size(max = 40) String section) {
    }

    private final GenerationService generation;
    private final DocumentService documents;

    public GenerationController(GenerationService generation, DocumentService documents) {
        this.generation = generation;
        this.documents = documents;
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

    @PostMapping("/api/documents/{id}/regenerate-section")
    public DocumentDto regenerate(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                  @Valid @RequestBody RegenerateRequest req) {
        return documents.regenerateSection(user.id(), id, req.section());
    }
}
