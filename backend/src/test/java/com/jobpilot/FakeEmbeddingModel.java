package com.jobpilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic stand-in for nomic-embed-text in tests: a hashed bag-of-words in 768 dimensions,
 * L2-normalised. Texts sharing words get high cosine similarity, so pgvector search is exercised
 * for real without Ollama.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            out.add(new Embedding(vector(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return 768;
    }

    static float[] vector(String text) {
        float[] v = new float[768];
        String body = text.replaceFirst("^search_(document|query): ", "").toLowerCase(Locale.ROOT);
        for (String token : body.split("[^\\p{L}\\p{N}+#]+")) {
            if (token.length() > 2) {
                v[Math.floorMod(token.hashCode(), 768)] += 1f;
            }
        }
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }
}
