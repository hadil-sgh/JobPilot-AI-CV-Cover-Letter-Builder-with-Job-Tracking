package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jobpilot.common.text.TextClean;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.CvContent.Entry;
import com.jobpilot.generation.content.CvContent.SkillGroup;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.content.ReviewFlag;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.llm.GenerationModels.RefBullets;
import com.jobpilot.generation.llm.GenerationModels.SkillGroupOut;
import com.jobpilot.profile.ItemType;

/**
 * Builds the stored documents from the (untrusted) LLM drafts. Facts — name, contact, titles,
 * organisations, dates, education, certifications, spoken languages — come from the profile
 * snapshot via the evidence refs; the LLM only contributes summary, bullets, project selection and
 * skill ordering. Unknown refs are ignored and reported; skills not in the profile are dropped.
 */
@Component
public class ContentAssembler {

    static final int MAX_BULLETS = 5;
    static final int MAX_PROJECTS = 3;

    public CvContent assembleCv(GenerationContext ctx, EvidencePack pack, CvDraftOut draft, CvContent.Match match) {
        ProfileSnapshot p = ctx.profile();
        Map<String, EvidencePack.Item> byRef = pack.byRef();
        Map<UUID, ProfileSnapshot.Item> items = new HashMap<>();
        p.items().forEach(i -> items.put(i.id(), i));
        List<ReviewFlag> flags = new ArrayList<>();

        // Experience: LLM order first, then any experience it left out (never silently dropped).
        List<Entry> experience = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (RefBullets rb : nullSafe(draft.experience())) {
            EvidencePack.Item ev = rb == null ? null : byRef.get(normalizeRef(rb.ref()));
            if (ev == null || ev.type() != ItemType.EXPERIENCE) {
                if (rb != null && rb.ref() != null) {
                    flags.add(new ReviewFlag("experience", "The AI referred to an unknown entry \"" + rb.ref() + "\"; it was ignored"));
                }
                continue;
            }
            if (used.add(ev.ref())) {
                experience.add(entry(ev.ref(), items.get(ev.itemId()), rb.bullets()));
            }
        }
        pack.ofType(ItemType.EXPERIENCE).stream()
                .filter(ev -> !used.contains(ev.ref()))
                .map(ev -> entry(ev.ref(), items.get(ev.itemId()), null))
                .sorted(Comparator.comparing(Entry::start, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(experience::add);

        // Projects: the LLM's selection (known refs only); fallback to the most relevant ones.
        List<Entry> projects = new ArrayList<>();
        Set<String> usedProjects = new LinkedHashSet<>();
        for (RefBullets rb : nullSafe(draft.projects())) {
            EvidencePack.Item ev = rb == null ? null : byRef.get(normalizeRef(rb.ref()));
            if (ev != null && ev.type() == ItemType.PROJECT && usedProjects.add(ev.ref())
                    && projects.size() < MAX_PROJECTS) {
                projects.add(entry(ev.ref(), items.get(ev.itemId()), rb.bullets()));
            }
        }
        if (projects.isEmpty()) {
            pack.ofType(ItemType.PROJECT).stream()
                    .sorted(Comparator.comparingDouble(EvidencePack.Item::relevance).reversed())
                    .limit(2)
                    .forEach(ev -> projects.add(entry(ev.ref(), items.get(ev.itemId()), null)));
        }

        List<Entry> education = refsOf(pack, ItemType.EDUCATION, items);
        List<Entry> certifications = refsOf(pack, ItemType.CERTIFICATION, items);

        CvContent.Header header = new CvContent.Header(p.fullName(), p.headline(), p.email(), p.phone(), p.location(),
                p.links());
        String summary = TextClean.clean(draft.summary(), 1200);
        if (summary == null) {
            summary = TextClean.clean(p.summary(), 1200);
        }
        return new CvContent(ctx.language(), header, summary, experience, education, projects,
                skills(draft.skills(), p), p.languages(), certifications, match, flags);
    }

    public LetterContent assembleLetter(GenerationContext ctx, LetterDraftOut draft) {
        List<String> paragraphs = new ArrayList<>();
        for (String para : nullSafe(draft.paragraphs())) {
            String c = TextClean.clean(para, 1500);
            if (c != null) {
                paragraphs.add(c);
            }
        }
        String greeting = TextClean.clean(draft.greeting(), 200);
        String closing = TextClean.clean(draft.closing(), 300);
        boolean fr = "fr".equals(ctx.language());
        return new LetterContent(ctx.language(), ctx.company(), ctx.roleTitle(),
                greeting == null ? (fr ? "Madame, Monsieur," : "Dear Hiring Manager,") : greeting,
                paragraphs.size() > 6 ? paragraphs.subList(0, 6) : paragraphs,
                closing == null ? (fr ? "Je vous prie d'agréer mes salutations distinguées." : "Kind regards,") : closing,
                ctx.profile().fullName(), List.of());
    }

    /** Skill groups from the LLM, keeping only skills that exist in the profile; fallback to the profile's own groups. */
    static List<SkillGroup> skills(List<SkillGroupOut> fromLlm, ProfileSnapshot p) {
        Map<String, String> profileSkills = new HashMap<>(); // lower-case → original spelling
        p.itemsOf(ItemType.SKILL).forEach(i -> i.tags().forEach(t -> profileSkills.put(t.toLowerCase(Locale.ROOT), t)));

        List<SkillGroup> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SkillGroupOut g : nullSafe(fromLlm)) {
            if (g == null) {
                continue;
            }
            List<String> kept = new ArrayList<>();
            for (String s : nullSafe(g.items())) {
                String original = s == null ? null : profileSkills.get(s.strip().toLowerCase(Locale.ROOT));
                if (original != null && seen.add(original.toLowerCase(Locale.ROOT))) {
                    kept.add(original);
                }
            }
            if (!kept.isEmpty()) {
                String group = TextClean.clean(g.group(), 60);
                out.add(new SkillGroup(group == null ? "Skills" : group, kept));
            }
        }
        // Skills the LLM forgot are appended to their original groups: tailoring reorders, it does not remove.
        for (ProfileSnapshot.Item i : p.itemsOf(ItemType.SKILL)) {
            List<String> missing = i.tags().stream().filter(t -> seen.add(t.toLowerCase(Locale.ROOT))).toList();
            if (!missing.isEmpty()) {
                out.add(new SkillGroup(i.title() == null ? "Skills" : i.title(), missing));
            }
        }
        return out;
    }

    private static List<Entry> refsOf(EvidencePack pack, ItemType type, Map<UUID, ProfileSnapshot.Item> items) {
        return pack.ofType(type).stream().map(ev -> entry(ev.ref(), items.get(ev.itemId()), null)).toList();
    }

    /** Facts from the profile item; bullets from the LLM when given, otherwise the item's own. */
    static Entry entry(String ref, ProfileSnapshot.Item item, List<String> llmBullets) {
        List<String> bullets = TextClean.cleanList(llmBullets, MAX_BULLETS, 300);
        if (bullets.isEmpty()) {
            bullets = item.bullets().size() > MAX_BULLETS ? item.bullets().subList(0, MAX_BULLETS) : item.bullets();
        }
        return new Entry(ref, item.id(), item.title(), item.organization(), item.start(), item.end(),
                item.description(), bullets, item.tags());
    }

    /** "[E1]", "e1 ", "E-1" → "E1". */
    static String normalizeRef(String ref) {
        return ref == null ? null : ref.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
