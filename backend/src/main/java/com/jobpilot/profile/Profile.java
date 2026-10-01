package com.jobpilot.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The user's master profile (one per user). */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    private String headline;

    @Column(columnDefinition = "text")
    private String summary;

    private String phone;

    private String location;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ProfileLink> links = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ProfileLanguage> languages = new ArrayList<>();

    @Column(name = "original_file_path")
    private String originalFilePath;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("type ASC, sortOrder ASC")
    private List<ProfileItem> items = new ArrayList<>();

    protected Profile() {
    }

    public Profile(UUID userId) {
        this.userId = userId;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Marks the profile changed even when only child items were modified. */
    public void markUpdated() {
        updatedAt = Instant.now();
    }

    public void addItem(ProfileItem item) {
        item.setProfile(this);
        items.add(item);
    }

    public void clearItems() {
        items.clear();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<ProfileLink> getLinks() {
        return links;
    }

    public void setLinks(List<ProfileLink> links) {
        this.links = links == null ? new ArrayList<>() : new ArrayList<>(links);
    }

    public List<ProfileLanguage> getLanguages() {
        return languages;
    }

    public void setLanguages(List<ProfileLanguage> languages) {
        this.languages = languages == null ? new ArrayList<>() : new ArrayList<>(languages);
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ProfileItem> getItems() {
        return items;
    }
}
