package com.jobpilot.common.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmClientTest {

    @Test
    void detectsOllamaOutOfMemoryAnywhereInTheCauseChain() {
        // Real error seen on 2026-10-01 (wrapped by Spring AI's retry template).
        RuntimeException e = new RuntimeException("Retry exhausted", new IllegalStateException(
                "HTTP 500 - {\"error\":\"model requires more system memory (2.4 GiB) than is available (1.5 GiB)\"}"));
        assertThat(LlmClient.causeMessageContains(e, "requires more system memory")).isTrue();
        assertThat(LlmClient.causeMessageContains(new RuntimeException("boom"), "requires more system memory")).isFalse();
    }
}
