package com.jobpilot.job;

/** Prompt B: structured analysis of a (sanitised) job description. */
public interface JobAnalyzer {

    JobAnalysis analyze(String jdText);
}
