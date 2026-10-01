package com.jobpilot.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    List<Application> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
