package com.example.mainservice.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.example.common.dto.ShoppingRequest;
import com.example.common.llm.LlmClient;
import com.example.common.llm.RealQwenLlmClient;
import com.example.common.util.DataPathResolver;
import com.example.common.util.JsonUtils;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Log4j2
@Service
public class OrchestratorService {
    private final PromptService promptService;
    private final SubAgentDirectoryService directoryService;
    private final SubAgentDialogueService dialogueService;
    private final LlmStreamService llmStreamService;
    private final LlmClient llmClient;
    @Value("${orchestrator.agent.max-concurrency:10}")
    private int agentMaxConcurrency = 10;
    @Value("${orchestrator.heartbeat-seconds:15}")
    private long heartbeatSeconds = 15;

    public OrchestratorService(PromptService promptService,
                               SubAgentDirectoryService directoryService,
                               SubAgentDialogueService dialogueService,
                               LlmStreamService llmStreamService,
                               LlmClient llmClient) {
        this.promptService = promptService;
        this.directoryService = directoryService;
        this.dialogueService = dialogueService;
        this.llmStreamService = llmStreamService;
        this.llmClient = llmClient;
    }

    public Flux<ServerSentEvent<String>> stream(ShoppingRequest request) {
        String profileId = "default";
        String system = promptService.readBuyerPrompt(profileId);
        String user = "需求：" + Optional.ofNullable(request.getRequest()).orElse("")
                + "；目标价格：" + request.getPrice()
                + "；排斥点：" + Optional.ofNullable(request.getDislike()).orElse("");

        // 1. 处理LLM客户端不可用的情况
        if (!(llmClient instanceof RealQwenLlmClient q)) {
            return Flux.just(
                    ServerSentEvent.<String>builder().event("error").data("llm_unavailable").build()
            );
        }

        // 2. 构建internalQuestionMono（仅内部使用，不对外输出）
        String questionFallback = user;
        Mono<String> internalQuestionMono = llmStreamService.streamTokens(q, system, user, "question")
                .map(ev -> ev.data() == null ? "" : ev.data())
                .reduce(new StringBuilder(), StringBuilder::append)
                .map(StringBuilder::toString)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .switchIfEmpty(Mono.just(questionFallback))
                .onErrorReturn(questionFallback)
                .cache();

        // 3. 构建agentInfoFlux（子agent信息流，立即输出）
        Flux<ServerSentEvent<String>> agentInfoFlux = directoryService.fetchAgents("shopping")
                .collectList()
                .onErrorReturn(Collections.emptyList())
                .flatMapMany(list -> {
                    String json = JSON.toJSONString(list);
                    return Flux.just(ServerSentEvent.<String>builder().event("subAgentInfo").data(json).build());
                })
                .publishOn(Schedulers.boundedElastic()); // 立即执行并输出

        // 4. 构建agentReplayFlux（子agent回复流，每完成一个立即输出）
        ConcurrentHashMap<String, String> replayMap = new ConcurrentHashMap<>();
        Sinks.Empty<Void> replayDone = Sinks.empty();
        Flux<ServerSentEvent<String>> agentReplayFlux = internalQuestionMono
                .flatMapMany(questionText -> directoryService.fetchAgents("shopping")
                        // flatMap显式配置并发数（根据你的agent数量调整，确保实时输出）
                        .flatMap(
                                a -> dialogueService.requestOnce(a.getId(), questionText, Map.of(
                                        "request", request.getRequest(),
                                        "price", request.getPrice(),
                                        "dislike", request.getDislike()
                                ))
                                        .onErrorResume(ex -> Mono.empty())
                                        .publishOn(Schedulers.boundedElastic()),
                                normalizePositive(agentMaxConcurrency, 10)
                        )
                )
                .flatMap(dialogueService::toEvents)
                .map(ev -> {
                    if (!"subAgentReplay".equals(ev.event())) {
                        return ev;
                    }
                    try {
                        String data = ev.data();
                        if (data != null && !data.isEmpty()) {
                            Map<?, ?> obj = JSON.parseObject(data, Map.class);
                            Object agentId = obj.get("agentId");
                            Object message = obj.get("message");
                            Object text = obj.get("text");
                            if (message == null) message = text;
                            Object quote = obj.get("quote");
                            Map<String, Object> enriched = new HashMap<>();
                            if (agentId != null) {
                                enriched.put("agentId", agentId);
                            }
                            if (quote != null) {
                                enriched.put("quote", quote);
                            }
                            if (message != null) {
                                enriched.put("message", message);
                            }
                            String json = JSON.toJSONString(enriched);
                            if (agentId != null && message != null && !"DONE".equals(String.valueOf(message))) {
                                replayMap.put(agentId.toString(), json);
                            }
                            return ServerSentEvent.<String>builder().event("subAgentReplay").data(json).build();
                        }
                    } catch (Exception ignored) { }
                    return ev;
                })
                .doFinally(st -> replayDone.tryEmitEmpty())
                .publishOn(Schedulers.boundedElastic()); // 确保实时输出

        // 5. 构建summaryFlux（汇总流：在agentReplayFlux完成后触发）
        Flux<ServerSentEvent<String>> summaryFlux = replayDone.asMono().thenMany(Flux.defer(() -> {
            // 构造判官输入：显性诉求 + <hidden> + <GoodsInfo> + 多个 <subAgentReplay>
            List<String> replays = replayMap.values().stream().toList();
            if (replays.isEmpty()) {
                return Flux.empty();
            }
            // log.info("subAgentReplays are : {}", JSON.toJSONString(replays));
            String judgeSystem = readJudgePrompt();
            String goodsInfo = readGoodsInfo();
            String judgeUser = buildJudgeUserInput(request, goodsInfo, replays);
            if (judgeSystem.isBlank()) {
                return Flux.just(summaryFallbackEvent("missing_judge_prompt", null, judgeUser));
            }
            return llmStreamService.streamTokens(q, judgeSystem, judgeUser, "summary")
                    .onErrorResume(ex -> Flux.just(summaryFallbackEvent("error", safeError(ex), judgeUser)))
                    .switchIfEmpty(Flux.just(summaryFallbackEvent("no_delta", null, judgeUser)));
        })).publishOn(Schedulers.boundedElastic());
        
        // 6. 核心流组合：并行合并实时输出的流 + 汇总流
        Flux<ServerSentEvent<String>> mainFlux = Flux.merge(
                agentInfoFlux,       // 立即输出agent信息
                agentReplayFlux,     // 实时输出子agent回复
                summaryFlux          // 在子agent结束后输出判官总结
        )
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder().event("error").data(safeError(ex)).build()))
                .takeUntil(ev -> "summary".equals(ev.event()) || "error".equals(ev.event()))
                .cache();
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(normalizePositive(heartbeatSeconds, 15)))
                .map(ts -> ServerSentEvent.<String>builder().event("heartbeat").data(String.valueOf(ts)).build())
                .takeUntilOther(mainFlux.then());

        return Flux.merge(heartbeatFlux, mainFlux)
                .concatWith(Mono.fromSupplier(() -> doneEvent(replayMap)));
    }

    private static String safeError(Throwable ex) {
        if (ex == null) return "unknown_error";
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) return type;
        msg = msg.replace("\r", " ").replace("\n", " ").trim();
        if (msg.length() > 200) msg = msg.substring(0, 200);
        return type + ": " + msg;
    }

    private static ServerSentEvent<String> summaryFallbackEvent(String reason, String detail, String chosen) {
        String preview = safePreview(chosen, 200);
        String data = JSON.toJSONString(Map.of(
                "fallback", true,
                "reason", reason == null ? "" : reason,
                "detail", detail == null ? "" : detail,
                "chosenLen", chosen == null ? 0 : chosen.length(),
                "chosenPreview", preview
        ));
        return ServerSentEvent.<String>builder().event("summary").data(data).build();
    }

    private static ServerSentEvent<String> doneEvent(ConcurrentHashMap<String, String> replayMap) {
        int bestQuote = replayMap.values().stream()
                .mapToInt(OrchestratorService::readQuote)
                .filter(v -> v > 0)
                .min()
                .orElse(0);
        String data = JSON.toJSONString(Map.of(
                "type", "done",
                "replyCount", replayMap.size(),
                "bestQuote", bestQuote
        ));
        return ServerSentEvent.<String>builder().event("done").data(data).build();
    }

    private static String safePreview(String s, int maxLen) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ").trim();
        if (maxLen > 0 && v.length() > maxLen) return v.substring(0, maxLen);
        return v;
    }

    private static String buildJudgeUserInput(ShoppingRequest request, String goodsInfo, List<String> replayJsonList) {
        // 汇总显性诉求、潜台词、商品信息与各商家导购回复，供判官提示词核验
        String req = request == null ? "" : Optional.ofNullable(request.getRequest()).orElse("");
        String dislike = request == null ? "" : Optional.ofNullable(request.getDislike()).orElse("");
        String hidden = request == null ? "" : Optional.ofNullable(request.getHidding()).orElse("");
        double price = request == null ? 0 : request.getPrice();

        StringBuilder sb = new StringBuilder();
        sb.append("用户显性输入：意向商品=").append(req).append("；预算=").append(price).append("；喜恶/排斥=").append(dislike).append("\n");
        sb.append("<hidden>").append(hidden).append("</hidden>\n");
        sb.append("<GoodsInfo>\n").append(goodsInfo == null ? "" : goodsInfo).append("\n</GoodsInfo>\n");

        for (String replayJson : replayJsonList) {
            String agentId = "";
            String message = "";
            try {
                Map<?, ?> m = JSON.parseObject(replayJson, Map.class);
                Object id = m.get("agentId");
                if (id == null) id = m.get("id");
                Object msg = m.get("message");
                if (msg == null) msg = m.get("text");
                agentId = id == null ? "" : String.valueOf(id);
                message = msg == null ? "" : String.valueOf(msg);
            } catch (Exception ignored) {
            }
            String payload = JSON.toJSONString(Map.of(
                    "merchantId", agentId,
                    "merchantType", "shopping",
                    "reply", message
            ));
            sb.append("<subAgentReplay>").append(payload).append("</subAgentReplay>\n");
        }
        return sb.toString();
    }

    private static String readFirstExisting(String envName, String... relativePaths) {
        return DataPathResolver.resolveFile(envName, relativePaths)
                .map(p -> {
                    try {
                        return JsonUtils.readString(p);
                    } catch (Exception ignored) {
                        return "";
                    }
                })
                .orElse("");
    }

    private static int normalizePositive(long n, int d) {
        if (n <= 0) {
            return d;
        }
        if (n > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) n;
    }

    private static int readQuote(String replayJson) {
        if (replayJson == null || replayJson.isBlank()) {
            return 0;
        }
        try {
            Map<?, ?> m = JSON.parseObject(replayJson, Map.class);
            Object q = m.get("quote");
            if (q instanceof Number n) {
                return n.intValue();
            }
            if (q instanceof String s) {
                return Integer.parseInt(s);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String readJudgePrompt() {
        return readFirstExisting(
                "JUDGE_PROMPT_FILE",
                Path.of("subagent-service", "data", "baonuanneiyi", "prompts", "judge.txt").toString(),
                Path.of("data", "baonuanneiyi", "prompts", "judge.txt").toString()
        );
    }

    private static String readGoodsInfo() {
        return readFirstExisting(
                "GOODS_INFO_FILE",
                Path.of("subagent-service", "data", "baonuanneiyi", "goodsinfo", "goos.txt").toString(),
                Path.of("data", "baonuanneiyi", "goodsinfo", "goos.txt").toString()
        );
    }
}
