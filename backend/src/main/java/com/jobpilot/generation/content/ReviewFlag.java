package com.jobpilot.generation.content;

/**
 * A validator finding the user should review (PROJECT.md 3.6 step 5), e.g.
 * {@code section = "experience:E1", message = "Mentions 'Kubernetes', which is not in your profile"}.
 */
public record ReviewFlag(String section, String message) {
}
