package com.jobpilot.generation.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.jobpilot.common.llm.LlmClient;
import com.jobpilot.common.llm.LlmJson;
import com.jobpilot.generation.EvidencePack;
import com.jobpilot.generation.GenerationContext;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.llm.GenerationModels.Translation;
import com.jobpilot.generation.llm.GenerationModels.Translations;
import com.jobpilot.generation.llm.GenerationModels.Verdict;
import com.jobpilot.generation.llm.GenerationModels.Verdicts;
import com.jobpilot.job.JobAnalysis;

/**
 * Prompts C, D, E (PROJECT.md 3.5) on the local Llama. Everything that comes from the job ad or
 * the profile is passed as delimited DATA in the user message (delimiters stripped from the
 * content); only controlled values (language) are substituted into system prompts.
 */
@Service
public class OllamaGenerationLlm implements GenerationLlm {

    private static final String[] TAGS = {"job_analysis", "evidence", "candidate", "company", "role", "tone",
            "requirements", "previous_violations", "texts"};

    private final LlmClient llm;
    private final String cvPrompt;
    private final String letterPrompt;
    private final String judgePrompt;
    private final String translatePrompt;

    public OllamaGenerationLlm(LlmClient llm,
                               @Value("classpath:prompts/cv-generation-system.txt") Resource cv,
                               @Value("classpath:prompts/letter-generation-system.txt") Resource letter,
                               @Value("classpath:prompts/match-judge-system.txt") Resource judge,
                               @Value("classpath:prompts/translate-system.txt") Resource translate) throws IOException {
        this.llm = llm;
        this.cvPrompt = cv.getContentAsString(StandardCharsets.UTF_8);
        this.letterPrompt = letter.getContentAsString(StandardCharsets.UTF_8);
        this.judgePrompt = judge.getContentAsString(StandardCharsets.UTF_8);
        this.translatePrompt = translate.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public CvDraftOut writeCv(GenerationContext ctx, EvidencePack pack, List<String> feedback, double creativity) {
        String user = block("job_analysis", analysisText(ctx.analysis()))
                + block("evidence", pack.render())
                + feedbackBlock(feedback);
        return llm.callJson(cvPrompt.replace("{{language}}", languageName(ctx.language())), user, CvDraftOut.class,
                Function.identity(), "the CV", creativity);
    }

    @Override
    public LetterDraftOut writeLetter(GenerationContext ctx, EvidencePack pack, List<String> feedback,
                                      double creativity) {
        String user = block("candidate", nullToEmpty(ctx.profile().fullName()))
                + block("company", ctx.company())
                + block("role", nullToEmpty(ctx.roleTitle()))
                + block("tone", ctx.tone())
                + block("job_analysis", analysisText(ctx.analysis()))
                + block("evidence", pack.render())
                + feedbackBlock(feedback);
        return llm.callJson(letterPrompt.replace("{{language}}", languageName(ctx.language())), user,
                LetterDraftOut.class, Function.identity(), "the letter", creativity);
    }

    @Override
    public List<Verdict> judgeMatch(GenerationContext ctx, EvidencePack pack) {
        var byRef = pack.byRef();
        StringBuilder sb = new StringBuilder();
        int id = 1;
        for (EvidencePack.Requirement r : pack.requirements()) {
            sb.append(id++).append(". [").append(r.mustHave() ? "MUST" : "NICE").append("] ").append(r.text()).append('\n');
            if (r.refs().isEmpty()) {
                sb.append("   Evidence: none\n");
            }
            r.refs().stream().limit(2).forEach(ref -> {
                String text = byRef.get(ref).text().replace('\n', ' ');
                sb.append("   Evidence [").append(ref).append("]: ")
                        .append(text.length() > 300 ? text.substring(0, 300) + "…" : text).append('\n');
            });
        }
        // Judgment task (yes/partial/no): runs on the fast model when one is configured.
        Verdicts v = llm.callJsonFast(judgePrompt, block("requirements", sb.toString()), Verdicts.class,
                Function.identity(), "the match score");
        return v.verdicts() == null ? List.of() : v.verdicts();
    }

    @Override
    public List<String> translate(List<String> texts, String language) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i).replace('\n', ' ')).append('\n');
        }
        // Writing task (quality matters): main model.
        Translations t = llm.callJson(translatePrompt.replace("{{language}}", languageName(language)),
                block("texts", sb.toString()), Translations.class, Function.identity(), "the translation");
        String[] out = texts.toArray(String[]::new);
        for (Translation tr : t.translations() == null ? List.<Translation>of() : t.translations()) {
            if (tr != null && tr.id() != null && tr.id() >= 1 && tr.id() <= out.length
                    && tr.text() != null && !tr.text().isBlank()) {
                out[tr.id() - 1] = tr.text().strip();
            }
        }
        return List.of(out);
    }

    static String analysisText(JobAnalysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(nullToEmpty(a.title())).append('\n');
        if (a.seniority() != null) {
            sb.append("Seniority: ").append(a.seniority()).append('\n');
        }
        list(sb, "Requirements", a.requirements());
        list(sb, "Nice to have", a.niceToHave());
        list(sb, "Responsibilities", a.responsibilities());
        if (a.keywords() != null && !a.keywords().isEmpty()) {
            sb.append("Keywords: ").append(String.join(", ", a.keywords())).append('\n');
        }
        return sb.toString();
    }

    private static void list(StringBuilder sb, String label, List<String> items) {
        if (items != null && !items.isEmpty()) {
            sb.append(label).append(":\n");
            items.forEach(i -> sb.append("- ").append(i).append('\n'));
        }
    }

    private static String feedbackBlock(List<String> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return "";
        }
        return block("previous_violations", "Your previous answer was rejected by the fact checker:\n- "
                + String.join("\n- ", feedback)
                + "\nWrite it again without these unsupported claims. Use only facts from the evidence.");
    }

    /** Wraps data in a tag after removing every delimiter tag from it, so data cannot break out. */
    static String block(String tag, String content) {
        String clean = nullToEmpty(content);
        for (String t : TAGS) {
            clean = LlmJson.stripDelimiter(clean, t);
        }
        return "<" + tag + ">\n" + clean.strip() + "\n</" + tag + ">\n";
    }

    private static String languageName(String code) {
        return "fr".equals(code) ? "French" : "English";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
