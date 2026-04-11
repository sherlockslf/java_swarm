package com.example.mainservice.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

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
    private static final String FOLLOW_UP_DEFAULT = "我希望品质要高一些，但不要为了品牌溢价太多，请给我高品质且溢价可控的方案。";
    private static final int MAX_DIALOGUE_ROUNDS = 3;

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
        String streamId = "stream-" + UUID.randomUUID();
        long streamBeginAt = System.currentTimeMillis();
        String requestText = Optional.ofNullable(request).map(ShoppingRequest::getRequest).orElse("");
        try {
            log.info("Shopping stream begin: streamId={}, request={}, followUp={}",
                    streamId, safePreview(JSON.toJSONString(request), 800),
                    safePreview(Optional.ofNullable(request.getFollowUp()).orElse(""), 200));
        } catch (Exception ignored) {
        }

        if (!(llmClient instanceof RealQwenLlmClient q)) {
            return Flux.just(
                    ServerSentEvent.<String>builder().event("error").data("llm_unavailable").build()
            );
        }

        Mono<String> internalQuestionMono = Mono.just(requestText)
                .map(String::trim)
                .doOnNext(qt -> {
                    try {
                        log.info("Shopping stream question ready: streamId={}, costMs={}, questionLen={}, questionPreview={}",
                                streamId, System.currentTimeMillis() - streamBeginAt, qt.length(), safePreview(qt, 500));
                    } catch (Exception ignored) {
                    }
                })
                .cache();

        Flux<ServerSentEvent<String>> agentInfoFlux = directoryService.fetchAgents("shopping")
                .collectList()
                .onErrorReturn(Collections.emptyList())
                .flatMapMany(list -> {
                    String json = JSON.toJSONString(list);
                    return Flux.just(ServerSentEvent.<String>builder().event("subAgentInfo").data(json).build());
                })
                .publishOn(Schedulers.boundedElastic());

        ConversationContext conversationContext = new ConversationContext(streamId);
        Map<String, BigDecimal> guidePriceMap = buildGuidePriceMap(readGoodsInfo());
        Map<Integer, RoundDecision> roundDecisions = new ConcurrentHashMap<>();
        String goodsInfo = readGoodsInfo();
        String summaySystem = readSummayPrompt();

        Flux<ServerSentEvent<String>> dialogueFlux = internalQuestionMono
                .flatMapMany(questionText -> {
                    try {
                        log.info("Shopping stream round1 start: streamId={}, costMs={}", streamId, System.currentTimeMillis() - streamBeginAt);
                    } catch (Exception ignored) {
                    }
                    Flux<ServerSentEvent<String>> round1 = runRoundWithJudge(
                            1, questionText, request, conversationContext, guidePriceMap,
                            goodsInfo, q, List.of()
                    );
                    Flux<ServerSentEvent<String>> round2 = Flux.defer(() -> shouldContinueRound(1, request, roundDecisions)
                            ? runRoundWithJudge(
                            2,
                            buildFollowUpQuestion(questionText, request, conversationContext, resolveFocusMerchantIds(1, roundDecisions)),
                            request,
                            conversationContext,
                            guidePriceMap,
                            goodsInfo,
                            q,
                            resolveFocusMerchantIds(1, roundDecisions)
                    )
                            : Flux.empty());
                    Flux<ServerSentEvent<String>> round3 = Flux.defer(() -> shouldContinueRound(2, request, roundDecisions)
                            ? runRoundWithJudge(
                            3,
                            buildFollowUpQuestion(questionText, request, conversationContext, resolveFocusMerchantIds(2, roundDecisions)),
                            request,
                            conversationContext,
                            guidePriceMap,
                            goodsInfo,
                            q,
                            resolveFocusMerchantIds(2, roundDecisions)
                    )
                            : Flux.empty());
                    return round1
                            .concatWith(round2)
                            .concatWith(round3)
                            .concatWith(Flux.defer(() -> runSummayForRound(
                                    conversationContext.maxRound(),
                                    request,
                                    goodsInfo,
                                    summaySystem,
                                    conversationContext,
                                    roundDecisions,
                                    q
                            )));
                })
                .publishOn(Schedulers.boundedElastic());

        Flux<ServerSentEvent<String>> mainFlux = Flux.merge(
                agentInfoFlux,
                dialogueFlux
        )
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<String>builder().event("error").data(safeError(ex)).build()))
                .takeUntil(ev -> "error".equals(ev.event()))
                .cache();
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(normalizePositive(heartbeatSeconds, 15)))
                .map(ts -> ServerSentEvent.<String>builder().event("heartbeat").data(String.valueOf(ts)).build())
                .takeUntilOther(mainFlux.then());

        return Flux.merge(heartbeatFlux, mainFlux)
                .concatWith(Mono.fromSupplier(() -> {
                    try {
                        log.info("Shopping stream done: streamId={}, costMs={}, replayCount={}",
                                streamId, System.currentTimeMillis() - streamBeginAt, conversationContext.replayHistoryJson().size());
                    } catch (Exception ignored) {
                    }
                    return doneEvent(conversationContext);
                }));
    }

    private Flux<ServerSentEvent<String>> runRound(
            int round,
            String question,
            ShoppingRequest request,
            ConversationContext conversationContext,
            Map<String, BigDecimal> guidePriceMap,
            List<String> focusMerchantIds) {
        conversationContext.startRound(round, request == null ? "" : safePreview(JSON.toJSONString(request), 500));
        Set<String> focusSet = focusMerchantIds == null ? Set.of() : focusMerchantIds.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return directoryService.fetchAgents("shopping")
                .filter(a -> focusSet.isEmpty() || focusSet.contains(a.getId()))
                .flatMap(a -> requestWithGuard(
                                a.getId(),
                                question,
                                buildContext(request, round, conversationContext),
                                request,
                                guidePriceMap,
                                0
                        )
                                .onErrorResume(ex -> {
                                    try {
                                        log.warn("Shopping stream round request failed: round={}, agentId={}, ex={}", round, a.getId(), ex.toString());
                                    } catch (Exception ignored) {
                                    }
                                    return Mono.empty();
                                })
                                .publishOn(Schedulers.boundedElastic()),
                        normalizePositive(agentMaxConcurrency, 10)
                )
                .flatMap(reply -> {
                    conversationContext.recordAgentReply(round, reply);
                    return dialogueService.toEvents(reply, round);
                })
                .map(ev -> cacheReplay(ev, conversationContext))
                .filter(ev -> !"subAgentDone".equals(ev.event()));
    }

    private Flux<ServerSentEvent<String>> runRoundWithJudge(
            int round,
            String question,
            ShoppingRequest request,
            ConversationContext conversationContext,
            Map<String, BigDecimal> guidePriceMap,
            String goodsInfo,
            RealQwenLlmClient llmClient,
            List<String> focusMerchantIds) {
        return runRound(round, question, request, conversationContext, guidePriceMap, focusMerchantIds)
                .concatMap(ev -> withRealtimeJudge(ev, request, goodsInfo, llmClient));
    }

    private Flux<ServerSentEvent<String>> withRealtimeJudge(
            ServerSentEvent<String> ev,
            ShoppingRequest request,
            String goodsInfo,
            RealQwenLlmClient llmClient) {
        if (!"subAgentReplay".equals(ev.event())) {
            return Flux.just(ev);
        }
        String replayJson = ev.data() == null ? "" : ev.data();
        Flux<ServerSentEvent<String>> judgeFlux = runJudgeForReplay(replayJson, request, goodsInfo, llmClient);
        return Flux.concat(Flux.just(ev), judgeFlux);
    }

    private Flux<ServerSentEvent<String>> runJudgeForReplay(
            String replayJson,
            ShoppingRequest request,
            String goodsInfo,
            RealQwenLlmClient llmClient) {
        if (replayJson == null || replayJson.isBlank()) {
            return Flux.just(judgeFallbackEvent("", "missing_replay", null));
        }
        return dialogueService.requestJudgeOnce(replayJson, request)
                .flatMapMany(data -> {
                    if (data == null || data.isBlank()) {
                        return Flux.just(judgeFallbackEvent(replayJson, "no_delta", null));
                    }
                    return Flux.just(ServerSentEvent.<String>builder().event("judge").data(data).build());
                })
                .onErrorResume(ex -> Flux.just(judgeFallbackEvent(replayJson, "error", safeError(ex))));
    }

    private Flux<ServerSentEvent<String>> runSummayForRound(
            int round,
            ShoppingRequest request,
            String goodsInfo,
            String summaySystem,
            ConversationContext conversationContext,
            Map<Integer, RoundDecision> roundDecisions,
            RealQwenLlmClient llmClient) {
        List<String> roundReplays = conversationContext.roundReplayJson(round);
        if (roundReplays.isEmpty()) {
            RoundDecision decision = defaultRoundDecision(round, request, false, "no_replay");
            roundDecisions.put(round, decision);
            return Flux.just(summayFallbackEvent(round, "no_replay", null, decision));
        }
        if (summaySystem == null || summaySystem.isBlank()) {
            RoundDecision decision = defaultRoundDecision(round, request, false, "missing_summay_prompt");
            roundDecisions.put(round, decision);
            return Flux.just(summayFallbackEvent(round, "missing_summay_prompt", null, decision));
        }
        String summayUser = buildSummayUserInput(request, goodsInfo, round, roundReplays, conversationContext.summaryHistory());
        return Mono.fromSupplier(() -> llmClient.generate(summaySystem, summayUser))
                .flatMapMany(raw -> {
                    if (raw == null || raw.isBlank()) {
                        RoundDecision decision = defaultRoundDecision(round, request, false, "no_delta");
                        roundDecisions.put(round, decision);
                        return Flux.just(summayFallbackEvent(round, "no_delta", null, decision));
                    }
                    RoundDecision decision = parseRoundDecision(raw, round, request);
                    roundDecisions.put(round, decision);
                    return Flux.just(ServerSentEvent.<String>builder().event("summay").data(raw).build());
                })
                .onErrorResume(ex -> {
                    RoundDecision decision = defaultRoundDecision(round, request, false, "error");
                    roundDecisions.put(round, decision);
                    return Flux.just(summayFallbackEvent(round, "error", safeError(ex), decision));
                });
    }

    private boolean shouldContinueRound(int finishedRound, ShoppingRequest request, Map<Integer, RoundDecision> roundDecisions) {
        if (finishedRound >= MAX_DIALOGUE_ROUNDS) {
            return false;
        }
        if (!needFollowUpRound(request)) {
            return false;
        }
        RoundDecision decision = roundDecisions.get(finishedRound);
        if (decision == null) {
            return true;
        }
        return !decision.endConversation;
    }

    private Mono<com.example.common.dto.SubAgentReply> requestWithGuard(
            String agentId,
            String question,
            Map<String, Object> context,
            ShoppingRequest request,
            Map<String, BigDecimal> guidePriceMap,
            int retryTimes) {
        return dialogueService.requestOnce(agentId, question, context)
                .map(reply -> {
                    Map<String, Object> meta = reply.getMeta() == null ? new HashMap<>() : new HashMap<>(reply.getMeta());
                    Map<String, Object> judge = evaluateReply(agentId, meta, request, guidePriceMap);
                    meta.put("judge", judge);
                    List<String> risks = new ArrayList<>(readStringList(meta.get("risk_flags")));
                    risks.addAll(readStringList(judge.get("risk_flags")));
                    meta.put("risk_flags", risks.stream().distinct().toList());
                    reply.setMeta(meta);
                    return reply;
                })
                .flatMap(reply -> {
                    Object judgeObj = reply.getMeta() == null ? null : reply.getMeta().get("judge");
                    Object severeValue = judgeObj instanceof Map<?, ?> jm ? jm.get("severe") : null;
                    boolean severe = readBoolean(severeValue);
                    if (severe && retryTimes < 1) {
                        Map<String, Object> retryContext = new HashMap<>(context);
                        retryContext.put("riskFeedback", reply.getMeta().get("judge"));
                        retryContext.put("retry", true);
                        return requestWithGuard(agentId, question, retryContext, request, guidePriceMap, retryTimes + 1);
                    }
                    return Mono.just(reply);
                });
    }

    private Map<String, Object> evaluateReply(String agentId, Map<String, Object> meta, ShoppingRequest request, Map<String, BigDecimal> guidePriceMap) {
        List<String> riskFlags = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        BigDecimal budget = BigDecimal.valueOf(request == null ? 0 : request.getPrice());
        BigDecimal quote = toDecimal(meta.get("quote"), BigDecimal.ZERO);
        BigDecimal guide = toDecimal(meta.get("guide_price"), BigDecimal.ZERO);
        String productId = String.valueOf(meta.getOrDefault("product_id", ""));
        if (guide.compareTo(BigDecimal.ZERO) <= 0 && !productId.isBlank()) {
            guide = guidePriceMap.getOrDefault(productId, BigDecimal.ZERO);
        }
        Map<?, ?> coupon = meta.get("coupon_info") instanceof Map<?, ?> m ? m : Map.of();
        BigDecimal original = toDecimal(coupon.get("original_price"), BigDecimal.ZERO);
        BigDecimal afterCoupon = toDecimal(coupon.get("after_coupon_price"), quote);
        String subsidyBy = String.valueOf(mapValue(coupon, "subsidy_by", "")).trim();
        Object stackable = coupon.get("stackable");
        if (quote.compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("报价缺失或无效");
            riskFlags.add("invalid_quote");
        }
        if (budget.compareTo(BigDecimal.ZERO) > 0 && quote.compareTo(budget) > 0) {
            reasons.add("报价超过预算");
            riskFlags.add("over_budget");
        }
        if (original.compareTo(BigDecimal.ZERO) <= 0 || afterCoupon.compareTo(BigDecimal.ZERO) <= 0 || subsidyBy.isBlank() || stackable == null) {
            reasons.add("价格体系字段不完整");
            riskFlags.add("price_fields_missing");
        }
        if (original.compareTo(BigDecimal.ZERO) > 0 && afterCoupon.compareTo(original) > 0) {
            reasons.add("券后价高于原价");
            riskFlags.add("coupon_inconsistent");
        }
        if (guide.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal upper = guide.multiply(BigDecimal.valueOf(1.15));
            BigDecimal lower = guide.multiply(BigDecimal.valueOf(0.45));
            if (quote.compareTo(upper) > 0 || quote.compareTo(lower) < 0) {
                reasons.add("报价与真实价格体系偏离");
                riskFlags.add("price_outlier");
            }
        }
        String rawAnswer = String.valueOf(meta.getOrDefault("rawAnswer", ""));
        if (containsRiskText(rawAnswer)) {
            reasons.add("命中高风险营销话术");
            riskFlags.add("marketing_risk");
        }
        boolean severe = riskFlags.stream().anyMatch(v ->
                "over_budget".equals(v)
                        || "invalid_quote".equals(v)
                        || "price_outlier".equals(v)
                        || "marketing_risk".equals(v)
        );
        Map<String, Object> judge = new HashMap<>();
        judge.put("agentId", agentId);
        judge.put("pass", riskFlags.isEmpty());
        judge.put("severe", severe);
        judge.put("reasons", reasons);
        judge.put("risk_flags", riskFlags);
        return judge;
    }

    private ServerSentEvent<String> cacheReplay(ServerSentEvent<String> ev, ConversationContext conversationContext) {
        if (!"subAgentReplay".equals(ev.event())) {
            return ev;
        }
        try {
            String data = ev.data();
            if (data != null && !data.isEmpty()) {
                conversationContext.recordReplayJson(data);
            }
        } catch (Exception ignored) {
        }
        return ev;
    }

    private Map<String, Object> buildContext(ShoppingRequest request, int round, ConversationContext conversationContext) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("request", request == null ? "" : request.getRequest());
        ctx.put("price", request == null ? 0 : request.getPrice());
        ctx.put("dislike", request == null ? "" : request.getDislike());
        ctx.put("hidding", request == null ? "" : request.getHidding());
        ctx.put("quantity", request == null || request.getQuantity() <= 0 ? 1 : request.getQuantity());
        ctx.put("bundleIntent", request != null && request.isBundleIntent());
        ctx.put("round", round);
        if (round > 1) {
            ctx.put("followUp", Optional.ofNullable(request.getFollowUp()).filter(v -> !v.isBlank()).orElse(FOLLOW_UP_DEFAULT));
            ctx.put("previousReplies", conversationContext.roundReplayJson(1));
            ctx.put("competitorReplies", conversationContext.roundReplayJson(1));
            ctx.put("roundHistory", conversationContext.summaryHistory());
        }
        return ctx;
    }

    private boolean needFollowUpRound(ShoppingRequest request) {
        if (request == null || request.isDecided()) {
            return false;
        }
        String req = Optional.ofNullable(request.getRequest()).orElse("");
        return !req.contains("我决定好了要买谁");
    }

    private String buildFollowUpQuestion(String questionText, ShoppingRequest request, ConversationContext conversationContext, List<String> focusMerchantIds) {
        String follow = request == null ? FOLLOW_UP_DEFAULT : Optional.ofNullable(request.getFollowUp()).filter(v -> !v.isBlank()).orElse(FOLLOW_UP_DEFAULT);
        int latestRound = conversationContext.maxRound();
        String previous = conversationContext.roundReplayJson(latestRound).stream().sorted(Comparator.naturalOrder()).reduce("", (a, b) -> a + "\n" + b);
        String focus = "";
        if (focusMerchantIds != null && !focusMerchantIds.isEmpty()) {
            focus = "\n本轮仅针对以下商家继续追问：" + String.join("、", focusMerchantIds);
        }
        return questionText + "\n用户追问：" + follow + focus + "\n上一轮各商家回复：" + previous;
    }

    private boolean containsRiskText(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        List<String> risky = List.of("品质远超同价位", "补偿券", "24小时闪电发货", "次日达", "品质保障加成");
        for (String s : risky) {
            if (raw.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readStringList(Object raw) {
        if (raw instanceof List<?> arr) {
            return arr.stream().map(String::valueOf).toList();
        }
        if (raw == null) {
            return List.of();
        }
        String one = String.valueOf(raw).trim();
        if (one.isBlank()) {
            return List.of();
        }
        return List.of(one);
    }

    private static boolean readBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return false;
    }

    private static BigDecimal toDecimal(Object raw, BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof BigDecimal b) {
            return b;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = String.valueOf(raw).trim();
        if (s.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Map<String, BigDecimal> buildGuidePriceMap(String goodsInfo) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            List<Map> goods = JSON.parseArray(goodsInfo, Map.class);
            for (Map<?, ?> g : goods) {
                String skuId = String.valueOf(mapValue(g, "skuId", mapValue(g, "商品id", "")));
                BigDecimal price = toDecimal(
                        g.get("指导价"),
                        toDecimal(g.get("当前指导价"), toDecimal(g.get("biddingPrice"), BigDecimal.ZERO))
                );
                if (!skuId.isBlank() && price.compareTo(BigDecimal.ZERO) > 0) {
                    map.put(skuId, price);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
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

    private static ServerSentEvent<String> judgeFallbackEvent(String replayJson, String reason, String detail) {
        String merchantId = "";
        try {
            Map<?, ?> obj = JSON.parseObject(replayJson, Map.class);
            Object id = obj.get("subAgentId");
            if (id == null) {
                id = obj.get("merchantId");
            }
            if (id == null) {
                id = obj.get("agentId");
            }
            merchantId = id == null ? "" : String.valueOf(id);
        } catch (Exception ignored) {
        }
        String advice = "请重试本条判定";
        if ("missing_judge_prompt".equals(reason)) {
            advice = "请检查并补齐judge提示词文件";
        } else if ("missing_replay".equals(reason)) {
            advice = "请检查商家回复事件是否完整";
        } else if ("no_delta".equals(reason)) {
            advice = "判官模型未返回有效内容，请重试";
        } else if ("error".equals(reason)) {
            advice = "判官模型调用异常，请稍后重试";
        }
        String data = JSON.toJSONString(Map.of(
                "merchantId", merchantId,
                "decision", "retry",
                "pass", false,
                "severe", false,
                "risk_flags", List.of(reason == null ? "unknown" : reason),
                "reasons", detail == null || detail.isBlank() ? List.of(reason == null ? "judge_fallback" : reason) : List.of(reason + ":" + detail),
                "fix_advice", List.of(advice),
                "score", 0.0
        ));
        return ServerSentEvent.<String>builder().event("judge").data(data).build();
    }

    private static ServerSentEvent<String> summayFallbackEvent(int round, String reason, String detail, RoundDecision decision) {
        String data = JSON.toJSONString(Map.of(
                "round", round,
                "endConversation", decision.endConversation,
                "hasCandidate", decision.hasCandidate,
                "decisionReason", decision.reason,
                "reason", reason == null ? "unknown" : reason,
                "detail", detail == null ? "" : detail,
                "review", "本轮总结采用兜底结果，请根据实时judge与商家回复继续判断。"
        ));
        return ServerSentEvent.<String>builder().event("summay").data(data).build();
    }

    private static ServerSentEvent<String> doneEvent(ConversationContext conversationContext) {
        int bestQuote = conversationContext.roundReplayJson(conversationContext.maxRound()).stream()
                .mapToInt(OrchestratorService::readQuote)
                .filter(v -> v > 0)
                .min()
                .orElseGet(() -> conversationContext.replayHistoryJson().stream()
                        .mapToInt(OrchestratorService::readQuote)
                        .filter(v -> v > 0)
                        .min()
                        .orElse(0));
        String data = JSON.toJSONString(Map.of(
                "type", "done",
                "replyCount", conversationContext.lastRoundReplyCount(),
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
        String req = request == null ? "" : Optional.ofNullable(request.getRequest()).orElse("");
        String dislike = request == null ? "" : Optional.ofNullable(request.getDislike()).orElse("");
        String hidden = request == null ? "" : Optional.ofNullable(request.getHidding()).orElse("");
        String followUp = request == null ? "" : Optional.ofNullable(request.getFollowUp()).orElse("");
        double price = request == null ? 0 : request.getPrice();

        StringBuilder sb = new StringBuilder();
        sb.append("用户显性输入：意向商品=").append(req).append("；预算=").append(price).append("；喜恶/排斥=").append(dislike).append("\n");
        if (!followUp.isBlank()) {
            sb.append("用户追问：").append(followUp).append("\n");
        }
        sb.append("<hidden>").append(hidden).append("</hidden>\n");
        sb.append("<GoodsInfo>\n").append(filterGoodsInfoByReplay(goodsInfo, replayJsonList)).append("\n</GoodsInfo>\n");

        for (String replayJson : replayJsonList) {
            String agentId = "";
            String message = "";
            Object quote = "";
            Object productId = "";
            Object tags = List.of();
            Object couponInfo = Map.of();
            try {
                Map<?, ?> m = JSON.parseObject(replayJson, Map.class);
                Object id = m.get("subAgentId");
                if (id == null) id = m.get("id");
                if (id == null) id = m.get("agentId");
                Object msg = m.get("message");
                if (msg == null) msg = m.get("text");
                quote = mapValue(m, "quote", "");
                productId = mapValue(m, "skuId", "");
                if (String.valueOf(productId).isBlank()) {
                    productId = mapValue(m, "product_id", "");
                }
                tags = mapValue(m, "tag", List.of());
                couponInfo = mapValue(m, "coupon", Map.of());
                if (couponInfo instanceof Map<?, ?> cm && cm.isEmpty()) {
                    couponInfo = mapValue(m, "coupon_info", Map.of());
                }
                agentId = id == null ? "" : String.valueOf(id);
                message = msg == null ? "" : String.valueOf(msg);
            } catch (Exception ignored) {
            }
            String payload = JSON.toJSONString(Map.of(
                    "merchantId", agentId,
                    "merchantType", "shopping",
                    "reply", message,
                    "quote", quote,
                    "productId", productId,
                    "tag", tags,
                    "couponInfo", couponInfo
            ));
            sb.append("<subAgentReplay>").append(payload).append("</subAgentReplay>\n");
        }
        return sb.toString();
    }

    private static String filterGoodsInfoByReplay(String goodsInfo, List<String> replayJsonList) {
        if (goodsInfo == null || goodsInfo.isBlank() || replayJsonList == null || replayJsonList.isEmpty()) {
            return goodsInfo == null ? "" : goodsInfo;
        }
        Set<String> skuIds = new HashSet<>();
        for (String replayJson : replayJsonList) {
            if (replayJson == null || replayJson.isBlank()) {
                continue;
            }
            try {
                Map<?, ?> replay = JSON.parseObject(replayJson, Map.class);
                String sku = String.valueOf(mapValue(replay, "skuId", mapValue(replay, "product_id", ""))).trim();
                if (!sku.isBlank()) {
                    skuIds.add(sku);
                }
            } catch (Exception ignored) {
            }
        }
        if (skuIds.isEmpty()) {
            return goodsInfo;
        }
        try {
            List<Map> goods = JSON.parseArray(goodsInfo, Map.class);
            List<Map> matched = new ArrayList<>();
            for (Map<?, ?> g : goods) {
                String sku = String.valueOf(mapValue(g, "skuId", mapValue(g, "商品id", ""))).trim();
                if (skuIds.contains(sku)) {
                    matched.add((Map) g);
                }
            }
            if (!matched.isEmpty()) {
                return JSON.toJSONString(matched);
            }
        } catch (Exception ignored) {
        }
        return goodsInfo;
    }

    private static String buildSummayUserInput(
            ShoppingRequest request,
            String goodsInfo,
            int round,
            List<String> roundReplayJson,
            String roundHistory) {
        String req = request == null ? "" : Optional.ofNullable(request.getRequest()).orElse("");
        String dislike = request == null ? "" : Optional.ofNullable(request.getDislike()).orElse("");
        String hidden = request == null ? "" : Optional.ofNullable(request.getHidding()).orElse("");
        String followUp = request == null ? "" : Optional.ofNullable(request.getFollowUp()).orElse("");
        double price = request == null ? 0 : request.getPrice();

        StringBuilder sb = new StringBuilder();
        sb.append("当前轮次=").append(round).append("\n");
        sb.append("用户显性输入：意向商品=").append(req).append("；预算=").append(price).append("；喜恶/排斥=").append(dislike).append("\n");
        if (!followUp.isBlank()) {
            sb.append("用户追问：").append(followUp).append("\n");
        }
        sb.append("<hidden>").append(hidden).append("</hidden>\n");
        sb.append("<GoodsInfo>\n").append(goodsInfo == null ? "" : goodsInfo).append("\n</GoodsInfo>\n");
        sb.append("<RoundHistory>\n").append(roundHistory == null ? "" : roundHistory).append("\n</RoundHistory>\n");
        for (String replayJson : roundReplayJson) {
            sb.append("<subAgentReplay>").append(replayJson == null ? "" : replayJson).append("</subAgentReplay>\n");
        }
        return sb.toString();
    }

    private static RoundDecision defaultRoundDecision(int round, ShoppingRequest request, boolean hasCandidate, String reason) {
        boolean end = hasCandidate || round >= MAX_DIALOGUE_ROUNDS || !needNextRoundByRequest(request);
        return new RoundDecision(end, hasCandidate, reason == null ? "" : reason, List.of(), "");
    }

    private static RoundDecision parseRoundDecision(String raw, int round, ShoppingRequest request) {
        boolean hasCandidate = false;
        boolean endConversation = round >= MAX_DIALOGUE_ROUNDS || !needNextRoundByRequest(request);
        String reason = "continue";
        List<String> focusMerchantIds = new ArrayList<>();
        String candidateMerchantId = "";
        BigDecimal highestScore = BigDecimal.ZERO;
        if (raw != null && !raw.isBlank()) {
            String content = raw.trim();
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }
            try {
                Map<?, ?> parsed = JSON.parseObject(content, Map.class);
                hasCandidate = toBool(mapValue(parsed, "hasCandidate", false)) || toBool(mapValue(parsed, "foundCandidate", false));
                endConversation = toBool(mapValue(parsed, "endConversation", endConversation))
                        || toBool(mapValue(parsed, "decided", false))
                        || toBool(mapValue(parsed, "shouldEnd", false))
                        || toBool(mapValue(parsed, "end", false));
                Object rs = mapValue(parsed, "decisionReason", mapValue(parsed, "reason", reason));
                reason = rs == null ? reason : String.valueOf(rs);
                Object candidateId = mapValue(parsed, "candidateMerchantId", "");
                candidateMerchantId = candidateId == null ? "" : String.valueOf(candidateId).trim();
                Object reviewsObj = mapValue(parsed, "merchantReviews", List.of());
                if (reviewsObj instanceof List<?> reviews) {
                    for (Object item : reviews) {
                        if (!(item instanceof Map<?, ?> review)) {
                            continue;
                        }
                        String merchantId = String.valueOf(mapValue(review, "merchantId", "")).trim();
                        BigDecimal score = toDecimal(mapValue(review, "score", 0), BigDecimal.ZERO);
                        if (merchantId.isBlank()) {
                            continue;
                        }
                        if (score.compareTo(BigDecimal.valueOf(80)) > 0) {
                            focusMerchantIds.add(merchantId);
                        }
                        if (score.compareTo(highestScore) > 0) {
                            highestScore = score;
                            if (candidateMerchantId.isBlank()) {
                                candidateMerchantId = merchantId;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                reason = "parse_failed";
            }
        }
        if (!focusMerchantIds.isEmpty()) {
            focusMerchantIds = focusMerchantIds.stream().distinct().toList();
        }
        if (highestScore.compareTo(BigDecimal.valueOf(90)) > 0) {
            hasCandidate = true;
            endConversation = true;
            if (reason.isBlank() || "continue".equals(reason)) {
                reason = "score_above_90_recommend";
            }
        }
        if (hasCandidate) {
            endConversation = true;
            reason = reason.isBlank() ? "candidate_found" : reason;
        }
        if (round >= MAX_DIALOGUE_ROUNDS && !hasCandidate) {
            endConversation = true;
            if (reason.isBlank() || "continue".equals(reason)) {
                reason = "max_round_reached";
            }
        }
        return new RoundDecision(endConversation, hasCandidate, reason, focusMerchantIds, candidateMerchantId);
    }

    private List<String> resolveFocusMerchantIds(int finishedRound, Map<Integer, RoundDecision> roundDecisions) {
        RoundDecision decision = roundDecisions == null ? null : roundDecisions.get(finishedRound);
        if (decision == null || decision.focusMerchantIds == null || decision.focusMerchantIds.isEmpty()) {
            return List.of();
        }
        return decision.focusMerchantIds;
    }

    private static boolean toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            String v = s.trim();
            return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
        }
        return false;
    }

    private static boolean needNextRoundByRequest(ShoppingRequest request) {
        if (request == null || request.isDecided()) {
            return false;
        }
        String req = Optional.ofNullable(request.getRequest()).orElse("");
        return !req.contains("我决定好了要买谁");
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

    private static Object mapValue(Map<?, ?> m, String key, Object fallback) {
        if (m == null || key == null) {
            return fallback;
        }
        Object v = m.get(key);
        return v == null ? fallback : v;
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

    private static String readSummayPrompt() {
        return readFirstExisting(
                "SUMMAY_PROMPT_FILE",
                Path.of("main-service", "data", "prompts", "summary", "summary.txt").toString(),
                Path.of("data", "prompts", "summary", "summary.txt").toString(),
                Path.of("main-service", "data", "prompts", "summary", "summay.txt").toString(),
                Path.of("data", "prompts", "summary", "summay.txt").toString()
        );
    }

    private static String readGoodsInfo() {
        return readFirstExisting(
                "GOODS_INFO_FILE",
                Path.of("common", "goodsInfo", "data-mapped.json").toString(),
                Path.of("subagent-service", "data", "baonuanneiyi", "goodsinfo", "goos.txt").toString(),
                Path.of("data", "baonuanneiyi", "goodsinfo", "goos.txt").toString()
        );
    }

    private static class ConversationContext {
        private final String streamId;
        private final List<RoundSnapshot> rounds = new ArrayList<>();

        private ConversationContext(String streamId) {
            this.streamId = streamId;
        }

        private void startRound(int round, String userInputPreview) {
            rounds.add(new RoundSnapshot(round, userInputPreview));
        }

        private void recordAgentReply(int round, com.example.common.dto.SubAgentReply reply) {
            RoundSnapshot snapshot = getRound(round);
            if (snapshot == null) {
                return;
            }
            snapshot.agentIds.add(reply.getAgentId());
            Object judgeObj = reply.getMeta() == null ? null : reply.getMeta().get("judge");
            String judgeSummary = "";
            if (judgeObj instanceof Map<?, ?> jm) {
                Object pass = jm.get("pass");
                Object reasons = jm.get("reasons");
                judgeSummary = "pass=" + pass + ";reasons=" + reasons;
            }
            snapshot.judgeSummaries.add(reply.getAgentId() + ":" + judgeSummary);
        }

        private void recordReplayJson(String replayJson) {
            if (replayJson == null || replayJson.isBlank()) {
                return;
            }
            int round = 1;
            try {
                Map<?, ?> obj = JSON.parseObject(replayJson, Map.class);
                Object r = obj.get("round");
                if (r instanceof Number n) {
                    round = n.intValue();
                } else if (r instanceof String s && !s.isBlank()) {
                    round = Integer.parseInt(s);
                }
            } catch (Exception ignored) {
            }
            RoundSnapshot snapshot = getRound(round);
            if (snapshot == null) {
                snapshot = new RoundSnapshot(round, "");
                rounds.add(snapshot);
            }
            snapshot.replayJson.add(replayJson);
        }

        private List<String> replayHistoryJson() {
            return rounds.stream()
                    .sorted((a, b) -> Integer.compare(a.round, b.round))
                    .flatMap(r -> r.replayJson.stream())
                    .toList();
        }

        private List<String> roundReplayJson(int round) {
            RoundSnapshot snapshot = getRound(round);
            if (snapshot == null) {
                return List.of();
            }
            return List.copyOf(snapshot.replayJson);
        }

        private int maxRound() {
            return rounds.stream().mapToInt(v -> v.round).max().orElse(1);
        }

        private String summaryHistory() {
            StringBuilder sb = new StringBuilder();
            for (RoundSnapshot round : rounds) {
                sb.append("Round-").append(round.round).append(" 用户输入摘要: ").append(round.userInputPreview).append("\n");
                for (String judgeSummary : round.judgeSummaries) {
                    sb.append("  ").append(judgeSummary).append("\n");
                }
            }
            return sb.toString();
        }

        private int lastRoundReplyCount() {
            if (rounds.isEmpty()) {
                return 0;
            }
            int maxRound = rounds.stream().mapToInt(r -> r.round).max().orElse(1);
            RoundSnapshot snapshot = getRound(maxRound);
            if (snapshot == null) {
                return 0;
            }
            return (int) snapshot.replayJson.stream()
                    .map(v -> {
                        try {
                            Map<?, ?> m = JSON.parseObject(v, Map.class);
                            Object id = m.get("subAgentId");
                            if (id == null) {
                                id = m.get("agentId");
                            }
                            return id == null ? "" : String.valueOf(id);
                        } catch (Exception ignored) {
                            return "";
                        }
                    })
                    .filter(v -> !v.isBlank())
                    .distinct()
                    .count();
        }

        private String renderFallbackSummary(String reason, String detail) {
            StringBuilder sb = new StringBuilder();
            sb.append("全局审视（兜底输出）\n");
            sb.append("原因：").append(reason == null ? "unknown" : reason);
            if (detail != null && !detail.isBlank()) {
                sb.append("；细节：").append(detail);
            }
            sb.append("\n");
            for (RoundSnapshot round : rounds) {
                sb.append("Round-").append(round.round).append("：");
                if (round.replayJson.isEmpty()) {
                    sb.append("无商家回复\n");
                    continue;
                }
                List<String> line = new ArrayList<>();
                for (String replay : round.replayJson) {
                    try {
                        Map<?, ?> m = JSON.parseObject(replay, Map.class);
                        Object id = m.get("subAgentId");
                        if (id == null) {
                            id = m.get("agentId");
                        }
                        line.add(String.valueOf(id) + "(报价=" + String.valueOf(m.get("quote")) + ")");
                    } catch (Exception ignored) {
                    }
                }
                sb.append(String.join("、", line)).append("\n");
            }
            int best = replayHistoryJson().stream().mapToInt(OrchestratorService::readQuote).filter(v -> v > 0).min().orElse(0);
            sb.append("建议：优先关注预算内且单Agent判定通过的最低报价方案；当前最低报价=").append(best).append("。");
            return sb.toString();
        }

        private RoundSnapshot getRound(int round) {
            for (RoundSnapshot snapshot : rounds) {
                if (snapshot.round == round) {
                    return snapshot;
                }
            }
            return null;
        }
    }

    private static class RoundSnapshot {
        private final int round;
        private final String userInputPreview;
        private final List<String> replayJson = new ArrayList<>();
        private final List<String> judgeSummaries = new ArrayList<>();
        private final List<String> agentIds = new ArrayList<>();

        private RoundSnapshot(int round, String userInputPreview) {
            this.round = round;
            this.userInputPreview = userInputPreview == null ? "" : userInputPreview;
        }
    }

    private static class RoundDecision {
        private final boolean endConversation;
        private final boolean hasCandidate;
        private final String reason;
        private final List<String> focusMerchantIds;
        private final String candidateMerchantId;

        private RoundDecision(boolean endConversation, boolean hasCandidate, String reason, List<String> focusMerchantIds, String candidateMerchantId) {
            this.endConversation = endConversation;
            this.hasCandidate = hasCandidate;
            this.reason = reason == null ? "" : reason;
            this.focusMerchantIds = focusMerchantIds == null ? List.of() : List.copyOf(focusMerchantIds);
            this.candidateMerchantId = candidateMerchantId == null ? "" : candidateMerchantId;
        }
    }
}
