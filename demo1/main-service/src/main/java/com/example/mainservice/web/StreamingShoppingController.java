package com.example.mainservice.web;

import com.example.common.dto.ShoppingRequest;
import com.example.mainservice.service.OrchestratorService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Log4j2
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
@RequestMapping("/api/shopping")
public class StreamingShoppingController {
    private final OrchestratorService orchestratorService;

    public StreamingShoppingController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Validated @RequestBody ShoppingRequest request, ServerHttpResponse response) {
        response.getHeaders().add("X-Accel-Buffering", "no");
        response.getHeaders().add("Cache-Control", "no-cache");
        return orchestratorService.stream(request);
    }
}
