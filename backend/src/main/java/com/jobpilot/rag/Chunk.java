package com.jobpilot.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * One retrievable piece of the profile (PROJECT.md 3.3).
 *
 * @param itemId   source profile_items row, or null for the summary / spoken-languages chunks
 * @param metadata {type, item_id, org, start, end} — stored as JSONB next to the vector
 */
public record Chunk(UUID itemId, String content, Map<String, Object> metadata) {

    /** Identifies unchanged content so its embedding can be reused on re-index. */
    public String contentHash() {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
