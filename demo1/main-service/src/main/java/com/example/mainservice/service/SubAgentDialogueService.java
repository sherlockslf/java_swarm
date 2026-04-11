package com.example.mainservice.service;

import com.alibaba.fastjson2.JSON;
import com.example.common.dto.ShoppingRequest;
import com.example.common.dto.SubAgentJudgeQuery;
import com.example.common.dto.SubAgentQuery;
import com.example.common.dto.SubAgentReply;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Log4j2
@Service
public class SubAgentDialogueService {
    private final WebClient webClient;
    private final String baseUrl;

    public SubAgentDialogueService(WebClient webClient,
                                   @Value("${subagent.base-url:http://localhost:9091}") String baseUrl) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
    }

    public Mono<SubAgentReply> requestOnce(String agentId, String question, Map<String, Object> context) {
        String reqId = "subagent-" + UUID.randomUUID();
        long beginAt = System.currentTimeMillis();
        SubAgentQuery qy = new SubAgentQuery();
        qy.setQuestion(question);
        qy.setContext(context);
        try {
            log.info("SubAgent request begin: reqId={}, time={}, agentId={}, questionLen={}, context={}",
                    reqId, Instant.ofEpochMilli(beginAt), agentId, question == null ? 0 : question.length(), preview(JSON.toJSONString(context), 800));
        } catch (Exception ignored) {}
        return webClient.post()
                .uri(baseUrl + "/api/subagents/" + agentId + "/reply")
                .bodyValue(qy)
                .retrieve()
                .bodyToMono(SubAgentReply.class)
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(r -> {
                    try {
                        log.info("SubAgent request done: reqId={}, agentId={}, costMs={}, answerPreview={}, meta={}",
                                reqId, agentId, System.currentTimeMillis() - beginAt,
                                preview(r == null ? "" : r.getAnswer(), 500),
                                preview(JSON.toJSONString(r == null ? null : r.getMeta()), 800));
                    } catch (Exception ignored) {}
                })
                .doOnError(ex -> log.warn("SubAgent request failed: reqId={}, agentId={}, costMs={}, ex={}",
                        reqId, agentId, System.currentTimeMillis() - beginAt, ex.toString()));
    }

    public Mono<String> requestJudgeOnce(String replayJson, ShoppingRequest request) {
        String reqId = "judge-" + UUID.randomUUID();
        long beginAt = System.currentTimeMillis();
        SubAgentJudgeQuery query = new SubAgentJudgeQuery();
        query.setReplayJson(replayJson);
        query.setRequest(request);
        try {
            log.info("SubAgent judge begin: reqId={}, replayLen={}", reqId, replayJson == null ? 0 : replayJson.length());
        } catch (Exception ignored) {
        }
        return webClient.post()
                .uri(baseUrl + "/api/subagents/judge")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .doOnSuccess(r -> {
                    try {
                        log.info("SubAgent judge done: reqId={}, costMs={}, dataPreview={}",
                                reqId, System.currentTimeMillis() - beginAt, preview(r, 500));
                    } catch (Exception ignored) {
                    }
                })
                .doOnError(ex -> log.warn("SubAgent judge failed: reqId={}, costMs={}, ex={}",
                        reqId, System.currentTimeMillis() - beginAt, ex.toString()));
    }

    public Flux<ServerSentEvent<String>> toEvents(SubAgentReply r) {
        return toEvents(r, 1);
    }

    public Flux<ServerSentEvent<String>> toEvents(SubAgentReply r, int round) {
        double quote = 0;
        Object qo = r.getMeta() == null ? null : r.getMeta().get("quote");
        if (qo instanceof Number n) quote = n.doubleValue();
        else if (qo instanceof String s) try { quote = Double.parseDouble(s); } catch (Exception ignored) {}
        double price = resolvePrice(r == null ? null : r.getMeta(), quote);
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("subAgentId", r.getAgentId());
        payloadMap.put("quote", quote);
        payloadMap.put("round", round);
        if (r.getMeta() != null) {
            payloadMap.put("tag", toStringList(r.getMeta().get("reason_tags")));
            payloadMap.put("skuId", r.getMeta().getOrDefault("product_id", ""));
            payloadMap.put("coupon", r.getMeta().getOrDefault("coupon_info", Map.of()));
        } else {
            payloadMap.put("tag", List.of());
            payloadMap.put("skuId", "");
            payloadMap.put("coupon", Map.of());
        }
        payloadMap.put("price", price);
        payloadMap.put("message", renderMessage(payloadMap, r.getAnswer()));
        String payload = JSON.toJSONString(payloadMap);
        String done = JSON.toJSONString(Map.of(
                "agentId", r.getAgentId(),
                "round", round,
                "done", true
        ));
        return Flux.just(
                ServerSentEvent.<String>builder().event("subAgentReplay").data(payload).build(),
                ServerSentEvent.<String>builder().event("subAgentDone").data(done).build()
        );
    }

    private String renderMessage(Map<String, Object> payloadMap, String fallbackAnswer) {
        String productId = String.valueOf(payloadMap.getOrDefault("skuId", ""));
        double quote = 0;
        Object q = payloadMap.get("quote");
        if (q instanceof Number n) {
            quote = n.doubleValue();
        }
        List<String> tags = toStringList(payloadMap.get("tag"));
        String couponText = renderCoupon(payloadMap.get("coupon"));
        String reason = tags.isEmpty() ? "匹配你的核心需求" : String.join("、", tags);
        String text = "推荐商品ID " + productId + "，当前报价 " + quote + " 元，理由：" + reason + "。"
                + (couponText.isBlank() ? "" : "优惠信息：" + couponText + "。");
        if (productId.isBlank() || quote <= 0) {
            return fallbackAnswer == null ? "" : fallbackAnswer;
        }
        return text;
    }

    private String renderCoupon(Object couponInfo) {
        if (!(couponInfo instanceof Map<?, ?> map)) {
            return "";
        }
        String subsidy = String.valueOf(mapValue(map, "subsidy_by", ""));
        String desc = String.valueOf(mapValue(map, "coupon_desc", ""));
        String original = String.valueOf(mapValue(map, "original_price", ""));
        String after = String.valueOf(mapValue(map, "after_coupon_price", ""));
        String stackable = String.valueOf(mapValue(map, "stackable", ""));
        return "原价" + original + "，券后" + after + "，补贴主体" + subsidy + "，可叠加=" + stackable + "，说明：" + desc;
    }

    private List<String> toStringList(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> arr) {
            for (Object o : arr) {
                if (o != null) {
                    String s = String.valueOf(o).trim();
                    if (!s.isBlank()) {
                        list.add(s);
                    }
                }
            }
        } else if (raw != null) {
            String s = String.valueOf(raw).trim();
            if (!s.isBlank()) {
                list.add(s);
            }
        }
        return list;
    }

    private String preview(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String v = text.replace("\r", " ").replace("\n", " ").trim();
        if (maxLen > 0 && v.length() > maxLen) {
            return v.substring(0, maxLen) + "...";
        }
        return v;
    }

    private Object mapValue(Map<?, ?> map, String key, Object fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        Object v = map.get(key);
        return v == null ? fallback : v;
    }

    private double resolvePrice(Map<String, Object> meta, double quote) {
        if (meta == null) {
            return quote;
        }
        Object priceObj = meta.get("price");
        if (priceObj instanceof Number n) {
            return n.doubleValue();
        }
        if (priceObj instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {
            }
        }
        Object couponObj = meta.get("coupon_info");
        if (couponObj instanceof Map<?, ?> coupon) {
            Object afterCoupon = mapValue(coupon, "after_coupon_price", null);
            if (afterCoupon instanceof Number n) {
                return n.doubleValue();
            }
            if (afterCoupon instanceof String s) {
                try {
                    return Double.parseDouble(s);
                } catch (Exception ignored) {
                }
            }
        }
        return quote;
    }
}
