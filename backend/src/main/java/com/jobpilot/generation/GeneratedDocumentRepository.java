package com.jobpilot.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    /** Loads a document only if its application belongs to the user. */
    @Query("select d from GeneratedDocument d where d.id = :id and d.applicationId in "
            + "(select a.id from Application a where a.userId = :userId)")
    Optional<GeneratedDocument> findOwned(UUID id, UUID userId);

    List<GeneratedDocument> findByApplicationIdOrderByTypeAscVersionDesc(UUID applicationId);

    @Query("select coalesce(max(d.version), 0) from GeneratedDocument d where d.applicationId = :applicationId and d.type = :type")
    int maxVersion(UUID applicationId, DocumentType type);
}
