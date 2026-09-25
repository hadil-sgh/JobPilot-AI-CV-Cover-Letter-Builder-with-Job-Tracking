package com.jobpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags (application.yml {@code features.*}). Half-built features stay off until ready.
 */
@ConfigurationProperties(prefix = "features")
public record FeatureProperties(boolean atsCheck, boolean stats, boolean notion, boolean email) {
}
