package com.jobpilot.profile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileItemRepository extends JpaRepository<ProfileItem, UUID> {

    /** Loads an item only if it belongs to the given user's profile (ownership check in one query). */
    @Query("select i from ProfileItem i join fetch i.profile p where i.id = :itemId and p.userId = :userId")
    Optional<ProfileItem> findOwned(UUID itemId, UUID userId);
}
