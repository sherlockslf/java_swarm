package com.example.common.llm;

import com.alibaba.fastjson2.JSON;
import com.example.common.llm.qwen.QwenApiModels.ChatRequest;
import com.example.common.llm.qwen.QwenApiModels.ChatResponse;
import com.example.common.llm.qwen.QwenApiModels.Message;
import com.example.common.llm.qwen.QwenKeyPool;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class RealQwenLlmClient implements LlmClient {
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final RestTemplate http;
    private final QwenKeyPool pool;
    private final Integer timeoutMs;
    private volatile WebClient webClient;

    public RealQwenLlmClient(String baseUrl, String model, double temperature, int timeoutMs, QwenKeyPool pool) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.pool = pool;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        this.http = new RestTemplate(f);
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String generate(String systemPrompt, String userInput) {
        if (pool == null || !pool.hasKeys()) {
            return userInput;
        }
        try {
            String sp = systemPrompt == null ? "" : systemPrompt;
            String up = userInput == null ? "" : userInput;
            log.info("LLM sync call begin: model={}, base={}, temp={}, sysLen={}, userLen={}",
                    model, baseUrl, temperature, sp.length(), up.length());
        } catch (Exception ignored) {}
        int authRetries = 2;
        int rateRetries = 3;
        int attempt = 0;
        while (true) {
            String key = pool.nextKey();
            if (key == null) return userInput;
            try {
                pool.acquire();
                String url = baseUrl + "/chat/completions";
                ChatRequest req = new ChatRequest();
                req.model = model;
                req.temperature = temperature;
                req.messages = List.of(
                        new Message("system", systemPrompt == null ? "" : systemPrompt),
                        new Message("user", userInput == null ? "" : userInput)
                );
                String body = JSON.toJSONString(req);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(key);
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                String respStr = http.postForObject(url, entity, String.class);
                if (respStr == null || respStr.isBlank()) return "";
                ChatResponse resp = JSON.parseObject(respStr, ChatResponse.class);
                if (resp != null && resp.choices != null && !resp.choices.isEmpty()
                        && resp.choices.get(0) != null
                        && resp.choices.get(0).message != null) {
                    String content = resp.choices.get(0).message.content;
                    try { log.info("LLM sync call done"); } catch (Exception ignored) {}
                    return content == null ? "" : content;
                }
                return "";
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401 && authRetries-- > 0) {
                    continue;
                }
                if (status == 429 && rateRetries-- > 0) {
                    backoff(++attempt);
                    continue;
                }
                try { log.warn("LLM sync call http error: status={}", status); } catch (Exception ignored) {}
                return "";
            } catch (Exception e) {
                try { log.error("LLM sync call exception: {}", e.toString()); } catch (Exception ignored) {}
                return "";
            } finally {
                try { pool.release(); } catch (Exception ignored) {}
            }
        }
    }

    public void generateStream(String systemPrompt, String userInput,
                               java.util.function.Consumer<String> onToken,
                               java.util.function.Consumer<Throwable> onError,
                               Runnable onComplete) {
        generateStreamFlux(systemPrompt, userInput)
                .subscribe(onToken, onError, onComplete);
    }

    public Flux<String> generateStreamFlux(String systemPrompt, String userInput) {
        if (pool == null || !pool.hasKeys()) {
            return Flux.just(userInput == null ? "" : userInput).filter(s -> !s.isBlank());
        }
        String url = baseUrl + "/chat/completions";
        String sp = systemPrompt == null ? "" : systemPrompt;
        String up = userInput == null ? "" : userInput;
        ChatRequest req = new ChatRequest();
        req.model = model;
        req.temperature = temperature;
        req.stream = Boolean.TRUE;
        req.messages = List.of(
                new Message("system", sp),
                new Message("user", up)
        );
        try {
            log.info("LLM stream begin: model={}, base={}, temp={}, sysLen={}, userLen={}",
                    model, baseUrl, temperature, sp.length(), up.length());
            log.info("LLM stream req: {}", JSON.toJSONString(req));
        } catch (Exception ignored) {}
        String body = JSON.toJSONString(req);
        return attemptStreamFlux(url, body, 3, 2)
                .doOnComplete(() -> {
                    try { log.info("LLM stream complete"); } catch (Exception ignored) {}
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    try { log.warn("LLM stream empty: model={}, sysLen={}, userLen={}", model, sp.length(), up.length()); } catch (Exception ignored) {}
                }).thenMany(Flux.empty()));
    }

    private Flux<String> attemptStreamFlux(String url, String body, int rateRetries, int authRetries) {
        return Flux.defer(() -> {
            String key = pool.nextKey();
            if (key == null) {
                return Flux.empty();
            }
            ensureWebClient();
            return Mono.fromRunnable(() -> {
                        try {
                            pool.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .thenMany(streamOnceFlux(url, body, key))
                    .doFinally(sig -> {
                        try { pool.release(); } catch (Exception ignored) {}
                    })
                    .onErrorResume(t -> {
                        String msg = t == null ? null : t.getMessage();
                        if ("401".equals(msg) && authRetries > 0) {
                            return attemptStreamFlux(url, body, rateRetries, authRetries - 1);
                        }
                        if ("429".equals(msg) && rateRetries > 0) {
                            int attempt = 4 - rateRetries;
                            long millis = Math.min(1000L, 200L * (long)Math.pow(2, Math.max(0, attempt - 1)));
                            return Mono.delay(Duration.ofMillis(millis))
                                    .thenMany(attemptStreamFlux(url, body, rateRetries - 1, authRetries));
                        }
                        try { log.warn("LLM stream error: {}", msg); } catch (Exception ignored) {}
                        return Flux.error(t);
                    });
        });
    }

    private Flux<String> streamOnceFlux(String url, String body, String key) {
        WebClient.RequestBodySpec spec = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM);
        Flux<String> chunks = spec.body(BodyInserters.fromValue(body))
                .exchangeToFlux((ClientResponse resp) -> {
                    int code = resp.statusCode().value();
                    if (code == 401) {
                        return Flux.error(new RuntimeException("401"));
                    }
                    if (code == 429) {
                        return Flux.error(new RuntimeException("429"));
                    }
                    return resp.bodyToFlux(String.class);
                });

        return parseStreamChunksToDeltas(chunks, this::extractDelta);
    }

    static Flux<String> parseStreamChunksToDeltas(Flux<String> chunks, java.util.function.Function<String, String> deltaExtractor) {
        AtomicReference<String> remainderRef = new AtomicReference<>("");
        return chunks
                .flatMapIterable(chunk -> {
                    if (chunk == null || chunk.isEmpty()) {
                        return List.<String>of();
                    }
                    String merged = remainderRef.get() + chunk;
                    String[] parts = merged.split("\\r?\\n", -1);
                    if (parts.length == 0) {
                        remainderRef.set(merged);
                        return List.<String>of();
                    }
                    remainderRef.set(parts[parts.length - 1]);
                    List<String> out = new ArrayList<>(Math.max(0, parts.length - 1));
                    for (int i = 0; i < parts.length - 1; i++) {
                        out.add(parts[i]);
                    }
                    return out;
                })
                .concatWith(Mono.fromCallable(() -> remainderRef.get()).filter(s -> s != null && !s.isEmpty()))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .filter(v -> !v.startsWith(":"))
                .filter(v -> !v.startsWith("event:"))
                .filter(v -> !v.startsWith("id:"))
                .filter(v -> !v.startsWith("retry:"))
                .filter(v -> !v.equals("data: [DONE]"))
                .map(v -> {
                    if (v.startsWith("data:")) {
                        return v.substring(5).trim();
                    }
                    return v;
                })
                .handle((v, sink) -> {
                    String delta;
                    try {
                        delta = deltaExtractor.apply(v);
                    } catch (Exception ignored) {
                        return;
                    }
                    if (delta != null && !delta.isEmpty()) {
                        sink.next(delta);
                        return;
                    }
                    String vv = v == null ? "" : v.trim();
                    if (!vv.isEmpty() && !vv.equals("[DONE]") && !vv.startsWith("{") && !vv.startsWith("[")) {
                        sink.next(vv);
                    }
                });
    }

    private synchronized void ensureWebClient() {
        if (this.webClient != null) return;
        this.webClient = WebClient.builder().build();
    }

    static String extractDeltaCompat(String json) {
        try {
            Object root = JSON.parseObject(json);
            if (root == null) return null;
            var obj = (com.alibaba.fastjson2.JSONObject) root;
            String ot = obj.getString("output_text");
            if (ot != null) return ot;
            var output = obj.getJSONObject("output");
            if (output != null) {
                String text = output.getString("text");
                if (text != null) return text;
                String outText = output.getString("output_text");
                if (outText != null) return outText;
            }

            var choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                if (output != null) {
                    choices = output.getJSONArray("choices");
                }
            }
            if (choices == null || choices.isEmpty()) return null;
            var first = choices.getJSONObject(0);
            if (first == null) return null;
            var delta = first.getJSONObject("delta");
            if (delta != null) {
                String c = delta.getString("content");
                if (c != null) return c;
            }
            var message = first.getJSONObject("message");
            if (message != null) {
                String c = message.getString("content");
                if (c != null) return c;
            }
            return first.getString("content");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractDelta(String json) {
        return extractDeltaCompat(json);
    }
    private void backoff(int attempt) {
        long millis = Math.min(1000L, 200L * (long)Math.pow(2, Math.max(0, attempt - 1)));
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
