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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrchestratorServiceJudgeSummaryInputTest {
    @Test
    void stream_judgeAndSummayUseTaggedUserInput() {
        AtomicReference<String> capturedJudgeReplay = new AtomicReference<>("");
        AtomicReference<String> capturedSummayUser = new AtomicReference<>("");

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
                capturedJudgeReplay.set(replayJson == null ? "" : replayJson);
                return Mono.just("{\"pass\":true,\"severe\":false,\"risk_flags\":[],\"reasons\":[],\"fix_advice\":[],\"score\":90}");
            }
        };

        LlmStreamService llmStreamService = new LlmStreamService() {
            @Override
            public Flux<ServerSentEvent<String>> streamTokens(RealQwenLlmClient client, String system, String user, String eventName) {
                if ("question".equals(eventName)) {
                    return Flux.just(ServerSentEvent.<String>builder().event("question").data("Q").build());
                }
                return Flux.empty();
            }
        };

        RealQwenLlmClient llmClient = new RealQwenLlmClient("http://localhost", "m", 0.0, 1000, null) {
            @Override
            public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
                return Flux.empty();
            }

            @Override
            public String generate(String systemPrompt, String userInput) {
                capturedSummayUser.set(userInput == null ? "" : userInput);
                return "{\"round\":3,\"hasCandidate\":true,\"candidateMerchantId\":\"seller_a\",\"candidateReason\":\"ok\",\"endConversation\":true,\"decisionReason\":\"candidate_found\",\"merchantReviews\":[],\"overallReview\":\"ok\"}";
            }
        };

        OrchestratorService svc = new OrchestratorService(promptService, directoryService, dialogueService, llmStreamService, llmClient);

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买保暖内衣");
        req.setPrice(200);
        req.setDislike("不喜欢太厚");
        req.setHidding("其实更在意性价比与售后");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .takeUntil(ev -> "done".equals(ev.event()))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);

        String judgeReplay = capturedJudgeReplay.get();
        String summayUsr = capturedSummayUser.get();
        assertTrue(judgeReplay.contains("\"subAgentId\""));
        assertTrue(judgeReplay.contains("\"quote\""));
        assertTrue(summayUsr.contains("当前轮次="));
        assertTrue(summayUsr.contains("<RoundHistory>") && summayUsr.contains("</RoundHistory>"));
    }
}
