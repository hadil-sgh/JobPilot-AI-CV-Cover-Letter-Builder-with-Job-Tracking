package com.jobpilot.profile.cv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.common.llm.LlmClient;
import com.jobpilot.common.llm.LlmJson;

/**
 * Prompt A (PROJECT.md 3.5) on the local Llama. The CV text is wrapped in delimiter tags and
 * treated as data; output shape mistakes are repaired by {@link CvJsonNormalizer}.
 */
@Service
public class OllamaCvStructurer implements CvStructurer {

    private final LlmClient llm;
    private final String systemPrompt;

    public OllamaCvStructurer(LlmClient llm,
                              @Value("classpath:prompts/cv-structuring-system.txt") Resource prompt) throws IOException {
        this.llm = llm;
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public CvDraft structure(String cvText) {
        String user = "<cv_text>\n" + LlmJson.stripDelimiter(cvText, "cv_text") + "\n</cv_text>";
        try {
            return llm.callJson(systemPrompt, user, CvDraft.class, CvJsonNormalizer::normalize, "this CV");
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.BAD_GATEWAY) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "The AI model could not read this CV. Try again, or fill in your profile manually.");
            }
            throw e;
        }
    }
}
