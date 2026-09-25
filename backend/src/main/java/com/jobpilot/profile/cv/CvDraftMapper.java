package com.jobpilot.profile.cv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileItem;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;

/**
 * Copies an untrusted {@link CvDraft} onto a profile. Every string is cleaned (control chars
 * removed, whitespace collapsed, length capped); lists are capped; URLs must be http(s).
 */
@Component
public class CvDraftMapper {

    static final int MAX_ITEMS_PER_TYPE = 30;
    static final int MAX_BULLETS = 15;
    static final int MAX_TAGS = 60;

    private static final Pattern URL = Pattern.compile("^https?://[^\\s<>\"]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_DOMAIN =
            Pattern.compile("^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    /** Replaces the profile's content with the draft. Returns the cleaned full name, if any. */
    public String apply(CvDraft draft, Profile profile) {
        CvDraft.Contact contact = draft.contact();
        profile.setHeadline(clean(draft.headline(), 255));
        profile.setSummary(clean(draft.summary(), 5000));
        profile.setPhone(contact == null ? null : clean(contact.phone(), 50));
        profile.setLocation(contact == null ? null : clean(contact.location(), 255));
        profile.setLinks(contact == null ? List.of() : links(contact.links()));
        profile.setLanguages(cap(draft.languages(), 20).stream()
                .map(l -> {
                    String name = clean(l.name(), 60);
                    return name == null ? null : new ProfileLanguage(name, clean(l.level(), 60));
                })
                .filter(Objects::nonNull)
                .toList());

        profile.clearItems();
        addAll(profile, ItemType.EXPERIENCE, draft.experience(), e -> {
            ProfileItem item = new ProfileItem(ItemType.EXPERIENCE);
            item.setTitle(clean(e.title(), 255));
            item.setOrganization(clean(e.org(), 255));
            item.setStartDate(CvDates.parse(e.start()).orElse(null));
            item.setEndDate(CvDates.parse(e.end()).orElse(null));
            item.setDescription(clean(e.description(), 5000));
            item.setBullets(strings(e.bullets(), MAX_BULLETS, 500));
            return hasText(item.getTitle(), item.getOrganization()) ? item : null;
        });
        addAll(profile, ItemType.EDUCATION, draft.education(), e -> {
            ProfileItem item = new ProfileItem(ItemType.EDUCATION);
            item.setTitle(clean(e.degree(), 255));
            item.setOrganization(clean(e.school(), 255));
            item.setStartDate(CvDates.parse(e.start()).orElse(null));
            item.setEndDate(CvDates.parse(e.end()).orElse(null));
            item.setDescription(clean(e.description(), 5000));
            return hasText(item.getTitle(), item.getOrganization()) ? item : null;
        });
        addAll(profile, ItemType.PROJECT, draft.projects(), p -> {
            ProfileItem item = new ProfileItem(ItemType.PROJECT);
            item.setTitle(clean(p.name(), 255));
            item.setDescription(clean(p.description(), 5000));
            item.setBullets(strings(p.bullets(), MAX_BULLETS, 500));
            item.setTags(strings(p.tags(), MAX_TAGS, 100));
            return hasText(item.getTitle()) ? item : null;
        });
        addAll(profile, ItemType.SKILL, draft.skills(), s -> {
            ProfileItem item = new ProfileItem(ItemType.SKILL);
            String group = clean(s.group(), 255);
            item.setTitle(group == null ? "Skills" : group);
            item.setTags(strings(s.items(), MAX_TAGS, 100));
            return item.getTags().isEmpty() ? null : item;
        });
        addAll(profile, ItemType.CERTIFICATION, draft.certifications(), c -> {
            ProfileItem item = new ProfileItem(ItemType.CERTIFICATION);
            item.setTitle(clean(c.name(), 255));
            item.setOrganization(clean(c.issuer(), 255));
            item.setStartDate(CvDates.parse(c.date()).orElse(null));
            return hasText(item.getTitle()) ? item : null;
        });
        profile.markUpdated();
        return clean(draft.fullName(), 255);
    }

    private static <T> void addAll(Profile profile, ItemType type, List<T> source, Function<T, ProfileItem> toItem) {
        int order = 0;
        for (T entry : cap(source, MAX_ITEMS_PER_TYPE)) {
            ProfileItem item = entry == null ? null : toItem.apply(entry);
            if (item != null) {
                item.setSortOrder(order++);
                profile.addItem(item);
            }
        }
    }

    static List<ProfileLink> links(List<CvDraft.Link> links) {
        List<ProfileLink> out = new ArrayList<>();
        for (CvDraft.Link link : cap(links, 10)) {
            if (link == null) {
                continue;
            }
            String url = clean(link.url(), 500);
            if (url == null) {
                continue;
            }
            if (!URL.matcher(url).matches() && BARE_DOMAIN.matcher(url).matches()) {
                url = "https://" + url;
            }
            if (URL.matcher(url).matches()) {
                out.add(new ProfileLink(clean(link.label(), 50), url));
            }
        }
        return out;
    }

    /** Removes control characters, collapses whitespace, trims and caps length; blank becomes null. */
    public static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String s = value.replaceAll("[\\p{Cntrl}&&[^\n]]", " ")
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll(" *\n+ *", "\n")
                .strip();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) {
            return null;
        }
        return s.length() > max ? s.substring(0, max).strip() : s;
    }

    static List<String> strings(List<String> values, int maxCount, int maxLength) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String v : cap(values, maxCount * 2)) {
            String c = clean(v, maxLength);
            if (c != null) {
                out.add(c.replace('\n', ' '));
            }
            if (out.size() == maxCount) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    private static <T> List<T> cap(List<T> list, int max) {
        if (list == null) {
            return List.of();
        }
        return list.size() > max ? list.subList(0, max) : list;
    }

    private static boolean hasText(String... values) {
        for (String v : values) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }
}
