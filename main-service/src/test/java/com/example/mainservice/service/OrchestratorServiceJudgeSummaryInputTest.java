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
    void stream_summaryUsesJudgePromptAndTaggedUserInput() {
        AtomicReference<String> capturedSummarySystem = new AtomicReference<>("");
        AtomicReference<String> capturedSummaryUser = new AtomicReference<>("");

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
                    capturedSummarySystem.set(system == null ? "" : system);
                    capturedSummaryUser.set(user == null ? "" : user);
                    return Flux.just(ServerSentEvent.<String>builder().event("summary").data("OK").build());
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

        String sys = capturedSummarySystem.get();
        String usr = capturedSummaryUser.get();
        assertTrue(sys.contains("消费判官") || sys.contains("判官"));
        assertTrue(usr.contains("<hidden>") && usr.contains("</hidden>"));
        assertTrue(usr.contains("<GoodsInfo>") && usr.contains("</GoodsInfo>"));
        assertTrue(usr.contains("<subAgentReplay>") && usr.contains("</subAgentReplay>"));
        assertTrue(usr.contains("A 商家 推荐方案"));
        assertTrue(!usr.contains("\"reply\":\"DONE\""));
    }
}

