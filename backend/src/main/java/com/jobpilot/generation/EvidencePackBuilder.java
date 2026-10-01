package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.jobpilot.job.JobAnalysis;
import com.jobpilot.profile.ItemType;
import com.jobpilot.rag.RetrievalService.Evidence;

/**
 * Builds the evidence pack (PROJECT.md 3.2): retrieval per requirement → relevance per profile
 * item → refs. Experience and education are always included (a tailored CV reorders and rephrases,
 * it does not silently drop real jobs); other items are added by relevance within a character
 * budget that keeps prompt + output inside the model's 8k context.
 */
@Component
public class EvidencePackBuilder {

    static final int BUDGET_CHARS = 9_000;

    public EvidencePack build(ProfileSnapshot profile, JobAnalysis analysis,
                              Function<String, List<Evidence>> retrieve) {
        Map<UUID, Double> relevance = new HashMap<>();
        Map<UUID, Set<String>> supports = new HashMap<>();
        List<Pending> pendingRequirements = new ArrayList<>();

        List<String> must = analysis.requirements() == null ? List.of() : analysis.requirements();
        List<String> nice = analysis.niceToHave() == null ? List.of() : analysis.niceToHave();
        for (int i = 0; i < must.size() + nice.size(); i++) {
            boolean isMust = i < must.size();
            String req = isMust ? must.get(i) : nice.get(i - must.size());
            List<Evidence> hits = retrieve.apply(req);
            List<UUID> hitItems = new ArrayList<>();
            for (Evidence e : hits) {
                UUID itemId = e.hit().itemId();
                if (itemId == null) {
                    continue; // summary / languages chunks: header facts, not citable items
                }
                relevance.merge(itemId, e.score(), Math::max);
                supports.computeIfAbsent(itemId, k -> new LinkedHashSet<>()).add(shorten(req));
                hitItems.add(itemId);
            }
            double best = hits.isEmpty() ? -1 : hits.get(0).score();
            pendingRequirements.add(new Pending(req, isMust, best, hitItems));
        }

        // Assign refs per type in profile order, then select by budget.
        Map<ItemType, Integer> counters = new EnumMap<>(ItemType.class);
        List<EvidencePack.Item> all = new ArrayList<>();
        for (ProfileSnapshot.Item item : profile.items()) {
            String text = profile.chunkTextByItem().get(item.id());
            if (text == null) {
                continue; // e.g. a skill group without skills
            }
            int n = counters.merge(item.type(), 1, Integer::sum);
            all.add(new EvidencePack.Item(EvidencePack.prefix(item.type()) + n, item.id(), item.type(), text,
                    relevance.getOrDefault(item.id(), 0.0),
                    List.copyOf(supports.getOrDefault(item.id(), Set.of()))));
        }

        Set<UUID> selected = new LinkedHashSet<>();
        int used = 0;
        for (EvidencePack.Item i : all) {
            if (i.type() == ItemType.EXPERIENCE || i.type() == ItemType.EDUCATION) {
                selected.add(i.itemId());
                used += i.text().length();
            }
        }
        List<EvidencePack.Item> rest = all.stream()
                .filter(i -> !selected.contains(i.itemId()))
                .sorted(Comparator.comparingDouble(EvidencePack.Item::relevance).reversed())
                .toList();
        for (EvidencePack.Item i : rest) {
            if (used + i.text().length() <= BUDGET_CHARS || i.type() == ItemType.SKILL) {
                selected.add(i.itemId());
                used += i.text().length();
            }
        }
        List<EvidencePack.Item> items = all.stream().filter(i -> selected.contains(i.itemId())).toList();

        Map<UUID, String> refByItem = new HashMap<>();
        items.forEach(i -> refByItem.put(i.itemId(), i.ref()));
        List<EvidencePack.Requirement> requirements = pendingRequirements.stream()
                .map(p -> new EvidencePack.Requirement(p.text, p.mustHave, p.best,
                        p.items.stream().map(refByItem::get).filter(r -> r != null).distinct().toList()))
                .toList();
        return new EvidencePack(items, requirements);
    }

    private static String shorten(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private record Pending(String text, boolean mustHave, double best, List<UUID> items) {
    }
}
