package com.aim.mall.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.aim.mall.ai.adapter.SkuServiceReactiveAdapter;
import com.aim.mall.ai.client.LlmClientReactive;
import com.aim.mall.ai.dto.AimModelTypeDO;
import com.aim.mall.ai.dto.ChatHistoryDO;
import com.aim.mall.ai.dto.LLMData;
import com.aim.mall.ai.dto.LLMStreamData;
import com.aim.mall.ai.dto.SkuTag;
import com.aim.mall.ai.dto.constant.LLMStreamDataStatus;
import com.aim.mall.ai.dto.request.ChatOptions;
import com.aim.mall.ai.dto.request.SkuRequest;
import com.aim.mall.ai.dto.response.SkuResp;
import com.aim.mall.ai.dto.response.SkuTagVO;
import com.aim.mall.ai.repo.AimModelTypeMapper;
import com.aim.mall.ai.util.SseEventMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@Slf4j
public class ReactiveChatService {

    @Autowired
    private ChatService chatService;
    @Autowired
    private AimModelTypeMapper aimModelTypeMapper;
    @Autowired
    private ImageValidService imageValidService;
    @Autowired
    private LlmClientReactive llmClientReactive;
    @Autowired
    private SkuServiceReactiveAdapter skuAdapter;

    private static final Long LLM_TEXT_MAX_LENGTH = 5000L;
    private static final String ERROR_REPLY = "哎呀，这个我暂时还不会";
    private static final String PHASE_LIST = "{\"study\":\"理解您的需求\",\"match\":\"搜索匹配商品\",\"search\":\"筛选优质结果\",\"offer\":\"整理推荐方案\"}";

