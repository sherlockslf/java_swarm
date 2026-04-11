package com.example.common.llm;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RealQwenLlmClientStreamParseTest {
    @Test
    void parseStreamChunksToDeltas_handlesSplitLinesAndDone() {
        String j1 = "{\"choices\":[{\"delta\":{\"content\":\"你\"}}]}";
        String j2 = "{\"choices\":[{\"delta\":{\"content\":\"好\"}}]}";

        Flux<String> chunks = Flux.just(
                "data: " + j1,
                "\n\n",
                "data: " + j2 + "\n\n" + "data: [DONE]\n\n"
        );

        Function<String, String> extractor = (json) -> {
            try {
                var obj = JSON.parseObject(json);
                var choices = obj.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) return null;
                var first = choices.getJSONObject(0);
                if (first == null) return null;
                var delta = first.getJSONObject("delta");
                if (delta == null) return null;
                return delta.getString("content");
            } catch (Exception e) {
                return null;
            }
        };

        List<String> out = RealQwenLlmClient.parseStreamChunksToDeltas(chunks, extractor)
                .collectList()
                .block(Duration.ofSeconds(3));
        assertEquals(List.of("你", "好"), out);
    }

    @Test
    void parseStreamChunksToDeltas_acceptsNonDataJsonLines() {
        String j1 = "{\"choices\":[{\"delta\":{\"content\":\"A\"}}]}";

        Flux<String> chunks = Flux.just(
                j1 + "\n\n"
        );

        Function<String, String> extractor = (json) -> {
            try {
                var obj = JSON.parseObject(json);
                var choices = obj.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) return null;
                var first = choices.getJSONObject(0);
                if (first == null) return null;
                var delta = first.getJSONObject("delta");
                if (delta == null) return null;
                return delta.getString("content");
            } catch (Exception e) {
                return null;
            }
        };

        List<String> out = RealQwenLlmClient.parseStreamChunksToDeltas(chunks, extractor)
                .collectList()
                .block(Duration.ofSeconds(3));
        assertEquals(List.of("A"), out);
    }

    @Test
    void parseStreamChunksToDeltas_ignoresEventLinesAndAcceptsPlainTextData() {
        Flux<String> chunks = Flux.just(
                "event: message\n",
                "data: 你\n\n",
                "event: message\n",
                "data: 好\n\n",
                "data: [DONE]\n\n"
        );

        List<String> out = RealQwenLlmClient.parseStreamChunksToDeltas(chunks, RealQwenLlmClient::extractDeltaCompat)
                .collectList()
                .block(Duration.ofSeconds(3));
        assertEquals(List.of("你", "好"), out);
    }

    @Test
    void extractDeltaCompat_supportsOutputChoicesMessageContent() {
        String j = "{\"output\":{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}}";
        assertEquals("OK", RealQwenLlmClient.extractDeltaCompat(j));
    }
}
