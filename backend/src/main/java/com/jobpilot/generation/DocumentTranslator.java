package com.jobpilot.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.jobpilot.common.text.LanguageGuess;
import com.jobpilot.common.text.TextClean;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.CvContent.Entry;
import com.jobpilot.generation.content.CvContent.SkillGroup;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.llm.GenerationLlm;
import com.jobpilot.profile.ProfileLanguage;

/**
 * Translates a CV or letter between English and French. Only wording is translated (headline,
 * summary, titles, descriptions, bullets, skill group names, spoken languages, letter text);
 * names, organisations, dates, links and skill items stay as they are. Texts that are already
 * clearly in the target language are not sent to the model. Output is untrusted like any LLM text:
 * it is cleaned here and escaped at render time.
 */
@Component
public class DocumentTranslator {

    static final int BATCH = 25;

    private final GenerationLlm llm;

    public DocumentTranslator(GenerationLlm llm) {
        this.llm = llm;
    }

    public CvContent translateCv(CvContent cv, String language) {
        Set<String> texts = new LinkedHashSet<>();
        mapCv(cv, collector(texts), language);
        Map<String, String> translated = translateAll(texts, language);
        return mapCv(cv, s -> s == null ? null : translated.getOrDefault(s, s), language);
    }

    public LetterContent translateLetter(LetterContent letter, String language) {
        Set<String> texts = new LinkedHashSet<>();
        mapLetter(letter, collector(texts), language);
        Map<String, String> translated = translateAll(texts, language);
        return mapLetter(letter, s -> s == null ? null : translated.getOrDefault(s, s), language);
    }

    private static UnaryOperator<String> collector(Set<String> into) {
        return s -> {
            if (s != null && !s.isBlank()) {
                into.add(s);
            }
            return s;
        };
    }

    /** original → translation, for the texts that need it (in batches, to keep each answer short). */
    Map<String, String> translateAll(Set<String> texts, String language) {
        List<String> todo = texts.stream().filter(t -> needsTranslation(t, language)).toList();
        Map<String, String> out = new HashMap<>();
        for (int from = 0; from < todo.size(); from += BATCH) {
            List<String> batch = todo.subList(from, Math.min(todo.size(), from + BATCH));
            List<String> result = llm.translate(batch, language);
            for (int i = 0; i < batch.size() && i < result.size(); i++) {
                String clean = TextClean.clean(result.get(i), Math.max(300, batch.get(i).length() * 2));
                if (clean != null) {
                    out.put(batch.get(i), clean);
                }
            }
        }
        return out;
    }

    /** Skip texts that are already clearly in the target language or have no words at all. */
    static boolean needsTranslation(String text, String language) {
        return text.chars().anyMatch(Character::isLetter) && !language.equals(LanguageGuess.clear(text, 2));
    }

    static CvContent mapCv(CvContent cv, UnaryOperator<String> f, String language) {
        CvContent.Header h = cv.header();
        CvContent.Header header = h == null ? null
                : new CvContent.Header(h.fullName(), f.apply(h.headline()), h.email(), h.phone(), h.location(), h.links());
        List<SkillGroup> skills = new ArrayList<>();
        for (SkillGroup g : nullSafe(cv.skills())) {
            skills.add(g == null ? null : new SkillGroup(f.apply(g.group()), g.items()));
        }
        List<ProfileLanguage> languages = new ArrayList<>();
        for (ProfileLanguage l : nullSafe(cv.languages())) {
            languages.add(l == null ? null : new ProfileLanguage(f.apply(l.name()), f.apply(l.level())));
        }
        return new CvContent(language, header, f.apply(cv.summary()), entries(cv.experience(), f),
                entries(cv.education(), f), entries(cv.projects(), f), skills, languages,
                entries(cv.certifications(), f), cv.match(), cv.review());
    }

    static LetterContent mapLetter(LetterContent l, UnaryOperator<String> f, String language) {
        List<String> paragraphs = nullSafe(l.paragraphs()).stream().map(f).toList();
        return new LetterContent(language, l.company(), f.apply(l.roleTitle()), f.apply(l.greeting()), paragraphs,
                f.apply(l.closing()), l.signature(), l.review());
    }

    private static List<Entry> entries(List<Entry> list, UnaryOperator<String> f) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : nullSafe(list)) {
            out.add(e == null ? null : new Entry(e.ref(), e.itemId(), f.apply(e.title()), e.organization(), e.start(),
                    e.end(), f.apply(e.description()), nullSafe(e.bullets()).stream().map(f).toList(), e.tags()));
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
