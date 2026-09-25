package com.jobpilot.profile;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.auth.User;
import com.jobpilot.auth.UserRepository;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.profile.cv.CvDraft;
import com.jobpilot.profile.cv.CvDraftMapper;
import com.jobpilot.profile.dto.ProfileDtos.ItemDto;
import com.jobpilot.profile.dto.ProfileDtos.ItemRequest;
import com.jobpilot.profile.dto.ProfileDtos.ProfileDto;
import com.jobpilot.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.jobpilot.profile.dto.ProfileDtos.ReorderRequest;

/** Master profile CRUD. Every operation is scoped to the calling user's own profile. */
@Service
@Transactional
public class ProfileService {

    private final ProfileRepository profiles;
    private final ProfileItemRepository items;
    private final UserRepository users;
    private final CvDraftMapper draftMapper;
    private final ApplicationEventPublisher events;

    public ProfileService(ProfileRepository profiles, ProfileItemRepository items, UserRepository users,
                          CvDraftMapper draftMapper, ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.items = items;
        this.users = users;
        this.draftMapper = draftMapper;
        this.events = events;
    }

    public ProfileDto get(UUID userId) {
        return toDto(getOrCreate(userId));
    }

    public ProfileDto update(UUID userId, ProfileUpdateRequest req) {
        Profile profile = getOrCreate(userId);
        profile.setHeadline(blankToNull(req.headline()));
        profile.setSummary(blankToNull(req.summary()));
        profile.setPhone(blankToNull(req.phone()));
        profile.setLocation(blankToNull(req.location()));
        profile.setLinks(req.links());
        profile.setLanguages(req.languages());
        user(userId).setFullName(blankToNull(req.fullName()));
        return changed(profile);
    }

    public ItemDto addItem(UUID userId, ItemRequest req) {
        Profile profile = getOrCreate(userId);
        ProfileItem item = new ProfileItem(req.type());
        copy(req, item);
        item.setSortOrder(profile.getItems().stream()
                .filter(i -> i.getType() == req.type())
                .mapToInt(ProfileItem::getSortOrder)
                .max().orElse(-1) + 1);
        profile.addItem(item);
        items.saveAndFlush(item);
        changed(profile);
        return toDto(item);
    }

    public ItemDto updateItem(UUID userId, UUID itemId, ItemRequest req) {
        ProfileItem item = owned(userId, itemId);
        if (item.getType() != req.type()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An item's type cannot be changed");
        }
        copy(req, item);
        changed(item.getProfile());
        return toDto(item);
    }

    public void deleteItem(UUID userId, UUID itemId) {
        ProfileItem item = owned(userId, itemId);
        Profile profile = item.getProfile();
        profile.getItems().remove(item);
        changed(profile);
    }

    /** Sets sort_order of one section from the given id order. The ids must be exactly that section. */
    public ProfileDto reorder(UUID userId, ReorderRequest req) {
        Profile profile = getOrCreate(userId);
        Map<UUID, ProfileItem> section = new HashMap<>();
        profile.getItems().stream().filter(i -> i.getType() == req.type()).forEach(i -> section.put(i.getId(), i));
        if (section.size() != req.ids().size() || !section.keySet().containsAll(req.ids())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The order must list every item of the section exactly once");
        }
        for (int i = 0; i < req.ids().size(); i++) {
            section.get(req.ids().get(i)).setSortOrder(i);
        }
        return changed(profile);
    }

    /** Replaces the whole profile content with an imported CV. */
    public ProfileDto replaceWithDraft(UUID userId, CvDraft draft, String storedFilePath) {
        Profile profile = getOrCreate(userId);
        String fullName = draftMapper.apply(draft, profile);
        profile.setOriginalFilePath(storedFilePath);
        User user = user(userId);
        if (fullName != null && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(fullName);
        }
        return changed(profile);
    }

    @Transactional(readOnly = true)
    public String originalFilePath(UUID userId) {
        return profiles.findByUserId(userId).map(Profile::getOriginalFilePath).orElse(null);
    }

    private ProfileDto changed(Profile profile) {
        profile.markUpdated();
        profiles.saveAndFlush(profile);
        events.publishEvent(new ProfileChangedEvent(profile.getId(), profile.getUserId()));
        return toDto(profile);
    }

    private Profile getOrCreate(UUID userId) {
        return profiles.findByUserId(userId).orElseGet(() -> profiles.saveAndFlush(new Profile(userId)));
    }

    private ProfileItem owned(UUID userId, UUID itemId) {
        return items.findOwned(itemId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private static void copy(ItemRequest req, ProfileItem item) {
        item.setTitle(blankToNull(req.title()));
        item.setOrganization(blankToNull(req.organization()));
        item.setStartDate(req.startDate());
        item.setEndDate(req.endDate());
        item.setDescription(blankToNull(req.description()));
        item.setBullets(trimAll(req.bullets()));
        item.setTags(trimAll(req.tags()));
    }

    private ProfileDto toDto(Profile p) {
        User user = user(p.getUserId());
        List<ItemDto> itemDtos = p.getItems().stream()
                .sorted(Comparator.comparing(ProfileItem::getType).thenComparingInt(ProfileItem::getSortOrder))
                .map(ProfileService::toDto)
                .toList();
        return new ProfileDto(p.getId(), user.getFullName(), user.getEmail(), p.getHeadline(), p.getSummary(),
                p.getPhone(), p.getLocation(), List.copyOf(p.getLinks()), List.copyOf(p.getLanguages()),
                p.getOriginalFilePath() != null, p.getUpdatedAt(), itemDtos);
    }

    private static ItemDto toDto(ProfileItem i) {
        return new ItemDto(i.getId(), i.getType(), i.getTitle(), i.getOrganization(), i.getStartDate(),
                i.getEndDate(), i.getDescription(), List.copyOf(i.getBullets()), List.copyOf(i.getTags()),
                i.getSortOrder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static List<String> trimAll(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
