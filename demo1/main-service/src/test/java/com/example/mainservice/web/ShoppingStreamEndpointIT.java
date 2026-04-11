package com.example.mainservice.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "QWEN_KEYS_FILE", matches = ".*")
public class ShoppingStreamEndpointIT {
    @Autowired
    WebTestClient client;

    @Test
    void streamPublicQuestionTokens() {
        String body = """
        {
          "request":"想买一台性价比高的轻薄本",
          "price":3000,
          "dislike":"风扇噪音大"
        }
        """;
        var result = client.post()
                .uri("/api/shopping/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);
        var bodyFlux = Flux.from(result.getResponseBody())
                .take(Duration.ofSeconds(15))
                .take(50);
        var got = bodyFlux.collectList().block(Duration.ofSeconds(20));
        assertTrue(got != null && got.stream().anyMatch(s -> s.contains("subAgintInfo") || s.contains("summary") || s.contains("subAgentReplay")));
        assertTrue(got != null && got.stream().noneMatch(s -> s.contains("event:agentJudge")));
    }
}
