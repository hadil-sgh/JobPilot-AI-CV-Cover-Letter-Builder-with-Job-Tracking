package com.jobpilot.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {

    Optional<JobDescription> findByIdAndUserId(UUID id, UUID userId);

    List<JobDescription> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
