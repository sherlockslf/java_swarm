package com.example.mainservice.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.example.common.dto.ShoppingRequest;
import com.example.common.llm.LlmClient;
import com.example.common.llm.RealQwenLlmClient;
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
                    ServerSentEvent.<String>builder().event("error").data("llm_unavailable").build(),
                    ServerSentEvent.<String>builder().event("done").data("{\"bestQuote\":-1,\"error\":true}").build()
            );
        }

        // 2. 构建publicQuestionFlux（公共问题生成流）
        StringBuilder publicQuestion = new StringBuilder();
        String questionFallback = user;
        Flux<ServerSentEvent<String>> publicQuestionFlux = llmStreamService.streamTokens(q, system, user, "question")
                .doOnNext(ev -> {
                    String d = ev.data();
                    if (d != null) {
                        publicQuestion.append(d);
                    }
                })
                .switchIfEmpty(Flux.defer(() -> Flux.just(questionFallbackEvent(publicQuestion, questionFallback))))
                .onErrorResume(ex -> {
                    if (publicQuestion.length() > 0) {
                        return Flux.empty();
                    }
                    return Flux.just(questionFallbackEvent(publicQuestion, questionFallback));
                })
                .publishOn(Schedulers.boundedElastic()); // 避免阻塞主线程

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
        AtomicInteger bestQuote = new AtomicInteger(Integer.MAX_VALUE);
        final Map<String, String> bestAnswer = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> replayMap = new ConcurrentHashMap<>();
        Sinks.Empty<Void> replayDone = Sinks.empty();
        Flux<ServerSentEvent<String>> agentReplayFlux = Mono.fromSupplier(publicQuestion::toString)
                .flatMapMany(questionText -> directoryService.fetchAgents("shopping")
                        // flatMap显式配置并发数（根据你的agent数量调整，确保实时输出）
                        .flatMap(
                                a -> dialogueService.requestOnce(a.getId(), questionText, Map.of(
                                        "request", request.getRequest(),
                                        "price", request.getPrice(),
                                        "dislike", request.getDislike()
                                ))
                                        .onErrorResume(ex -> Mono.empty())
                                        .publishOn(Schedulers.boundedElastic()), // 每个agent异步执行
                                10 // 并发数：建议根据agent数量调整，避免过高/过低
                        )
                )
                .doOnNext(r -> {
                    // 实时更新最优报价
                    int quote = 0;
                    Object qo = r.getMeta() == null ? null : r.getMeta().get("quote");
                    if (qo instanceof Number n) quote = n.intValue();
                    else if (qo instanceof String s) try { quote = Integer.parseInt(s); } catch (Exception ignored) {}
                    if (quote < bestQuote.get()) {
                        bestQuote.set(quote);
                        bestAnswer.put("text", Optional.ofNullable(r.getAnswer()).orElse(""));
                    }
                })
                .flatMap(dialogueService::toEvents)
                .map(ev -> {
                    // 实时封装子agent回复并输出
                    try {
                        String data = ev.data();
                        if (data != null && !data.isEmpty()) {
                            Map<?, ?> obj = JSON.parseObject(data, Map.class);
                            Object agentId = obj.get("agentId");
                            Object text = obj.get("text");
                            Map<String, Object> enriched = new HashMap<>();
                            enriched.putAll((Map<? extends String, ?>) obj);
                            if (agentId != null) enriched.put("id", agentId);
                            if (text != null) enriched.put("message", text);
                            String json = JSON.toJSONString(enriched);
                            // 只保留“完整回复”，避免被 DONE 标记覆盖导致 summary 无有效上下文
                            if (agentId != null && text != null && !"DONE".equals(String.valueOf(text))) {
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
        Flux<ServerSentEvent<String>> mainFluxCold = Flux.merge(
                agentInfoFlux,       // 立即输出agent信息
                publicQuestionFlux,  // 实时输出公共问题
                agentReplayFlux,     // 实时输出子agent回复
                summaryFlux          // 在子agent结束后输出判官总结
        )
                .concatWith(Mono.fromSupplier(() -> {
                    // 主流程完成后，发送 done（含 bestQuote/error）
                    int best = bestQuote.get() == Integer.MAX_VALUE ? -1 : bestQuote.get();
                    return ServerSentEvent.<String>builder().event("done")
                            .data("{\"bestQuote\":" + best + ",\"error\":false}")
                            .build();
                }))
                .onErrorResume(ex -> {
                    // 错误处理：实时返回错误+done事件
                    int best = bestQuote.get() == Integer.MAX_VALUE ? -1 : bestQuote.get();
                    return Flux.just(
                            ServerSentEvent.<String>builder().event("error").data(safeError(ex)).build(),
                            ServerSentEvent.<String>builder().event("done").data("{\"bestQuote\":" + best + ",\"error\":true}").build()
                    );
                });

        // 7. 心跳流：每15秒发送，直到主流程输出 done 事件
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(ts -> ServerSentEvent.<String>builder().event("heartbeat").data(String.valueOf(ts)).build());

        // 8. 合并心跳流和主流程，遇到 done 后终止（避免额外订阅主流程）
        return Flux.merge(heartbeatFlux, mainFluxCold)
                .takeUntil(ev -> "done".equals(ev.event()));
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

    private static ServerSentEvent<String> questionFallbackEvent(StringBuilder publicQuestion, String fallback) {
        String v = fallback == null ? "" : fallback;
        if (publicQuestion != null && publicQuestion.length() == 0 && !v.isEmpty()) {
            publicQuestion.append(v);
        }
        return ServerSentEvent.<String>builder().event("question").data(v).build();
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

    private static String readFirstExisting(String... relativePaths) {
        // 从 user.dir 开始向上回溯若干层，按候选相对路径顺序读取第一个存在的文件内容（兼容模块目录运行）
        Path base = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 5 && base != null; i++) {
            for (String rel : relativePaths) {
                if (rel == null || rel.isBlank()) continue;
                Path p = base.resolve(rel);
                try {
                    if (Files.exists(p)) {
                        return JsonUtils.readString(p);
                    }
                } catch (Exception ignored) {
                }
            }
            base = base.getParent();
        }
        return "";
    }

    private static String readJudgePrompt() {
        return readFirstExisting(
                Path.of("subagent-service", "data", "baonuanneiyi", "prompts", "judge.txt").toString(),
                Path.of("data", "baonuanneiyi", "prompts", "judge.txt").toString()
        );
    }

    private static String readGoodsInfo() {
        return readFirstExisting(
                Path.of("subagent-service", "data", "baonuanneiyi", "goodsinfo", "goos.txt").toString(),
                Path.of("data", "baonuanneiyi", "goodsinfo", "goos.txt").toString()
        );
    }
}
