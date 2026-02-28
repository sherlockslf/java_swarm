package com.example.mainservice.service;

import com.example.common.llm.RealQwenLlmClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LlmStreamService {
    public Flux<ServerSentEvent<String>> streamTokens(RealQwenLlmClient client, String system, String user, String eventName) {
        Flux<String> tokens = client.generateStreamFlux(system, user);
        if ("summary".equals(eventName)) {
            tokens = tokens.switchIfEmpty(Mono.fromSupplier(() -> client.generate(system, user))
                    .filter(s -> s != null && !s.isBlank())
                    .flux());
        }
        return tokens
                .map(delta -> ServerSentEvent.<String>builder().event(eventName).data(delta).build());
    }
}
