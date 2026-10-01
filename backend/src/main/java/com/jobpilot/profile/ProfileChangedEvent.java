package com.jobpilot.profile;

import java.util.UUID;

/**
 * Published whenever a profile or its items change. Phase 3 listens to it (after commit) to
 * re-chunk and re-embed the profile.
 */
public record ProfileChangedEvent(UUID profileId, UUID userId) {
}
