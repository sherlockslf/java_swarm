package com.aim.mall.ai.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.aim.mall.ai.util.NacosServiceUtil;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class LlmClientReactive {

    private final WebClient webClient;

    public LlmClientReactive(WebClient llmWebClient) {
        this.webClient = llmWebClient;
    }

    public Flux<String> stream(String requestBody) {
        String url = NacosServiceUtil.getAgentServerIpFromNacos() + "/stream_sku_id";
        log.info("LlmClientReactive start streaming to {}", url);
        final StringBuilder carry = new StringBuilder();
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(db -> {
                    byte[] bytes = new byte[db.readableByteCount()];
                    db.read(bytes);
                    DataBufferUtils.release(db);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .concatMap(s -> {
                    List<String> out = new java.util.ArrayList<>();
                    carry.append(s);
                    int idx;
                    while ((idx = carry.indexOf("\n")) >= 0) {
                        if (idx > carry.length()) {
                            break;
                        }
                        String line = carry.substring(0, idx);
                        if (line.endsWith("\r")) {
                            line = line.substring(0, line.length() - 1);
                        }
                        if (line.startsWith("data:")) {
                            line = line.substring(5).trim();
                        }
                        if (!ObjectUtils.isEmpty(line)) {
                            out.add(line);
                        }
                        carry.delete(0, idx + 1);
                    }
                    return Flux.fromIterable(out);
                })
                .retryWhen(Retry.max(0))
                .filter(s -> !ObjectUtils.isEmpty(s))
                .doOnError(e -> log.error("LlmClientReactive stream error", e))
                .timeout(Duration.ofSeconds(100));
    }

    static Flux<String> splitByNewline(Flux<String> chunks) {
        final StringBuilder carry = new StringBuilder();
        return chunks.concatMap(s -> {
            List<String> out = new java.util.ArrayList<>();
            carry.append(s);
            int idx;
            while ((idx = carry.indexOf("\n")) >= 0) {
                if (idx > carry.length()) {
                    break;
                }
                String line = carry.substring(0, idx);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.startsWith("data:")) {
                    line = line.substring(5).trim();
                }
                if (!ObjectUtils.isEmpty(line)) {
                    out.add(line);
                }
                carry.delete(0, idx + 1);
            }
            return Flux.fromIterable(out);
        }).filter(s -> !ObjectUtils.isEmpty(s));
    }

}
