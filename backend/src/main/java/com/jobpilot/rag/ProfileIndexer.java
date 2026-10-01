package com.jobpilot.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobpilot.common.config.AsyncConfig;
import com.jobpilot.profile.ProfileChangedEvent;
import com.jobpilot.profile.ProfileRepository;
import com.jobpilot.rag.ProfileChunkRepository.StoredChunk;

/**
 * Keeps profile_chunks in sync with the profile (PROJECT.md 3.3 "re-ingest on edit").
 * Runs after the profile transaction commits, on a single background thread (so re-indexes of
 * the same profile never interleave). Chunks whose text is unchanged keep their old embedding.
 */
@Service
public class ProfileIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProfileIndexer.class);

    private final ProfileRepository profiles;
    private final ProfileChunker chunker;
    private final EmbeddingService embeddings;
    private final ProfileChunkRepository chunks;
    private final TransactionTemplate readOnlyTx;

    public ProfileIndexer(ProfileRepository profiles, ProfileChunker chunker, EmbeddingService embeddings,
                          ProfileChunkRepository chunks, TransactionTemplate transactionTemplate) {
        this.profiles = profiles;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.readOnlyTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.readOnlyTx.setReadOnly(true);
    }

    @Async(AsyncConfig.INDEXER)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProfileChanged(ProfileChangedEvent event) {
        try {
            reindex(event.profileId());
        } catch (RuntimeException e) {
            // The profile edit already succeeded; retrieval uses the previous index until the next change
            // or a manual POST /api/profile/reindex.
            log.warn("Re-indexing profile {} failed: {}", event.profileId(), e.getMessage());
        }
    }

    /** Rebuilds the profile's chunks and returns how many were stored. */
    public int reindex(UUID profileId) {
        List<Chunk> fresh = readOnlyTx.execute(tx -> profiles.findById(profileId).map(chunker::chunk).orElse(List.of()));
        if (fresh == null) {
            fresh = List.of();
        }

        Map<String, float[]> known = chunks.embeddingsByHash(profileId);
        List<String> toEmbed = new ArrayList<>();
        for (Chunk c : fresh) {
            if (!known.containsKey(c.contentHash())) {
                toEmbed.add(c.content());
            }
        }
        List<float[]> newVectors = embeddings.embedDocuments(toEmbed);
        Map<String, float[]> byContent = new HashMap<>();
        for (int i = 0; i < toEmbed.size(); i++) {
            byContent.put(toEmbed.get(i), newVectors.get(i));
        }

        List<StoredChunk> stored = new ArrayList<>();
        for (Chunk c : fresh) {
            String hash = c.contentHash();
            float[] vector = known.containsKey(hash) ? known.get(hash) : byContent.get(c.content());
            Map<String, Object> meta = new HashMap<>(c.metadata());
            meta.put("content_hash", hash);
            stored.add(new StoredChunk(c.itemId(), c.content(), meta, vector));
        }
        chunks.replaceAll(profileId, stored);
        log.info("Indexed profile {}: {} chunks ({} newly embedded)", profileId, stored.size(), toEmbed.size());
        return stored.size();
    }
}
