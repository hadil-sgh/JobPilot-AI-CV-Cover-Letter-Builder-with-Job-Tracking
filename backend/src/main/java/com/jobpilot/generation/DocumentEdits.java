package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobpilot.common.text.TextClean;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.CvContent.Entry;
import com.jobpilot.generation.content.CvContent.SkillGroup;
import com.jobpilot.generation.content.LetterContent;

/**
 * Pure merge rules for user edits and section regeneration. What is editable: summary, bullets,
 * order of experiences, which projects are shown, skill groups, and the letter text. Facts
 * (header, titles, organisations, dates, education, certifications, languages) and the match score
 * always come from the stored document — to change a fact, edit the profile.
 */
public final class DocumentEdits {

    static final int MAX_BULLETS = 8;

    private DocumentEdits() {
    }

    public static CvContent applyUserEdits(CvContent stored, CvContent edited) {
        Map<String, Entry> storedExp = byRef(stored.experience());
        List<Entry> experience = new ArrayList<>();
        for (Entry e : nullSafe(edited.experience())) {
            Entry s = e == null ? null : storedExp.remove(e.ref());
            if (s != null) {
                experience.add(withBullets(s, e.bullets()));
            }
        }
        experience.addAll(storedExp.values()); // a real experience can be reordered, not deleted here

        Map<String, Entry> storedProj = byRef(stored.projects());
        List<Entry> projects = new ArrayList<>();
        for (Entry e : nullSafe(edited.projects())) {
            Entry s = e == null ? null : storedProj.get(e.ref());
            if (s != null && projects.stream().noneMatch(p -> p.ref().equals(s.ref()))) {
                projects.add(withBullets(s, e.bullets()));
            }
        }

        List<SkillGroup> skills = new ArrayList<>();
        for (SkillGroup g : nullSafe(edited.skills())) {
            if (g == null) {
                continue;
            }
            List<String> items = TextClean.cleanList(g.items(), 60, 100);
            if (!items.isEmpty()) {
                String group = TextClean.clean(g.group(), 60);
                skills.add(new SkillGroup(group == null ? "Skills" : group, items));
            }
        }

        return stored.withSummary(TextClean.clean(edited.summary(), 1200))
                .withExperience(experience)
                .withProjects(projects)
                .withSkills(skills);
    }

    public static LetterContent applyUserEdits(LetterContent stored, LetterContent edited) {
        List<String> paragraphs = new ArrayList<>();
        for (String p : nullSafe(edited.paragraphs())) {
            String c = TextClean.clean(p, 1500);
            if (c != null) {
                paragraphs.add(c);
            }
        }
        return new LetterContent(stored.language(), stored.company(), stored.roleTitle(),
                TextClean.clean(edited.greeting(), 200), paragraphs.size() > 8 ? paragraphs.subList(0, 8) : paragraphs,
                TextClean.clean(edited.closing(), 300), stored.signature(), stored.review());
    }

    /**
     * Copies one regenerated section into the stored CV. Sections: {@code summary}, {@code skills},
     * {@code experience:E1}, {@code projects:P1}.
     */
    public static CvContent mergeSection(CvContent stored, CvContent fresh, String section) {
        if ("summary".equals(section)) {
            return stored.withSummary(fresh.summary());
        }
        if ("skills".equals(section)) {
            return stored.withSkills(fresh.skills());
        }
        if (section.startsWith("experience:")) {
            String ref = section.substring("experience:".length());
            Entry newer = byRef(fresh.experience()).get(ref);
            return newer == null ? stored : stored.withExperience(replace(stored.experience(), newer));
        }
        if (section.startsWith("projects:")) {
            String ref = section.substring("projects:".length());
            Entry newer = byRef(fresh.projects()).get(ref);
            return newer == null ? stored : stored.withProjects(replace(stored.projects(), newer));
        }
        throw new IllegalArgumentException("Unknown section: " + section);
    }

    public static boolean isCvSection(String section) {
        return section != null && (section.equals("summary") || section.equals("skills")
                || section.matches("experience:E\\d{1,3}") || section.matches("projects:P\\d{1,3}"));
    }

    private static List<Entry> replace(List<Entry> list, Entry newer) {
        return nullSafe(list).stream().map(e -> e.ref().equals(newer.ref()) ? withBullets(e, newer.bullets()) : e).toList();
    }

    private static Entry withBullets(Entry e, List<String> bullets) {
        return new Entry(e.ref(), e.itemId(), e.title(), e.organization(), e.start(), e.end(), e.description(),
                TextClean.cleanList(bullets, MAX_BULLETS, 300), e.tags());
    }

    private static Map<String, Entry> byRef(List<Entry> entries) {
        return nullSafe(entries).stream().filter(e -> e != null && e.ref() != null)
                .collect(Collectors.toMap(Entry::ref, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
