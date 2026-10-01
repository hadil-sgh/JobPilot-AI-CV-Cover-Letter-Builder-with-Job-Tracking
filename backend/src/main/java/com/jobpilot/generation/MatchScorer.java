package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jobpilot.generation.content.CvContent.Match;
import com.jobpilot.generation.content.CvContent.RequirementMatch;
import com.jobpilot.generation.llm.GenerationModels.Verdict;

/**
 * Prompt E scoring (PROJECT.md 3.5 E): per requirement, combine the LLM judgment
 * (yes 1 / partial 0.5 / no 0) with retrieval strength; must-haves weigh 2, nice-to-haves 1.
 * A requirement without any retrieved evidence counts as "no" whatever the LLM says.
 * Gaps = requirements scoring below 0.4 (must-haves first).
 */
public final class MatchScorer {

    static final double VERDICT_WEIGHT = 0.6;
    static final double GAP_BELOW = 0.4;

    private MatchScorer() {
    }

    public static Match score(List<EvidencePack.Requirement> requirements, List<Verdict> verdicts) {
        List<RequirementMatch> out = new ArrayList<>();
        List<String> mustGaps = new ArrayList<>();
        List<String> niceGaps = new ArrayList<>();
        double weighted = 0;
        double weights = 0;
        for (int i = 0; i < requirements.size(); i++) {
            EvidencePack.Requirement r = requirements.get(i);
            boolean hasEvidence = r.bestScore() >= 0 && !r.refs().isEmpty();
            String verdict = hasEvidence ? verdictFor(i + 1, verdicts, r.bestScore()) : "no";
            double value = hasEvidence
                    ? VERDICT_WEIGHT * verdictValue(verdict) + (1 - VERDICT_WEIGHT) * strength(r.bestScore())
                    : 0;
            double w = r.mustHave() ? 2 : 1;
            weighted += w * value;
            weights += w;
            if (value < GAP_BELOW) {
                (r.mustHave() ? mustGaps : niceGaps).add(r.text());
            }
            out.add(new RequirementMatch(r.text(), r.mustHave(), verdict, Math.round(r.bestScore() * 1000) / 1000.0,
                    r.refs()));
        }
        int score = weights == 0 ? 0 : (int) Math.round(100 * weighted / weights);
        List<String> gaps = new ArrayList<>(mustGaps);
        gaps.addAll(niceGaps);
        return new Match(score, gaps, out);
    }

    /** Verdict by 1-based id; when the LLM skipped one, fall back on the retrieval score. */
    static String verdictFor(int id, List<Verdict> verdicts, double bestScore) {
        for (Verdict v : verdicts) {
            if (v != null && v.id() != null && v.id() == id && v.verdict() != null) {
                String s = v.verdict().strip().toLowerCase(Locale.ROOT);
                if (s.startsWith("y") || s.startsWith("o")) { // yes / oui
                    return "yes";
                }
                if (s.startsWith("p")) {
                    return "partial";
                }
                if (s.startsWith("n")) {
                    return "no";
                }
            }
        }
        return bestScore >= 0.85 ? "yes" : bestScore >= 0.6 ? "partial" : "no";
    }

    static double verdictValue(String v) {
        return switch (v) {
            case "yes" -> 1;
            case "partial" -> 0.5;
            default -> 0;
        };
    }

    /** Hybrid retrieval score → 0..1 (0.45 = weakest kept evidence, ≥ 1.0 = very strong). */
    static double strength(double score) {
        return Math.max(0, Math.min(1, (score - 0.45) / 0.55));
    }
}
