package com.jobpilot.rag;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * nomic-embed-text wrapper. The model expects task prefixes: "search_document: " for stored
 * chunks and "search_query: " for queries (PROJECT.md 3.3). Vectors must be 768-d to fit
 * profile_chunks.embedding.
 */
@Service
public class EmbeddingService {

    public static final int DIMENSIONS = 768;

    private final EmbeddingModel model;

    public EmbeddingService(EmbeddingModel model) {
        this.model = model;
    }

    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = model.embed(texts.stream().map(t -> "search_document: " + t).toList());
        vectors.forEach(EmbeddingService::check);
        return vectors;
    }

    public float[] embedQuery(String query) {
        return check(model.embed("search_query: " + query));
    }

    private static float[] check(float[] v) {
        if (v.length != DIMENSIONS) {
            throw new IllegalStateException("Embedding model returned " + v.length + " dimensions, expected "
                    + DIMENSIONS + ". Is OLLAMA_EMBEDDING_MODEL set to nomic-embed-text?");
        }
        return v;
    }
}
