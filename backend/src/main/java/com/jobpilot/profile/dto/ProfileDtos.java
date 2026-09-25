package com.jobpilot.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;

/** Request/response bodies for /api/profile. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileDto(
            UUID id,
            String fullName,
            String email,
            String headline,
            String summary,
            String phone,
            String location,
            List<ProfileLink> links,
            List<ProfileLanguage> languages,
            boolean hasOriginalFile,
            Instant updatedAt,
            List<ItemDto> items) {
    }

    public record ItemDto(
            UUID id,
            ItemType type,
            String title,
            String organization,
            LocalDate startDate,
            LocalDate endDate,
            String description,
            List<String> bullets,
            List<String> tags,
            int sortOrder) {
    }

    public record ProfileUpdateRequest(
            @Size(max = 255) String fullName,
            @Size(max = 255) String headline,
            @Size(max = 5000) String summary,
            @Size(max = 50) String phone,
            @Size(max = 255) String location,
            @Size(max = 20) List<@Valid @NotNull ProfileLink> links,
            @Size(max = 20) List<@Valid @NotNull ProfileLanguage> languages) {
    }

    public record ItemRequest(
            @NotNull ItemType type,
            @Size(max = 255) String title,
            @Size(max = 255) String organization,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 5000) String description,
            @Size(max = 15) List<@NotBlank @Size(max = 500) String> bullets,
            @Size(max = 60) List<@NotBlank @Size(max = 100) String> tags) {

        @AssertTrue(message = "end date must not be before start date")
        public boolean isDateRangeValid() {
            return startDate == null || endDate == null || !endDate.isBefore(startDate);
        }
    }

    public record ReorderRequest(@NotNull ItemType type, @NotEmpty @Size(max = 200) List<@NotNull UUID> ids) {
    }
}
