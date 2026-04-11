package com.example.common.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "QWEN_KEYS_FILE", matches = ".*")
public class RealQwenLlmClientStreamingIT {
    @Test
    void streamTokens() throws Exception {
        LlmClient c = LlmClientFactory.createQwenOnly();
        if (!(c instanceof RealQwenLlmClient q)) return;
        AtomicInteger n = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(1);
        q.generateStream("你是一个简洁的助手", "请用一句话打个招呼", t -> {
            System.out.print(t);
            n.incrementAndGet();
        }, e -> {
            e.printStackTrace();
            done.countDown();
        }, done::countDown);
        done.await(30, TimeUnit.SECONDS);
        assertTrue(n.get() > 0);
    }
}
