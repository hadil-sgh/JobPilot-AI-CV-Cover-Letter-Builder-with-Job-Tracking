package com.jobpilot.generation;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileItem;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;
import com.jobpilot.rag.Chunk;

/**
 * Detached copy of the profile taken in a short read transaction, so the multi-minute LLM
 * pipeline never holds a DB session or touches lazy collections.
 */
public record ProfileSnapshot(
        UUID profileId,
        String fullName,
        String email,
        String headline,
        String summary,
        String phone,
        String location,
        List<ProfileLink> links,
        List<ProfileLanguage> languages,
        List<Item> items,
        Map<UUID, String> chunkTextByItem,
        String allText) {

    public record Item(UUID id, ItemType type, String title, String organization, LocalDate start, LocalDate end,
                       String description, List<String> bullets, List<String> tags, int sortOrder) {
    }

    public static ProfileSnapshot of(Profile p, String fullName, String email, List<Chunk> chunks) {
        List<Item> items = p.getItems().stream()
                .sorted(Comparator.comparing(ProfileItem::getType).thenComparingInt(ProfileItem::getSortOrder))
                .map(i -> new Item(i.getId(), i.getType(), i.getTitle(), i.getOrganization(), i.getStartDate(),
                        i.getEndDate(), i.getDescription(), List.copyOf(i.getBullets()), List.copyOf(i.getTags()),
                        i.getSortOrder()))
                .toList();
        Map<UUID, String> byItem = chunks.stream().filter(c -> c.itemId() != null)
                .collect(Collectors.toMap(Chunk::itemId, Chunk::content, (a, b) -> a));
        String all = chunks.stream().map(Chunk::content).collect(Collectors.joining("\n"))
                + "\n" + (fullName == null ? "" : fullName);
        return new ProfileSnapshot(p.getId(), fullName, email, p.getHeadline(), p.getSummary(), p.getPhone(),
                p.getLocation(), List.copyOf(p.getLinks()), List.copyOf(p.getLanguages()), items, byItem, all);
    }

    public List<Item> itemsOf(ItemType type) {
        return items.stream().filter(i -> i.type() == type).toList();
    }

    public boolean isEmpty() {
        return items.isEmpty() && (summary == null || summary.isBlank());
    }
}
