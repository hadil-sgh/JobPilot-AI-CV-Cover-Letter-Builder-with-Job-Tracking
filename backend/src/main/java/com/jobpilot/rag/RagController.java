package com.jobpilot.rag;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jobpilot.auth.AuthUser;
import com.jobpilot.profile.ProfileService;

/** Lets the user see and rebuild what the AI can retrieve from their profile. */
@RestController
@RequestMapping("/api/profile")
public class RagController {

    public record EvidenceDto(UUID itemId, String type, String content, double similarity) {

        static EvidenceDto of(ProfileChunkRepository.Hit h) {
            return new EvidenceDto(h.itemId(), String.valueOf(h.metadata().get("type")), h.content(),
                    Math.round(h.similarity() * 1000) / 1000.0);
        }
    }

    public record IndexStatus(int chunks) {
    }

    private final ProfileService profiles;
    private final ProfileIndexer indexer;
    private final ProfileChunkRepository chunks;
    private final RetrievalService retrieval;

    public RagController(ProfileService profiles, ProfileIndexer indexer, ProfileChunkRepository chunks,
                         RetrievalService retrieval) {
        this.profiles = profiles;
        this.indexer = indexer;
        this.chunks = chunks;
        this.retrieval = retrieval;
    }

    @GetMapping("/index")
    public IndexStatus status(@AuthenticationPrincipal AuthUser user) {
        return new IndexStatus(chunks.count(profiles.profileIdOf(user.id())));
    }

    /** Synchronous full rebuild (normally the index updates by itself after each edit). */
    @PostMapping("/reindex")
    public IndexStatus reindex(@AuthenticationPrincipal AuthUser user) {
        return new IndexStatus(indexer.reindex(profiles.profileIdOf(user.id())));
    }

    /** Raw top-k search (no threshold) so the user can check what the AI "sees" for a query. */
    @GetMapping("/search")
    public List<EvidenceDto> search(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam @NotBlank @Size(max = 500) String q,
                                    @RequestParam(defaultValue = "5") @Min(1) @Max(10) int k) {
        return retrieval.search(q, profiles.profileIdOf(user.id()), k, 0).stream().map(EvidenceDto::of).toList();
    }
}
