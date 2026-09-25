package com.jobpilot.auth;

import java.util.UUID;

/** Principal placed in the SecurityContext for authenticated requests. Use with @AuthenticationPrincipal. */
public record AuthUser(UUID id) {
}
