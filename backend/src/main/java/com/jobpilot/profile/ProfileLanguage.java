package com.jobpilot.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A spoken language and level, e.g. {"name": "French", "level": "Native"}. Stored in profiles.languages. */
public record ProfileLanguage(@NotBlank @Size(max = 60) String name, @Size(max = 60) String level) {
}
