package com.jobpilot.rag;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jobpilot.rag.ProfileChunkRepository.Hit;

/**
 * Profile evidence search. Hybrid retrieval (see docs/DECISIONS.md, Phase 3):
 * <ol>
 *   <li>vector candidates: top {@code candidates} chunks by cosine similarity (pgvector);</li>
 *   <li>score = similarity + lexicalWeight × (share of the requirement's key terms in the chunk);</li>
 *   <li>a chunk is evidence if it shares ≥1 key term and similarity ≥ {@code minWithTerms},
 *       or if similarity alone ≥ {@code minAlone};</li>
 *   <li>best {@code topK} by score.</li>
 * </ol>
 */
@Service
public class RetrievalService {

    /** A retrieved chunk with its scores and the requirement terms it contains (for explainability). */
    public record Evidence(Hit hit, double score, List<String> matchedTerms) {
    }

    private static final int CANDIDATES = 30;

    private final EmbeddingService embeddings;
    private final ProfileChunkRepository chunks;
    private final int topK;
    private final double lexicalWeight;
    private final double minWithTerms;
    private final double minAlone;

    public RetrievalService(EmbeddingService embeddings, ProfileChunkRepository chunks,
                            @Value("${jobpilot.rag.top-k:4}") int topK,
                            @Value("${jobpilot.rag.lexical-weight:0.35}") double lexicalWeight,
                            @Value("${jobpilot.rag.min-similarity-with-terms:0.45}") double minWithTerms,
                            @Value("${jobpilot.rag.min-similarity-alone:0.70}") double minAlone) {
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.topK = topK;
        this.lexicalWeight = lexicalWeight;
        this.minWithTerms = minWithTerms;
        this.minAlone = minAlone;
    }

    /** Evidence for one job requirement. */
    public List<Evidence> evidenceFor(String requirement, UUID profileId) {
        if (requirement == null || requirement.isBlank()) {
            return List.of();
        }
        List<Hit> candidates = chunks.search(profileId, embeddings.embedQuery(requirement.strip()), CANDIDATES, 0);
        return rank(requirement, candidates);
    }

    /** Pure ranking step (unit-tested with real nomic similarities). */
    List<Evidence> rank(String requirement, List<Hit> candidates) {
        List<String> terms = LexicalMatcher.terms(requirement);
        return candidates.stream()
                .map(h -> {
                    List<String> matched = LexicalMatcher.matched(terms, h.content());
                    double lexical = terms.isEmpty() ? 0 : (double) matched.size() / terms.size();
                    return new Evidence(h, h.similarity() + lexicalWeight * lexical, matched);
                })
                .filter(e -> (!e.matchedTerms().isEmpty() && e.hit().similarity() >= minWithTerms)
                        || e.hit().similarity() >= minAlone)
                .sorted(Comparator.comparingDouble(Evidence::score).reversed())
                .limit(topK)
                .toList();
    }

    /** Raw vector search (no hybrid filtering), for the profile "what does the AI see" tool. */
    public List<Hit> search(String query, UUID profileId, int k, double threshold) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return chunks.search(profileId, embeddings.embedQuery(query.strip()), k, threshold);
    }

    public double minSimilarity() {
        return minWithTerms;
    }
}
