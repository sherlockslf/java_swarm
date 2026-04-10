package com.example.subagentservice.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.common.dto.ShoppingRequest;
import com.example.common.dto.SubAgentJudgeQuery;
import com.example.common.dto.SubAgentInfo;
import com.example.common.dto.SubAgentQuery;
import com.example.common.dto.SubAgentReply;
import com.example.common.llm.LlmClient;
import com.example.subagentservice.model.SubAgentDef;
import com.example.subagentservice.repo.SubAgentRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@RestController
@RequestMapping("/api")
@Validated
public class SubAgentController {
    private static final Pattern PRICE_AFTER_LABEL = Pattern.compile("(?:报价|价格)\\s*[=:：]?\\s*(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern PRICE_WITH_YUAN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*元");
    private final SubAgentRepository repository;
    private final LlmClient llmClient;

    public SubAgentController(SubAgentRepository repository, LlmClient llmClient) {
        this.repository = repository;
        this.llmClient = llmClient;
    }

    @GetMapping("/subagents")
    public List<SubAgentInfo> list(@RequestParam(value = "type", required = false) String type) {
        return repository.listByType(type);
    }

    @PostMapping("/subagents/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable("id") String id, @RequestBody SubAgentQuery query) throws IOException {
        Optional<SubAgentDef> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SubAgentDef def = opt.get();
        String prompt = repository.readPrompt(def);
        String goodsInfo = repository.readGoodsInfo(def);
        String outputRule = """
                你必须严格只输出一个 JSON 对象，字段必须包含：
                product_id, quote, reason_tags, coupon_info, risk_flags, cta
                其中 coupon_info 必须是对象，包含：
                original_price, after_coupon_price, subsidy_by, stackable, coupon_desc
                禁止输出任何 JSON 之外的文本。
                """;
        String merged = "PROMPT:\n" + prompt
                + "\n\n<OutputRule>\n" + outputRule + "\n</OutputRule>"
                + "\n\n<GoodsInfo>\n" + goodsInfo + "\n</GoodsInfo>\n\nQUESTION:\n" + query.getQuestion()
                + "\n\nCONTEXT:\n" + JSON.toJSONString(query.getContext());
        try {
            log.info("SubAgent '{}' handle request: questionLen={}, ctxKeys={}", def.getId(),
                    merged.length(), query.getContext() == null ? 0 : query.getContext().size());
        } catch (Exception ignored) {}
        String base = llmClient.generate(prompt, merged);
        String answer = base == null ? "" : base.trim();
        Map<String, Object> structured = normalizeStructured(def, answer, goodsInfo, query.getContext());
        BigDecimal quote = toDecimal(structured.get("quote"), BigDecimal.ZERO);
        Map<String, Object> meta = new HashMap<>(structured);
        meta.put("model", "mock");
        meta.put("quote", quote.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        meta.put("rawAnswer", answer);
        SubAgentReply reply = new SubAgentReply();
        reply.setAgentId(def.getId());
        reply.setAnswer(JSON.toJSONString(structured));
        reply.setMeta(meta);
        try {
            log.info("SubAgent '{}' reply success: quote={}", def.getId(), quote.stripTrailingZeros().toPlainString());
        } catch (Exception ignored) {}
        return ResponseEntity.ok(reply);
    }

    @PostMapping("/subagents/judge")
    public ResponseEntity<Map<String, Object>> judge(@RequestBody SubAgentJudgeQuery query) {
        String replayJson = query == null ? "" : asString(query.getReplayJson());
        String merchantId = extractMerchantId(replayJson);
        if (replayJson.isBlank()) {
            return ResponseEntity.ok(judgeFallback(merchantId, "missing_replay", null));
        }
        String judgePrompt = repository.readJudgePrompt();
        if (judgePrompt.isBlank()) {
            return ResponseEntity.ok(judgeFallback(merchantId, "missing_judge_prompt", null));
        }
        ShoppingRequest request = query == null ? null : query.getRequest();
        String goodsInfo = repository.readGoodsInfo();
        String judgeUser = buildJudgeUserInput(request, goodsInfo, replayJson);
        try {
            String raw = llmClient.generate(judgePrompt, judgeUser);
            Map<String, Object> parsed = parseStructuredAnswer(raw == null ? "" : raw.trim());
            if (parsed.isEmpty()) {
                return ResponseEntity.ok(judgeFallback(merchantId, "no_delta", null));
            }
            Map<String, Object> normalized = normalizeJudgeResult(parsed, merchantId);
            return ResponseEntity.ok(normalized);
        } catch (Exception ex) {
            return ResponseEntity.ok(judgeFallback(merchantId, "error", ex.getClass().getSimpleName() + ": " + ex.getMessage()));
        }
    }

    private int extractPrice(Map<String, Object> ctx) {
        if (ctx == null) return 0;
        Object v = ctx.get("price");
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return 0;
    }

    private BigDecimal extractQuote(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        Matcher m1 = PRICE_AFTER_LABEL.matcher(answer);
        if (m1.find()) {
            try {
                return new BigDecimal(m1.group(1));
            } catch (Exception ignored) {
            }
        }
        Matcher m2 = PRICE_WITH_YUAN.matcher(answer);
        if (m2.find()) {
            try {
                return new BigDecimal(m2.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Map<String, Object> normalizeStructured(SubAgentDef def, String answer, String goodsInfo, Map<String, Object> context) {
        List<Map<String, Object>> goods = parseGoods(goodsInfo);
        Map<String, Object> parsed = parseStructuredAnswer(answer);
        String productId = asString(parsed.get("product_id"));
        int budget = extractPrice(context);
        if (productId.isBlank()) {
            productId = chooseProductId(goods, budget);
        }
        Map<String, Object> product = findProduct(goods, productId);
        BigDecimal guidePrice = resolveGuidePrice(product);
        BigDecimal quote = toDecimal(parsed.get("quote"), null);
        if (quote == null) {
            quote = extractQuote(answer);
        }
        if (quote == null || quote.compareTo(BigDecimal.ZERO) <= 0) {
            quote = guidePrice.compareTo(BigDecimal.ZERO) > 0 ? guidePrice : BigDecimal.valueOf(Math.max(1, budget));
        }
        List<String> reasonTags = parseStringList(parsed.get("reason_tags"));
        if (reasonTags.isEmpty()) {
            reasonTags = parseStringList(product.get("tags"));
        }
        Map<String, Object> couponInfo = parseCouponInfo(parsed.get("coupon_info"), quote, guidePrice, def.getId());
        List<String> riskFlags = parseStringList(parsed.get("risk_flags"));
        String cta = asString(parsed.get("cta"));
        if (cta.isBlank()) {
            cta = "如需我进一步对比材质、尺码和售后细节，我可以继续给你补充建议。";
        }
        Map<String, Object> structured = new HashMap<>();
        structured.put("product_id", productId);
        structured.put("quote", quote.setScale(2, RoundingMode.HALF_UP).doubleValue());
        structured.put("reason_tags", reasonTags);
        structured.put("coupon_info", couponInfo);
        structured.put("risk_flags", riskFlags);
        structured.put("cta", cta);
        structured.put("guide_price", guidePrice.setScale(2, RoundingMode.HALF_UP).doubleValue());
        return structured;
    }

    private Map<String, Object> parseStructuredAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(answer, Map.class);
        } catch (Exception ignored) {
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return JSON.parseObject(answer.substring(start, end + 1), Map.class);
            } catch (Exception ignored) {
            }
        }
        return new HashMap<>();
    }

    private List<Map<String, Object>> parseGoods(String goodsInfo) {
        if (goodsInfo == null || goodsInfo.isBlank()) {
            return List.of();
        }
        try {
            JSONArray arr = JSON.parseArray(goodsInfo);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof JSONObject jo) {
                    list.add(new HashMap<>(jo));
                } else if (o instanceof Map<?, ?> m) {
                    list.add(new HashMap<>((Map<String, Object>) m));
                }
            }
            list.forEach(this::normalizeGoodsFields);
            return list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String chooseProductId(List<Map<String, Object>> goods, int budget) {
        if (goods == null || goods.isEmpty()) {
            return "";
        }
        Map<String, Object> first = goods.get(0);
        if (budget <= 0) {
            return extractProductId(first);
        }
        Map<String, Object> best = null;
        for (Map<String, Object> g : goods) {
            BigDecimal price = resolveGuidePrice(g);
            if (price.compareTo(BigDecimal.valueOf(budget)) <= 0) {
                if (best == null || price.compareTo(resolveGuidePrice(best)) > 0) {
                    best = g;
                }
            }
        }
        if (best == null) {
            best = goods.stream().min((a, b) -> resolveGuidePrice(a).compareTo(resolveGuidePrice(b))).orElse(first);
        }
        return extractProductId(best);
    }

    private Map<String, Object> findProduct(List<Map<String, Object>> goods, String productId) {
        if (goods == null || goods.isEmpty()) {
            return Map.of();
        }
        for (Map<String, Object> g : goods) {
            if (productId.equals(extractProductId(g))) {
                return g;
            }
        }
        return goods.get(0);
    }

    private BigDecimal resolveGuidePrice(Map<String, Object> product) {
        if (product == null || product.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal guide = toDecimal(product.get("当前指导价"), null);
        if (guide == null) {
            guide = toDecimal(product.get("指导价"), null);
        }
        if (guide == null) {
            guide = toDecimal(product.get("biddingPrice"), null);
        }
        return guide == null ? BigDecimal.ZERO : guide;
    }

    private void normalizeGoodsFields(Map<String, Object> goods) {
        if (goods == null || goods.isEmpty()) {
            return;
        }
        String productId = extractProductId(goods);
        if (!productId.isBlank()) {
            goods.putIfAbsent("skuId", productId);
            goods.putIfAbsent("product_id", productId);
            goods.putIfAbsent("商品id", productId);
        }
        if (!goods.containsKey("指导价")) {
            goods.put("指导价", toDecimal(goods.get("当前指导价"), null));
        }
    }

    private String extractProductId(Map<String, Object> product) {
        if (product == null || product.isEmpty()) {
            return "";
        }
        String productId = asString(product.get("skuId"));
        if (productId.isBlank()) {
            productId = asString(product.get("商品id"));
        }
        if (productId.isBlank()) {
            productId = asString(product.get("product_id"));
        }
        return productId;
    }

    private String extractMerchantId(String replayJson) {
        if (replayJson == null || replayJson.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> replay = JSON.parseObject(replayJson, Map.class);
            String merchantId = asString(mapValue(replay, "subAgentId", ""));
            if (merchantId.isBlank()) {
                merchantId = asString(mapValue(replay, "merchantId", ""));
            }
            return merchantId;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildJudgeUserInput(ShoppingRequest request, String goodsInfo, String replayJson) {
        String req = request == null ? "" : asString(request.getRequest());
        String hidden = request == null ? "" : asString(request.getHidding());
        String followUp = request == null ? "" : asString(request.getFollowUp());
        double price = request == null ? 0 : request.getPrice();
        String dislike = request == null ? "" : asString(request.getDislike());
        String replay = replayJson == null ? "" : replayJson;
        String goodsFiltered = filterGoodsInfoByReplay(goodsInfo, replay);
        Map<String, Object> replayObj = parseStructuredAnswer(replay);
        String merchantId = asString(mapValue(replayObj, "subAgentId", asString(mapValue(replayObj, "merchantId", ""))));
        String productId = asString(mapValue(replayObj, "skuId", asString(mapValue(replayObj, "product_id", ""))));
        Object couponObj = mapValue(replayObj, "coupon", mapValue(replayObj, "coupon_info", Map.of()));
        Map<String, Object> couponInfo = couponObj instanceof Map<?, ?> m ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("用户显性输入：意向商品=").append(req).append("；预算=").append(price).append("；喜恶/排斥=").append(dislike).append("\n");
        if (!followUp.isBlank()) {
            sb.append("用户追问：").append(followUp).append("\n");
        }
        sb.append("<hidden>").append(hidden).append("</hidden>\n");
        sb.append("<GoodsInfo>\n").append(goodsFiltered == null ? "" : goodsFiltered).append("\n</GoodsInfo>\n");
        sb.append("<subAgentReplay>\n");
        sb.append(JSON.toJSONString(Map.of(
                "merchantId", merchantId,
                "productId", productId,
                "couponInfo", couponInfo,
                "raw", replay
        )));
        sb.append("\n</subAgentReplay>\n");
        return sb.toString();
    }

    private String filterGoodsInfoByReplay(String goodsInfo, String replayJson) {
        if (goodsInfo == null || goodsInfo.isBlank() || replayJson == null || replayJson.isBlank()) {
            return goodsInfo == null ? "" : goodsInfo;
        }
        Set<String> skuIds = new HashSet<>();
        try {
            Map<String, Object> replay = JSON.parseObject(replayJson, Map.class);
            String sku = asString(mapValue(replay, "skuId", asString(mapValue(replay, "product_id", ""))));
            if (!sku.isBlank()) {
                skuIds.add(sku);
            }
        } catch (Exception ignored) {
        }
        if (skuIds.isEmpty()) {
            return goodsInfo;
        }
        try {
            JSONArray arr = JSON.parseArray(goodsInfo);
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Object o : arr) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> g = new HashMap<>((Map<String, Object>) m);
                String sku = asString(mapValue(g, "skuId", asString(mapValue(g, "商品id", asString(mapValue(g, "product_id", ""))))));
                if (skuIds.contains(sku)) {
                    matched.add(g);
                }
            }
            if (!matched.isEmpty()) {
                return JSON.toJSONString(matched);
            }
        } catch (Exception ignored) {
        }
        return goodsInfo;
    }

    private Map<String, Object> normalizeJudgeResult(Map<String, Object> parsed, String merchantId) {
        boolean pass = toBool(parsed.get("pass"));
        boolean severe = toBool(parsed.get("severe"));
        List<String> riskFlags = parseStringList(parsed.get("risk_flags"));
        List<String> reasons = parseStringList(parsed.get("reasons"));
        List<String> fixAdvice = parseStringList(parsed.get("fix_advice"));
        int score = toDecimal(parsed.get("score"), BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).intValue();
        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }
        if (!pass && riskFlags.isEmpty()) {
            riskFlags.add("unknown");
        }
        if (!pass && reasons.isEmpty()) {
            reasons.add("judge_no_reason");
        }
        if (!pass && fixAdvice.isEmpty()) {
            fixAdvice.add("请补充可核验信息后重试");
        }
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("pass", pass);
        normalized.put("severe", severe);
        normalized.put("risk_flags", riskFlags);
        normalized.put("reasons", reasons);
        normalized.put("fix_advice", fixAdvice);
        normalized.put("score", score);
        normalized.put("merchantId", merchantId);
        return normalized;
    }

    private Map<String, Object> judgeFallback(String merchantId, String reason, String detail) {
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
        String reasonText = detail == null || detail.isBlank() ? reason : reason + ":" + detail;
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("merchantId", merchantId == null ? "" : merchantId);
        fallback.put("decision", "retry");
        fallback.put("pass", false);
        fallback.put("severe", false);
        fallback.put("risk_flags", List.of(reason == null ? "unknown" : reason));
        fallback.put("reasons", List.of(reasonText == null ? "judge_fallback" : reasonText));
        fallback.put("fix_advice", List.of(advice));
        fallback.put("score", 0);
        return fallback;
    }

    private Map<String, Object> parseCouponInfo(Object raw, BigDecimal quote, BigDecimal guidePrice, String agentId) {
        Map<String, Object> info;
        if (raw instanceof Map<?, ?> m) {
            info = new HashMap<>((Map<String, Object>) m);
        } else {
            info = new HashMap<>();
        }
        BigDecimal original = toDecimal(info.get("original_price"), guidePrice);
        if (original == null || original.compareTo(BigDecimal.ZERO) <= 0) {
            original = guidePrice;
        }
        BigDecimal afterCoupon = toDecimal(info.get("after_coupon_price"), quote);
        if (afterCoupon == null || afterCoupon.compareTo(BigDecimal.ZERO) <= 0) {
            afterCoupon = quote;
        }
        String subsidyBy = asString(info.get("subsidy_by"));
        if (subsidyBy.isBlank()) {
            subsidyBy = "B".equalsIgnoreCase(agentId) ? "平台补贴+店铺券" : "无补贴";
        }
        Object stackable = info.get("stackable");
        if (!(stackable instanceof Boolean)) {
            stackable = Boolean.TRUE;
        }
        String couponDesc = asString(info.get("coupon_desc"));
        if (couponDesc.isBlank()) {
            couponDesc = "无";
            if (afterCoupon.compareTo(original) < 0) {
                couponDesc = "已应用优惠";
            }
        }
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("original_price", original.setScale(2, RoundingMode.HALF_UP).doubleValue());
        normalized.put("after_coupon_price", afterCoupon.setScale(2, RoundingMode.HALF_UP).doubleValue());
        normalized.put("subsidy_by", subsidyBy);
        normalized.put("stackable", stackable);
        normalized.put("coupon_desc", couponDesc);
        return normalized;
    }

    private List<String> parseStringList(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> arr) {
            for (Object o : arr) {
                String s = asString(o);
                if (!s.isBlank()) {
                    list.add(s);
                }
            }
        } else {
            String one = asString(raw);
            if (!one.isBlank()) {
                list.add(one);
            }
        }
        return list;
    }

    private BigDecimal toDecimal(Object raw, BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof BigDecimal b) {
            return b;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = asString(raw);
        if (s.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String asString(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private Object mapValue(Map<?, ?> map, String key, Object fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        Object value = map.get(key);
        return value == null ? fallback : value;
    }

    private boolean toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = asString(raw).toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
}
