package com.jobpilot.generation.content;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;

/**
 * Structured CV stored in generated_documents.content_json (editable in the UI, rendered by the
 * LaTeX templates in Phase 5). Facts (header, organisations, titles, dates, education,
 * certifications, languages) are copied from the profile, never written by the LLM.
 * New content = new field here + template block (PROJECT.md 3.9).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvContent(
        String language,
        Header header,
        String summary,
        List<Entry> experience,
        List<Entry> education,
        List<Entry> projects,
        List<SkillGroup> skills,
        List<ProfileLanguage> languages,
        List<Entry> certifications,
        Match match,
        List<ReviewFlag> review) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String fullName, String headline, String email, String phone, String location,
                         List<ProfileLink> links) {
    }

    /** {@code ref} is the evidence reference (E1, P2, D1, C1) linking back to a profile item. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ref, UUID itemId, String title, String organization, LocalDate start, LocalDate end,
                        String description, List<String> bullets, List<String> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillGroup(String group, List<String> items) {
    }

    /** Match score (0–100) and skill gaps — shown in the editor, not printed on the CV. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(int score, List<String> gaps, List<RequirementMatch> requirements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementMatch(String requirement, boolean mustHave, String verdict, double evidenceScore,
                                   List<String> refs) {
    }

    public CvContent withSummary(String s) {
        return new CvContent(language, header, s, experience, education, projects, skills, languages, certifications,
                match, review);
    }

    public CvContent withExperience(List<Entry> e) {
        return new CvContent(language, header, summary, e, education, projects, skills, languages, certifications,
                match, review);
    }

    public CvContent withProjects(List<Entry> p) {
        return new CvContent(language, header, summary, experience, education, p, skills, languages, certifications,
                match, review);
    }

    public CvContent withSkills(List<SkillGroup> s) {
        return new CvContent(language, header, summary, experience, education, projects, s, languages, certifications,
                match, review);
    }

    public CvContent withMatch(Match m) {
        return new CvContent(language, header, summary, experience, education, projects, skills, languages,
                certifications, m, review);
    }

    public CvContent withReview(List<ReviewFlag> r) {
        return new CvContent(language, header, summary, experience, education, projects, skills, languages,
                certifications, match, r);
    }
}