    public Flux<ServerSentEvent<String>> streamChat(Long userId, ChatOptions chatOptions) {
        List<ChatHistoryDO> historyList = new ArrayList<>();
        return audit(chatOptions.getQuestion())
                .flatMapMany(valid -> {
                    if (!valid) {
                        return Flux.just(SseEventMapper.text("内容提交失败。为维护健康、安全的社区环境，请修改后重新提交。"));
                    }
                    log.info("用户 {} 发起了一次流式对话，请求参数为: {}", userId, JSON.toJSONString(chatOptions));
                    saveUserMessage(historyList, chatOptions, System.currentTimeMillis(), userId);
                    String requestBody = buildLLMRequestBody(chatOptions, userId);
                    LLMData llmData = new LLMData();
                    StringBuilder fullContent = new StringBuilder();
                    final Map<String, Object> ctx = new HashMap<>();
                    ctx.put("guess", null);
                    ctx.put("tags", null);
                    java.util.concurrent.atomic.AtomicReference<Mono<List<SkuResp>>> skuRef = new java.util.concurrent.atomic.AtomicReference<>(Mono.empty());
                    final java.util.concurrent.atomic.AtomicReference<StringBuilder> accRef = new java.util.concurrent.atomic.AtomicReference<>(new StringBuilder());
                    Flux<ServerSentEvent<String>> main = llmClientReactive.stream(requestBody)
                            .concatMap(chunk -> {
                                StringBuilder acc = accRef.get();
                                if (chunk != null) {
                                    acc.append(chunk);
                                }
                                java.util.List<LLMStreamData> out = new java.util.ArrayList<>();
                                String s = acc.toString();
                                int start = s.indexOf('{');
                                while (start >= 0) {
                                    int end = findJsonObjectEnd(s, start);
                                    if (end < 0) {
                                        break;
                                    }
                                    String jsonStr = s.substring(start, end + 1);
                                    try {
                                        LLMStreamData obj = JSON.parseObject(jsonStr, LLMStreamData.class);
                                        if (obj != null) {
                                            out.add(obj);
                                        }
                                    } catch (Exception e) {
                                        log.error("ReactiveChatService parse LLM json error, conversationId: {}", chatOptions.getConversationId(), e);
                                    }
                                    s = s.substring(end + 1);
                                    start = s.indexOf('{');
                                }
                                acc.setLength(0);
                                acc.append(s);
                                return Flux.fromIterable(out);
                            })
                            .filter(sd -> ObjectUtils.isNotEmpty(sd.getStatus()))
                            .flatMap(streamData -> {
                                String status = streamData.getStatus();
                                if (ObjectUtils.isEmpty(status)) {
                                    return Flux.empty();
                                }
                                // 状态处理
                                if (LLMStreamDataStatus.STATUS_ERROR.getStatus().equals(status.toLowerCase(Locale.ROOT)) || LLMStreamDataStatus.INTERRUPT.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    log.error("ReactiveChatService received STATUS_ERROR from LLM, conversationId: {}", chatOptions.getConversationId());
                                    ChatHistoryDO errorHistory = buildChatHistory(chatOptions.getConversationId(), "assistant", ERROR_REPLY, null, System.currentTimeMillis(), "text", null);
                                    historyList.add(errorHistory);
                                    return Flux.just(SseEventMapper.text(ERROR_REPLY))
                                            .doFinally(signal -> chatService.saveChatHistoryAsync(historyList));
                                }
                                // 流程结束处理
                                if (LLMStreamDataStatus.STATUS_STOP.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    List<ServerSentEvent<String>> out = new ArrayList<>();
                                    if (ObjectUtils.isNotEmpty(fullContent.toString())) {
                                        llmData.setText(fullContent.toString());
                                        log.info("完整回复为: {}", fullContent.toString());
                                        ChatHistoryDO aiHistory = buildChatHistory(
                                                chatOptions.getConversationId(), "assistant",
                                                fullContent.toString(), fullContent.toString(), System.currentTimeMillis(), "text", null);
                                        historyList.add(aiHistory);
                                    }
                                    return skuRef.get().defaultIfEmpty(List.of())
                                            .flatMapMany(skus -> {
                                                log.info("当前查询到上的商品信息: {}", skus.size());
                                                List<SkuResp> list = skus;
                                                Object tagsObj = ctx.get("tags");
                                                if (tagsObj instanceof String tagStr) {
                                                    processTagListToSkuList(tagStr, list);
                                                }
                                                if (!list.isEmpty()) {
                                                    llmData.setSkuIds(list.stream().map(SkuResp::getSkuId).toList());
                                                    Map<String, Object> outParams = new HashMap<>();
                                                    outParams.put("messageId", chatOptions.getConversationId());
                                                    outParams.put("round", System.currentTimeMillis());
                                                    outParams.put("skuList", list);
                                                    String skuStr = JSON.toJSONString(outParams);
                                                    ChatHistoryDO aiSkuHistory = buildChatHistory(
                                                            chatOptions.getConversationId(), "assistant", skuStr,
                                                            JSON.toJSONString(llmData), System.currentTimeMillis() + 1000L, "card", chatOptions.getConversationId());
                                                    historyList.add(aiSkuHistory);
                                                    out.add(SseEventMapper.card(skuStr));
                                                }
                                                Object guessObj = ctx.get("guess");
                                                if (guessObj instanceof String guessStr) {
                                                    Map<String, List<String>> map = new HashMap<>();
                                                    try {
                                                        map = JSON.parseObject(guessStr, new TypeReference<Map<String, List<String>>>() {});
                                                    } catch (Exception e) {
                                                    }
                                                    if (map.containsKey("guess_list")) {
                                                        llmData.setGuessText(guessStr);
                                                        Map<String, Object> outParams = new HashMap<>();
                                                        outParams.put("messageId", chatOptions.getConversationId());
                                                        outParams.put("round", System.currentTimeMillis());
                                                        outParams.put("guessList", map.get("guess_list"));
                                                        String guessOut = JSON.toJSONString(outParams);
                                                        ChatHistoryDO aiGuessHistory = buildChatHistory(
                                                                chatOptions.getConversationId(), "assistant", guessOut,
                                                                JSON.toJSONString(llmData), System.currentTimeMillis() + 1000L, "guess", chatOptions.getConversationId());
                                                        historyList.add(aiGuessHistory);
                                                        out.add(SseEventMapper.guess(guessOut));
                                                    }
                                                }
                                                return Flux.fromIterable(out)
                                                        .doFinally(signal -> chatService.saveChatHistoryAsync(historyList));
                                            });
                                }
                                // 商品ID处理
                                if (LLMStreamDataStatus.SKU_ID_LIST.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    SkuRequest req = processSkuLogic(streamData);
                                    if (req != null) {
                                        log.info("异步查询商品信息, req: {}", JSON.toJSONString(req));
                                        skuRef.set(skuAdapter.query(req));
                                    }
                                    return Flux.empty();
                                }
                                // 开场白处理
                                if (LLMStreamDataStatus.FULL_CONTEXT.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    String content = streamData.getContent();
                                    if (ObjectUtils.isEmpty(content) || "null".equals(content)) {
                                        return Flux.empty();
                                    }
                                    if (fullContent.length() <= LLM_TEXT_MAX_LENGTH) {
                                        fullContent.append(content);
                                    }
                                    return Flux.just(SseEventMapper.text(content));
                                }
                                // 中间节点处理
                                if (LLMStreamDataStatus.STUDY_START.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(
                                            SseEventMapper.phase(PHASE_LIST),
                                            SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.STUDY_START))
                                    );
                                }
                                if (LLMStreamDataStatus.STUDY_END.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.STUDY_END)));
                                }
                                if (LLMStreamDataStatus.MATCH_START.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.MATCH_START)));
                                }
                                if (LLMStreamDataStatus.MATCH_END.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.MATCH_END)));
                                }
                                if (LLMStreamDataStatus.SEARCH_START.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.SEARCH_START)));
                                }
                                if (LLMStreamDataStatus.SEARCH_END.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.SEARCH_END)));
                                }
                                if (LLMStreamDataStatus.OFFER_START.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.OFFER_START)));
                                }
                                if (LLMStreamDataStatus.OFFER_END.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    return Flux.just(SseEventMapper.thinking(getThinkingStatus(LLMStreamDataStatus.OFFER_END)));
                                }
                                // tag处理
                                if (LLMStreamDataStatus.TAG_LIST.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    if (ObjectUtils.isNotEmpty(streamData.getContent())) {
                                        ctx.put("tags", streamData.getContent());
                                    }
                                    return Flux.empty();
                                }
                                // 猜你想问处理
                                if (LLMStreamDataStatus.GUESS_LIST.getStatus().equals(status.toLowerCase(Locale.ROOT))) {
                                    if (ObjectUtils.isNotEmpty(streamData.getContent())) {
                                        log.info("猜你想问为： {}", streamData.getContent());
                                        ctx.put("guess", streamData.getContent());
                                    }
                                    return Flux.empty();
                                }
                                return Flux.empty();
                            })
                            .onErrorContinue((e, bad) -> log.error("流式对话异常,conversationId: {}", chatOptions.getConversationId(), e))
                            .retryWhen(Retry.max(0))
                            .onErrorResume(e -> Flux.just(SseEventMapper.text(ERROR_REPLY))
                                    .doFinally(signal -> chatService.saveChatHistoryAsync(historyList)))
                            .share();
                    return Flux.merge(
                            heartbeat().takeUntilOther(main.ignoreElements().then(Mono.empty())),
                            main
                    );
                });
    }

    private String getThinkingStatus(LLMStreamDataStatus status) {
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("name", status.getStatus().substring(0,status.getStatus().lastIndexOf("_")));
        resultMap.put("desc", status.getDesc());
        resultMap.put("status", status.getStatus().contains("start") ? "START" : "END");
        return JSON.toJSONString(resultMap);
    }

    private static int findJsonObjectEnd(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == '\\') {
                if (inStr) {
                    esc = true;
                }
                continue;
            }
            if (c == '"') {
                inStr = !inStr;
                continue;
            }
            if (!inStr) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private Flux<ServerSentEvent<String>> heartbeat() {
        return Flux.interval(java.time.Duration.ofSeconds(30))
                .map(ts -> SseEventMapper.heartbeat(String.valueOf(ts)));
    }

    private Mono<Boolean> audit(String text) {
        return Mono.fromCallable(() -> {
            if (ObjectUtils.isEmpty(text)) {
                return true;
            }
            // TextAuditResponse resp = thirdPartyRemoteService.textAuditSync(text);
            // return resp.getIsValid();
            // 暂时屏蔽入口
            return true;
        }).doOnError(e -> log.error("ReactiveChatService text audit error", e))
                .onErrorReturn(true)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void saveUserMessage(List<ChatHistoryDO> historyList, ChatOptions chatOptions, Long round, Long userId) {
        if (!ObjectUtils.isEmpty(chatOptions.getFileUrl()) && chatOptions.getFileUrl().contains("https://")) {
            ChatHistoryDO imgHistory = buildChatHistory(
                    chatOptions.getConversationId(), "user",
                    chatOptions.getFileUrl(), null, round, "img", null
            );
            historyList.add(imgHistory);
        }
        ChatHistoryDO userHistory = buildChatHistory(
                chatOptions.getConversationId(), "user",
                chatOptions.getQuestion(), null, System.currentTimeMillis(), "text", null
        );
        historyList.add(userHistory);
    }

    private String buildLLMRequestBody(ChatOptions chatOptions, Long userId) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> externalData = new HashMap<>();
        externalData.put("user_id", userId);
        externalData.put("product_msg", chatOptions.getProductMsg());
        body.put("user_id", userId);
        body.put("query", chatOptions.getQuestion());
        body.put("session_id", chatOptions.getConversationId());
        if (ObjectUtils.isNotEmpty(chatOptions.getFileUrl())) {
            body.put("image_b64", imageValidService.generateBase64FromUrl(chatOptions.getFileUrl()));
        }
        Integer precision = chatOptions.getPrecision();
        if (precision == null) {
            AimModelTypeDO aimModelTypeDO = aimModelTypeMapper.selectOne(new LambdaUpdateWrapper<>(AimModelTypeDO.class).eq(AimModelTypeDO::getPrecisionStatus, 1));
            Integer precisionType = aimModelTypeDO != null ? aimModelTypeDO.getPrecisionType() : 0;
            body.put("precision", precisionType);
        } else {
            body.put("precision", chatOptions.getPrecision());
        }
        body.put("external_data", externalData);
        return JSON.toJSONString(body);
    }

    private SkuRequest processSkuLogic(LLMStreamData streamData) {
        try {
            log.info("origin sku_ids_list: {}", streamData.getContent());
            List<Long> newSkuIds = JSON.parseArray(streamData.getContent(), Long.class);
            if (!org.springframework.util.CollectionUtils.isEmpty(newSkuIds)) {
                SkuRequest skuRequest = new SkuRequest();
                skuRequest.setSkuIds(newSkuIds);
                skuRequest.setLimitSize(50);
                return skuRequest;
            }
        } catch (Exception e) {
            log.error("ReactiveChatService parse sku_ids_list error", e);
            return null;
        }
        return null;
    }

    private void processTagListToSkuList(String tagListStr, List<SkuResp> skus) {
        SkuTagVO tagVo = null;
        log.info("原始taglist为 {}",tagListStr);
        try {
            tagVo = JSON.parseObject(tagListStr, SkuTagVO.class);
        } catch (Exception e) {
            log.error("ReactiveChatService parse tags_done error", e);
        }
        if (CollectionUtils.isEmpty(skus) || tagVo == null) {
            return;
        }
        if (tagVo != null && ObjectUtils.isNotEmpty(tagVo.getTagList())) {
            List<SkuTag> tagList = tagVo.getTagList();
            for (SkuResp item : skus) {
                item.setTags(tagList.stream()
                        .filter(tag -> item.getSkuId().equals(tag.getSkuId()))
                        .findFirst()
                        .map(SkuTag::getTags)
                        .orElse(null));
            }
        }
    }

    private ChatHistoryDO buildChatHistory(String conversationId, String roleType,
                                           String content, String llmContent,
                                           Long round, String eventType, String messageId) {
        return ChatHistoryDO.builder()
                .conversationId(conversationId)
                .messageId(messageId != null ? messageId : java.util.UUID.randomUUID().toString())
                .content(content)
                .eventType(eventType)
                .llmContent(llmContent)
                .roleType(roleType)
                .createTime(java.time.LocalDateTime.now())
                .round(round)
                .build();
    }
}
