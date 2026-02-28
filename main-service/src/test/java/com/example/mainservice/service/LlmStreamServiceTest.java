package com.example.mainservice.service;

import com.example.common.llm.RealQwenLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LlmStreamServiceTest {
    @Test
    void streamTokens_emitsEventsBeforeComplete() {
        LlmStreamService svc = new LlmStreamService();
        RealQwenLlmClient client = new RealQwenLlmClient("http://localhost", "m", 0.0, 1000, null) {
            @Override
            public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
                return Flux.just("a", "b");
            }
        };

        Flux<ServerSentEvent<String>> flux = svc.streamTokens(client, "s", "u", "question");

        StepVerifier.create(flux)
                .assertNext(ev -> {
                    assertEquals("question", ev.event());
                    assertEquals("a", ev.data());
                })
                .assertNext(ev -> {
                    assertEquals("question", ev.event());
                    assertEquals("b", ev.data());
                })
                .verifyComplete();
    }

    @Test
    void streamTokens_summaryFallsBackToSyncGenerateWhenNoDelta() {
        LlmStreamService svc = new LlmStreamService();
        RealQwenLlmClient client = new RealQwenLlmClient("http://localhost", "m", 0.0, 1000, null) {
            @Override
            public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
                return Flux.empty();
            }

            @Override
            public String generate(String systemPrompt, String userInput) {
                return "SUM";
            }
        };

        Flux<ServerSentEvent<String>> flux = svc.streamTokens(client, "s", "u", "summary");

        StepVerifier.create(flux)
                .assertNext(ev -> {
                    assertEquals("summary", ev.event());
                    assertEquals("SUM", ev.data());
                })
                .verifyComplete();
    }
}
