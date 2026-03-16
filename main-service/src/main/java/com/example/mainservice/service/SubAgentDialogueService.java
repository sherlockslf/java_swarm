package com.example.mainservice.service;

import com.alibaba.fastjson2.JSON;
import com.example.common.dto.SubAgentQuery;
import com.example.common.dto.SubAgentReply;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

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
        SubAgentQuery qy = new SubAgentQuery();
        qy.setQuestion(question);
        qy.setContext(context);
        return webClient.post()
                .uri(baseUrl + "/api/subagents/" + agentId + "/reply")
                .bodyValue(qy)
                .retrieve()
                .bodyToMono(SubAgentReply.class)
                .doOnSuccess(r -> log.info("Call subAgent '{}' success", agentId))
                .doOnError(ex -> log.warn("Call subAgent '{}' failed: {}", agentId, ex.toString()));
    }

    public Flux<ServerSentEvent<String>> toEvents(SubAgentReply r) {
        double quote = 0;
        Object qo = r.getMeta() == null ? null : r.getMeta().get("quote");
        if (qo instanceof Number n) quote = n.doubleValue();
        else if (qo instanceof String s) try { quote = Double.parseDouble(s); } catch (Exception ignored) {}
        String payload = JSON.toJSONString(Map.of(
                "agentId", r.getAgentId(),
                "quote", quote,
                "message", r.getAnswer() == null ? "" : r.getAnswer()
        ));
        String done = JSON.toJSONString(Map.of(
                "agentId", r.getAgentId(),
                "done", true
        ));
        return Flux.just(
                ServerSentEvent.<String>builder().event("subAgentReplay").data(payload).build(),
                ServerSentEvent.<String>builder().event("subAgentDone").data(done).build()
        );
    }
}
