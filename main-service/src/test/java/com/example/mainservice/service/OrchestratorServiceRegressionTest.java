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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrchestratorServiceRegressionTest {
    @Test
    void stream_doesNotDuplicateMainFlow() {
        AtomicInteger questionSubscribeCount = new AtomicInteger(0);
        AtomicInteger summarySubscribeCount = new AtomicInteger(0);
        AtomicInteger requestOnceCount = new AtomicInteger(0);

        OrchestratorService svc = newOrchestrator(
                Flux.just(agent("seller_a"), agent("seller_b")),
                (agentId, question, context) -> {
                    requestOnceCount.incrementAndGet();
                    SubAgentReply r = new SubAgentReply();
                    r.setAgentId(agentId);
                    r.setAnswer(agentId + " answer");
                    r.setMeta(Map.of("quote", 100));
                    return Mono.just(r);
                },
                Flux.defer(() -> {
                    questionSubscribeCount.incrementAndGet();
                    return Flux.just(
                            sse("question", "Q1"),
                            sse("question", "Q2")
                    );
                }),
                Flux.defer(() -> {
                    summarySubscribeCount.incrementAndGet();
                    return Flux.empty();
                })
        );

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买轻薄本");
        req.setPrice(3000);
        req.setDislike("风扇噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        long doneCount = events.stream().filter(e -> "done".equals(e.event())).count();
        assertEquals(1, doneCount);
        assertEquals(1, questionSubscribeCount.get());
        assertEquals(1, summarySubscribeCount.get());
        assertEquals(2, requestOnceCount.get());
    }

    @Test
    void stream_subAgentUsesFinalPublicQuestion() {
        AtomicReference<String> receivedQuestion = new AtomicReference<>();

        OrchestratorService svc = newOrchestrator(
                Flux.just(agent("seller_a")),
                (agentId, question, context) -> {
                    receivedQuestion.set(question);
                    SubAgentReply r = new SubAgentReply();
                    r.setAgentId(agentId);
                    r.setAnswer("answer");
                    r.setMeta(Map.of("quote", 100));
                    return Mono.just(r);
                },
                Flux.just(
                        sse("question", "你"),
                        sse("question", "好")
                ),
                Flux.empty()
        );

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("我想买个保温杯");
        req.setPrice(200);
        req.setDislike("太重");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertEquals("你好", receivedQuestion.get());
    }

    @Test
    void stream_questionFallbackWhenNoDelta() {
        AtomicReference<String> receivedQuestion = new AtomicReference<>();

        OrchestratorService svc = newOrchestrator(
                Flux.just(agent("seller_a")),
                (agentId, question, context) -> {
                    receivedQuestion.set(question);
                    SubAgentReply r = new SubAgentReply();
                    r.setAgentId(agentId);
                    r.setAnswer("answer");
                    r.setMeta(Map.of("quote", 100));
                    return Mono.just(r);
                },
                Flux.empty(),
                Flux.empty()
        );

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("我想买个蓝牙耳机");
        req.setPrice(300);
        req.setDislike("延迟高");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        String expected = "需求：" + req.getRequest() + "；目标价格：" + req.getPrice() + "；排斥点：" + req.getDislike();

        long questionCount = events.stream().filter(e -> "question".equals(e.event())).count();
        assertEquals(1, questionCount);
        String questionData = events.stream()
                .filter(e -> "question".equals(e.event()))
                .map(e -> String.valueOf(e.data()))
                .findFirst()
                .orElse("");
        assertEquals(expected, questionData);
        assertEquals(expected, receivedQuestion.get());
    }

    @Test
    void stream_questionFallbackWhenQuestionStreamErrors() {
        AtomicReference<String> receivedQuestion = new AtomicReference<>();

        OrchestratorService svc = newOrchestrator(
                Flux.just(agent("seller_a")),
                (agentId, question, context) -> {
                    receivedQuestion.set(question);
                    SubAgentReply r = new SubAgentReply();
                    r.setAgentId(agentId);
                    r.setAnswer("answer");
                    r.setMeta(Map.of("quote", 100));
                    return Mono.just(r);
                },
                Flux.error(new RuntimeException("boom")),
                Flux.empty()
        );

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("我想买个电动牙刷");
        req.setPrice(200);
        req.setDislike("噪音大");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        String expected = "需求：" + req.getRequest() + "；目标价格：" + req.getPrice() + "；排斥点：" + req.getDislike();

        long questionCount = events.stream().filter(e -> "question".equals(e.event())).count();
        assertEquals(1, questionCount);
        String questionData = events.stream()
                .filter(e -> "question".equals(e.event()))
                .map(e -> String.valueOf(e.data()))
                .findFirst()
                .orElse("");
        assertEquals(expected, questionData);
        assertEquals(expected, receivedQuestion.get());
    }

    @Test
    void stream_oneAgentFailureDoesNotBreakOverall() {
        AtomicInteger requestOnceCount = new AtomicInteger(0);

        OrchestratorService svc = newOrchestrator(
                Flux.just(agent("seller_a"), agent("seller_b"), agent("seller_c")),
                (agentId, question, context) -> {
                    requestOnceCount.incrementAndGet();
                    if ("seller_a".equals(agentId)) {
                        return Mono.error(new RuntimeException("boom"));
                    }
                    SubAgentReply r = new SubAgentReply();
                    r.setAgentId(agentId);
                    r.setAnswer(agentId + " answer");
                    if ("seller_b".equals(agentId)) {
                        r.setMeta(Map.of("quote", 200));
                    } else {
                        r.setMeta(Map.of("quote", 100));
                    }
                    return Mono.just(r);
                },
                Flux.just(sse("question", "Q")),
                Flux.just(sse("summary", "S"))
        );

        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("想买保暖内衣");
        req.setPrice(200);
        req.setDislike("不喜欢某品牌");

        List<ServerSentEvent<String>> events = svc.stream(req)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertEquals(3, requestOnceCount.get());

        String allReplayData = events.stream()
                .filter(e -> "subAgentReplay".equals(e.event()))
                .map(e -> String.valueOf(e.data()))
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(!allReplayData.contains("seller_a"));

        String doneData = events.stream()
                .filter(e -> "done".equals(e.event()))
                .map(e -> String.valueOf(e.data()))
                .findFirst()
                .orElse("");
        assertTrue(doneData.contains("\"bestQuote\":100"));
    }

    private static OrchestratorService newOrchestrator(
            Flux<SubAgentInfo> agents,
            SubAgentRequestOnceFn requestOnceFn,
            Flux<ServerSentEvent<String>> questionFlux,
            Flux<ServerSentEvent<String>> summaryFlux) {
        PromptService promptService = new PromptService() {
            @Override
            public String readBuyerPrompt(String profileId) {
                return "sys";
            }
        };

        SubAgentDirectoryService directoryService = new SubAgentDirectoryService(WebClient.builder().build(), "http://localhost") {
            @Override
            public Flux<SubAgentInfo> fetchAgents(String type) {
                return agents;
            }
        };

        SubAgentDialogueService dialogueService = new SubAgentDialogueService(WebClient.builder().build(), "http://localhost") {
            @Override
            public Mono<SubAgentReply> requestOnce(String agentId, String question, Map<String, Object> context) {
                return requestOnceFn.apply(agentId, question, context);
            }
        };

        LlmStreamService llmStreamService = new LlmStreamService() {
            @Override
            public Flux<ServerSentEvent<String>> streamTokens(RealQwenLlmClient client, String system, String user, String eventName) {
                if ("question".equals(eventName)) {
                    return questionFlux;
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

    private static SubAgentInfo agent(String id) {
        SubAgentInfo info = new SubAgentInfo();
        info.setId(id);
        info.setName(id);
        info.setType("shopping");
        info.setEnabled(true);
        return info;
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private interface SubAgentRequestOnceFn {
        Mono<SubAgentReply> apply(String agentId, String question, Map<String, Object> context);
    }
}
