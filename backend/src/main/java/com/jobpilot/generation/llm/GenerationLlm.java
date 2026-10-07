package com.jobpilot.generation.llm;

import java.util.List;

import com.jobpilot.generation.EvidencePack;
import com.jobpilot.generation.GenerationContext;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.llm.GenerationModels.Verdict;

/**
 * The generation prompts (C: CV, D: letter, E: match judgment, plus translation). One interface so tests can
 * replace the LLM with a single {@code @MockitoBean}.
 *
 * @param feedback   validator violations from a previous attempt (empty on the first try)
 * @param creativity 0 = deterministic; higher gives a different version (section regenerate)
 */
public interface GenerationLlm {

    CvDraftOut writeCv(GenerationContext ctx, EvidencePack pack, List<String> feedback, double creativity);

    LetterDraftOut writeLetter(GenerationContext ctx, EvidencePack pack, List<String> feedback, double creativity);

    List<Verdict> judgeMatch(GenerationContext ctx, EvidencePack pack);

    /**
     * Translates short texts to {@code language} ("en" / "fr"), same order and size as the input.
     * A text the model skipped or emptied comes back unchanged.
     */
    List<String> translate(List<String> texts, String language);
}
