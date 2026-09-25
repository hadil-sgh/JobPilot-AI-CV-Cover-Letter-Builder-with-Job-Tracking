package com.jobpilot.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A link on the profile (LinkedIn, GitHub, portfolio...). Stored in profiles.links (JSONB). */
public record ProfileLink(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String url) {
}
