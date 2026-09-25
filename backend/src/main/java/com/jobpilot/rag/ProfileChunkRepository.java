package com.jobpilot.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * pgvector access for profile_chunks with plain, parameterised SQL (no string-built filters).
 * Similarity = 1 - cosine distance ({@code <=>}), matching the HNSW vector_cosine_ops index.
 */
@Repository
public class ProfileChunkRepository {

    public record StoredChunk(UUID itemId, String content, Map<String, Object> metadata, float[] embedding) {
    }

    public record Hit(UUID chunkId, UUID itemId, String content, Map<String, Object> metadata, double similarity) {
    }

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProfileChunkRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Existing embeddings keyed by content hash, so unchanged chunks are not re-embedded. */
    public Map<String, float[]> embeddingsByHash(UUID profileId) {
        Map<String, float[]> out = new HashMap<>();
        jdbc.query("select metadata->>'content_hash', embedding::text from profile_chunks where profile_id = ?",
                rs -> {
                    String hash = rs.getString(1);
                    if (hash != null) {
                        out.put(hash, parseVector(rs.getString(2)));
                    }
                }, profileId);
        return out;
    }

    /**
     * Atomically swaps the profile's chunks for a new set. A transaction-scoped advisory lock per
     * profile serialises concurrent swaps (automatic re-index vs manual /reindex, or several backend
     * instances); without it both could delete-then-insert and leave duplicate chunks.
     */
    @Transactional
    public void replaceAll(UUID profileId, List<StoredChunk> chunks) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, profileId.toString());
        jdbc.update("delete from profile_chunks where profile_id = ?", profileId);
        jdbc.batchUpdate(
                "insert into profile_chunks (profile_id, item_id, content, metadata, embedding) "
                        + "values (?, ?, ?, ?::jsonb, ?::vector)",
                chunks, 100, (ps, c) -> {
                    ps.setObject(1, profileId);
                    ps.setObject(2, c.itemId());
                    ps.setString(3, c.content());
                    ps.setString(4, toJson(c.metadata()));
                    ps.setString(5, toVector(c.embedding()));
                });
    }

    public List<Hit> search(UUID profileId, float[] query, int topK, double minSimilarity) {
        String vector = toVector(query);
        return jdbc.query(
                "select id, item_id, content, metadata::text, 1 - (embedding <=> ?::vector) as similarity "
                        + "from profile_chunks where profile_id = ? "
                        + "order by embedding <=> ?::vector limit ?",
                (rs, n) -> new Hit(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        fromJson(rs.getString(4)), rs.getDouble(5)),
                vector, profileId, vector, topK)
                .stream().filter(h -> h.similarity() >= minSimilarity).toList();
    }

    public int count(UUID profileId) {
        Integer n = jdbc.queryForObject("select count(*) from profile_chunks where profile_id = ?", Integer.class, profileId);
        return n == null ? 0 : n;
    }

    static String toVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    static float[] parseVector(String text) {
        String body = text.substring(1, text.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i]);
        }
        return v;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> fromJson(String text) {
        try {
            return text == null ? Map.of() : json.readValue(text, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
