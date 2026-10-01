package com.jobpilot.profile.cv;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw structured CV as returned by the LLM (prompt A). Untrusted: every value is sanitized by
 * {@link CvDraftMapper} before it reaches the database. Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvDraft(
        String fullName,
        String headline,
        String summary,
        Contact contact,
        List<Experience> experience,
        List<Education> education,
        List<Project> projects,
        List<SkillGroup> skills,
        List<Language> languages,
        List<Certification> certifications) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(String email, String phone, String location, List<Link> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(String label, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(String title, String org, String location, String start, String end,
                             String description, List<String> bullets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(String degree, String school, String start, String end, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String name, String description, List<String> bullets, List<String> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillGroup(String group, List<String> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(String name, String level) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certification(String name, String issuer, String date) {
    }
}
