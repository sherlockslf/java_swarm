package com.example.mainservice.service;

import com.example.common.dto.SubAgentInfo;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Log4j2
@Service
public class SubAgentDirectoryService {
    private final WebClient webClient;
    private final String baseUrl;

    public SubAgentDirectoryService(WebClient webClient,
                                    @Value("${subagent.base-url:http://localhost:9091}") String baseUrl) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
    }

    public Flux<SubAgentInfo> fetchAgents(String type) {
        return webClient.get()
                .uri(baseUrl + "/api/subagents?type=" + type)
                .retrieve()
                .bodyToFlux(SubAgentInfo.class)
                .doOnComplete(() -> log.info("Fetched subAgents list successfully"))
                .doOnError(ex -> log.warn("Fetch subAgents failed: {}", ex.toString()));
    }
}
