package com.example.mainservice.service;

import com.example.common.dto.ShoppingRequest;
import com.example.common.dto.SubAgentInfo;
import com.example.common.dto.SubAgentReply;
import com.example.common.llm.RealQwenLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrchestratorServiceSummaryFallbackTest {
    @Test
    void stream_emitsFallbackSummaryWhenSummaryStreamHasNoDelta() {
        OrchestratorService svc = newOrchestratorWithSummaryFlux(Flux.empty());

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买轻薄本");
        req.setPrice(3000);
        req.setDislike("风扇噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .takeUntil(ev -> "done".equals(ev.event()))
                .filter(ev -> "summary".equals(ev.event()) || "done".equals(ev.event()))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertTrue(events.size() >= 2);
        assertEquals("summary", events.get(0).event());
        assertTrue(String.valueOf(events.get(0).data()).contains("\"fallback\":true"));
        assertTrue(String.valueOf(events.get(0).data()).contains("\"reason\":\"no_delta\""));
        assertEquals("done", events.get(events.size() - 1).event());
    }

    @Test
    void stream_emitsFallbackSummaryWhenSummaryStreamErrors() {
        OrchestratorService svc = newOrchestratorWithSummaryFlux(Flux.error(new RuntimeException("boom")));

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买轻薄本");
        req.setPrice(3000);
        req.setDislike("风扇噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .takeUntil(ev -> "done".equals(ev.event()))
                .filter(ev -> "summary".equals(ev.event()) || "done".equals(ev.event()))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertTrue(events.size() >= 2);
        assertEquals("summary", events.get(0).event());
        assertTrue(String.valueOf(events.get(0).data()).contains("\"fallback\":true"));
        assertTrue(String.valueOf(events.get(0).data()).contains("\"reason\":\"error\""));
        assertTrue(String.valueOf(events.get(0).data()).contains("RuntimeException"));
        assertEquals("done", events.get(events.size() - 1).event());
    }

    private static OrchestratorService newOrchestratorWithSummaryFlux(Flux<ServerSentEvent<String>> summaryFlux) {
        PromptService promptService = new PromptService() {
            @Override
            public String readBuyerPrompt(String profileId) {
                return "sys";
            }
        };

        SubAgentDirectoryService directoryService = new SubAgentDirectoryService(WebClient.builder().build(), "http://localhost") {
            @Override
            public Flux<SubAgentInfo> fetchAgents(String type) {
                SubAgentInfo info = new SubAgentInfo();
                info.setId("seller_a");
                info.setName("A");
                info.setType("shopping");
                info.setEnabled(true);
                return Flux.just(info);
            }
        };

        SubAgentDialogueService dialogueService = new SubAgentDialogueService(WebClient.builder().build(), "http://localhost") {
            @Override
            public Mono<SubAgentReply> requestOnce(String agentId, String question, Map<String, Object> context) {
                SubAgentReply r = new SubAgentReply();
                r.setAgentId(agentId);
                r.setAnswer("A 商家 推荐方案：报价=100；说明：mock");
                r.setMeta(Map.of("quote", 100));
                return Mono.just(r);
            }
        };

        LlmStreamService llmStreamService = new LlmStreamService() {
            @Override
            public Flux<ServerSentEvent<String>> streamTokens(RealQwenLlmClient client, String system, String user, String eventName) {
                if ("question".equals(eventName)) {
                    return Flux.just(ServerSentEvent.<String>builder().event("question").data("Q").build());
                }
                if ("summary".equals(eventName)) {
                    return summaryFlux;
                }
                return Flux.empty();
            }
        };

        RealQwenLlmClient llmClient = new RealQwenLlmClient("http://localhost", "m", 0.0, 1000, null) {
            @Override
            public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
                return Flux.empty();
            }
        };

        return new OrchestratorService(promptService, directoryService, dialogueService, llmStreamService, llmClient);
    }
}

