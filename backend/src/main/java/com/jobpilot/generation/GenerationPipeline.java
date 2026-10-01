package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationService;
import com.jobpilot.auth.User;
import com.jobpilot.auth.UserRepository;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.common.text.LanguageGuess;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.content.ReviewFlag;
import com.jobpilot.generation.llm.GenerationLlm;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.validation.FactBase;
import com.jobpilot.generation.validation.FactValidator;
import com.jobpilot.job.JobDescription;
import com.jobpilot.job.JobDescriptionRepository;
import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileRepository;
import com.jobpilot.rag.ProfileChunker;
import com.jobpilot.rag.RetrievalService;

/**
 * The generation steps (PROJECT.md 3.2): evidence → match score → CV → letter, each LLM output
 * validated (3.6) with one corrective retry; remaining findings become review flags.
 * No method here holds a DB transaction while calling the LLM.
 */
@Service
public class GenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(GenerationPipeline.class);

    public record Result(CvContent cv, LetterContent letter) {
    }

    private final ApplicationService applications;
    private final JobDescriptionRepository jobs;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final ProfileChunker chunker;
    private final RetrievalService retrieval;
    private final EvidencePackBuilder packBuilder;
    private final GenerationLlm llm;
    private final ContentAssembler assembler;
    private final FactValidator validator;
    private final DocumentTranslator translator;
    private final TransactionTemplate readOnlyTx;

    public GenerationPipeline(ApplicationService applications, JobDescriptionRepository jobs,
                              ProfileRepository profiles, UserRepository users, ProfileChunker chunker,
                              RetrievalService retrieval, EvidencePackBuilder packBuilder, GenerationLlm llm,
                              ContentAssembler assembler, FactValidator validator, DocumentTranslator translator,
                              TransactionTemplate tx) {
        this.applications = applications;
        this.jobs = jobs;
        this.profiles = profiles;
        this.users = users;
        this.chunker = chunker;
        this.retrieval = retrieval;
        this.packBuilder = packBuilder;
        this.llm = llm;
        this.assembler = assembler;
        this.validator = validator;
        this.translator = translator;
        this.readOnlyTx = new TransactionTemplate(tx.getTransactionManager());
        this.readOnlyTx.setReadOnly(true);
    }

    /** Loads and checks everything up front so problems surface before the job is queued. */
    public GenerationContext loadContext(UUID userId, UUID applicationId) {
        return readOnlyTx.execute(tx -> {
            Application app = applications.owned(userId, applicationId);
            if (app.getJobId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This application has no analysed job description");
            }
            JobDescription job = jobs.findById(app.getJobId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The job description no longer exists"));
            User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
            Profile profile = profiles.findByUserId(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Fill in your profile first"));
            ProfileSnapshot snapshot = ProfileSnapshot.of(profile, user.getFullName(), user.getEmail(), chunker.chunk(profile));
            if (snapshot.itemsOf(com.jobpilot.profile.ItemType.EXPERIENCE).isEmpty()
                    && snapshot.itemsOf(com.jobpilot.profile.ItemType.EDUCATION).isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Your profile has no experience or education yet. Import your CV or fill in your profile first.");
            }
            return new GenerationContext(userId, applicationId, app.getCompany(), app.getRoleTitle(), job.getAnalysis(),
                    job.getLanguage() == null ? "en" : job.getLanguage(), snapshot);
        });
    }

    public EvidencePack buildPack(GenerationContext ctx) {
        return packBuilder.build(ctx.profile(), ctx.analysis(),
                req -> retrieval.evidenceFor(req, ctx.profile().profileId()));
    }

    public CvContent.Match scoreMatch(GenerationContext ctx, EvidencePack pack) {
        if (pack.requirements().isEmpty()) {
            return new CvContent.Match(0, List.of(), List.of());
        }
        return MatchScorer.score(pack.requirements(), llm.judgeMatch(ctx, pack));
    }

    public CvContent writeCv(GenerationContext ctx, EvidencePack pack, CvContent.Match match, double creativity) {
        FactBase facts = FactBase.of(ctx);
        CvDraftOut draft = llm.writeCv(ctx, pack, List.of(), creativity);
        CvContent cv = assembler.assembleCv(ctx, pack, draft, match);
        List<ReviewFlag> flags = validator.validateCv(cv, facts);
        if (!flags.isEmpty()) {
            log.info("CV for application {} failed validation ({} findings); retrying once", ctx.applicationId(), flags.size());
            cv = assembler.assembleCv(ctx, pack, llm.writeCv(ctx, pack, messages(flags), creativity), match);
            flags = validator.validateCv(cv, facts);
        }
        return cv.withReview(merge(cv.review(), flags));
    }

    public LetterContent writeLetter(GenerationContext ctx, EvidencePack pack, double creativity) {
        FactBase facts = FactBase.of(ctx);
        LetterDraftOut draft = llm.writeLetter(ctx, pack, List.of(), creativity);
        LetterContent letter = assembler.assembleLetter(ctx, draft);
        List<ReviewFlag> flags = validator.validateLetter(letter, facts);
        if (!flags.isEmpty()) {
            log.info("Letter for application {} failed validation ({} findings); retrying once", ctx.applicationId(), flags.size());
            letter = assembler.assembleLetter(ctx, llm.writeLetter(ctx, pack, messages(flags), creativity));
            flags = validator.validateLetter(letter, facts);
        }
        return letter.withReview(flags);
    }

    /** Full run used by the async job; {@code progress} receives step names for polling. */
    public Result run(GenerationContext ctx, Consumer<String> progress) {
        progress.accept("EVIDENCE");
        EvidencePack pack = buildPack(ctx);
        progress.accept("MATCH");
        CvContent.Match match = scoreMatch(ctx, pack);
        progress.accept("CV");
        CvContent cv = localizeFacts(ctx, writeCv(ctx, pack, match, 0.0));
        progress.accept("LETTER");
        LetterContent letter = writeLetter(ctx, pack, 0.0);
        progress.accept("SAVING");
        return new Result(cv, letter);
    }

    /**
     * Titles, descriptions, skill group names and languages are copied from the profile, so an
     * English profile would leave English labels in a French CV: translate them when the languages differ.
     */
    CvContent localizeFacts(GenerationContext ctx, CvContent cv) {
        String profileLanguage = LanguageGuess.guess(ctx.profile().allText());
        if (profileLanguage.equals(ctx.language())) {
            return cv;
        }
        log.info("Profile is in {} but the job is in {}: translating CV labels", profileLanguage, ctx.language());
        return translator.translateCv(cv, ctx.language());
    }

    private static List<String> messages(List<ReviewFlag> flags) {
        return flags.stream().map(f -> f.section() + ": " + f.message()).distinct().toList();
    }

    private static List<ReviewFlag> merge(List<ReviewFlag> a, List<ReviewFlag> b) {
        List<ReviewFlag> out = new ArrayList<>(a == null ? List.of() : a);
        out.addAll(b);
        return out;
    }
}
