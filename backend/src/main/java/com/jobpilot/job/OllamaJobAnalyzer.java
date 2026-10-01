package com.jobpilot.job;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.jobpilot.common.llm.LlmClient;
import com.jobpilot.common.llm.LlmJson;

/** Prompt B (PROJECT.md 3.5) on the local Llama. The JD is data inside {@code <job>} tags. */
@Service
public class OllamaJobAnalyzer implements JobAnalyzer {

    private final LlmClient llm;
    private final String systemPrompt;

    public OllamaJobAnalyzer(LlmClient llm,
                             @Value("classpath:prompts/jd-analysis-system.txt") Resource prompt) throws IOException {
        this.llm = llm;
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public JobAnalysis analyze(String jdText) {
        String user = "<job>\n" + LlmJson.stripDelimiter(jdText, "job") + "\n</job>";
        return llm.callJson(systemPrompt, user, JobAnalysis.class, Function.identity(), "this job description");
    }
}
