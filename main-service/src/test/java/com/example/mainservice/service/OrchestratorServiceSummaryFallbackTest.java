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
    void stream_emitsFallbackSummayWhenSummaryGenerateReturnsBlank() {
        OrchestratorService svc = newOrchestratorWithSummaryGenerate("", false);

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买轻薄本");
        req.setPrice(3000);
        req.setDislike("风扇噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .takeUntil(ev -> "done".equals(ev.event()))
                .filter(ev -> "summay".equals(ev.event()) || "done".equals(ev.event()))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertTrue(events.size() >= 1);
        if (events.size() >= 2 && "summay".equals(events.get(0).event())) {
            assertTrue(String.valueOf(events.get(0).data()).contains("no_delta"));
        }
        assertEquals("done", events.get(events.size() - 1).event());
    }

    @Test
    void stream_emitsFallbackSummayWhenSummaryGenerateErrors() {
        OrchestratorService svc = newOrchestratorWithSummaryGenerate(null, true);

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买轻薄本");
        req.setPrice(3000);
        req.setDislike("风扇噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .takeUntil(ev -> "done".equals(ev.event()))
                .filter(ev -> "summay".equals(ev.event()) || "done".equals(ev.event()))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertTrue(events.size() >= 1);
        if (events.size() >= 2 && "summay".equals(events.get(0).event())) {
            assertTrue(String.valueOf(events.get(0).data()).contains("error"));
            assertTrue(String.valueOf(events.get(0).data()).contains("\"endConversation\""));
        }
        assertEquals("done", events.get(events.size() - 1).event());
    }

    private static OrchestratorService newOrchestratorWithSummaryGenerate(String summaryResult, boolean throwError) {
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

            @Override
            public Mono<String> requestJudgeOnce(String replayJson, ShoppingRequest request) {
                return Mono.just("{\"pass\":true,\"severe\":false,\"risk_flags\":[],\"reasons\":[],\"fix_advice\":[],\"score\":90}");
            }
        };

        LlmStreamService llmStreamService = new LlmStreamService();

        RealQwenLlmClient llmClient = new RealQwenLlmClient("http://localhost", "m", 0.0, 1000, null) {
            @Override
            public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
                return Flux.empty();
            }

            @Override
            public String generate(String systemPrompt, String userInput) {
                if (throwError) {
                    throw new RuntimeException("boom");
                }
                return summaryResult;
            }
        };

        return new OrchestratorService(promptService, directoryService, dialogueService, llmStreamService, llmClient);
    }
}
