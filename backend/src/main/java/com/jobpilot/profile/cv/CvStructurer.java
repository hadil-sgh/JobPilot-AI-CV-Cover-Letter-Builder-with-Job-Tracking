package com.jobpilot.profile.cv;

/** Turns raw CV text into structured data (prompt A). Implemented with the local LLM. */
public interface CvStructurer {

    CvDraft structure(String cvText);
}
