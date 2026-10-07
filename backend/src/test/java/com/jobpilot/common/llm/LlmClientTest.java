package com.jobpilot.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.jobpilot.common.error.ApiException;

class LlmClientTest {

    record Answer(String value) {
    }

    static final String OOM = "HTTP 500 - {\"error\":\"model requires more system memory (2.4 GiB) than is available (1.5 GiB)\"}";

    /** Fake Ollama: the default model is out of memory, the fallback answers. Records which model was asked. */
    static class FakeModel implements ChatModel {
        final List<String> calls = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            String model = prompt.getOptions() == null ? null : prompt.getOptions().getModel();
            calls.add(String.valueOf(model));
            if (model == null || !model.equals("llama3.2:3b")) {
                throw new RuntimeException("Retry exhausted", new IllegalStateException(OOM));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"value\":\"from fallback\"}"))));
        }
    }

    @Test
    void usesTheFallbackModelWhenTheMainOneDoesNotFitInMemory() {
        FakeModel model = new FakeModel();
        LlmClient client = new LlmClient(ChatClient.builder(model), new ObjectMapper(), "llama3.2:3b", "");

        Answer a = client.callJson("system", "user", Answer.class, Function.identity(), "this test");

        assertThat(a.value()).isEqualTo("from fallback");
        assertThat(model.calls).containsExactly("null", "llama3.2:3b");
    }

    @Test
    void withoutFallbackTheUserGetsAClearMemoryMessage() {
        LlmClient client = new LlmClient(ChatClient.builder(new FakeModel()), new ObjectMapper(), "", "");
        assertThatThrownBy(() -> client.callJson("s", "u", Answer.class, Function.identity(), "this test"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not have enough free memory");
    }

    @Test
    void fastCallsUseTheFastModel() {
        FakeModel model = new FakeModel();
        LlmClient client = new LlmClient(ChatClient.builder(model), new ObjectMapper(), "", "llama3.2:3b");
        assertThat(client.callJsonFast("s", "u", Answer.class, Function.identity(), "x").value()).isEqualTo("from fallback");
        assertThat(model.calls).containsExactly("llama3.2:3b");
    }

    @Test
    void detectsOllamaOutOfMemoryAnywhereInTheCauseChain() {
        RuntimeException e = new RuntimeException("Retry exhausted", new IllegalStateException(OOM));
        assertThat(LlmClient.causeMessageContains(e, LlmClient.OUT_OF_MEMORY)).isTrue();
        assertThat(LlmClient.causeMessageContains(new RuntimeException("boom"), LlmClient.OUT_OF_MEMORY)).isFalse();
    }
}
