package com.jobpilot.generation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    @Query("select j from GenerationJob j where j.id = :id and j.applicationId in "
            + "(select a.id from Application a where a.userId = :userId)")
    Optional<GenerationJob> findOwned(UUID id, UUID userId);

    Optional<GenerationJob> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    boolean existsByApplicationIdAndStatusIn(UUID applicationId, Collection<GenerationJob.Status> statuses);

    List<GenerationJob> findByStatusIn(Collection<GenerationJob.Status> statuses);

    @Modifying
    @Query("update GenerationJob j set j.status = com.jobpilot.generation.GenerationJob.Status.FAILED, "
            + "j.error = :error, j.finishedAt = CURRENT_TIMESTAMP where j.status in :statuses")
    int failAll(Collection<GenerationJob.Status> statuses, String error);
}
