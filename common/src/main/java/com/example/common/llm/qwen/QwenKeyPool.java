package com.example.common.llm.qwen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class QwenKeyPool {
    private final List<String> keys;
    private final AtomicInteger idx = new AtomicInteger(0);
    private final Semaphore semaphore;

    public QwenKeyPool(List<String> keys, int maxConcurrency) {
        this.keys = keys;
        this.semaphore = new Semaphore(maxConcurrency > 0 ? maxConcurrency : Math.max(1, keys.size() * 3));
    }

    public static QwenKeyPool loadDefault() throws IOException {
        String envPath = System.getenv("QWEN_KEYS_FILE");
        Path path;
        if (envPath != null && !envPath.isBlank()) {
            path = Path.of(envPath);
        } else {
            path = Path.of("d:/metaspace/trae/java_swarm/common/src/main/java/com/example/common/key/keys.txt");
        }
        List<String> keys = readKeys(path);
        int maxConn = 0;
        String mc = System.getenv("QWEN_MAX_CONN");
        if (mc != null) {
            try { maxConn = Integer.parseInt(mc.trim()); } catch (Exception ignored) {}
        }
        return new QwenKeyPool(keys, maxConn);
    }

    private static List<String> readKeys(Path path) throws IOException {
        List<String> out = new ArrayList<>();
        if (Files.exists(path)) {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String[] parts = content.split("[,\\n\\r]+");
            for (String p : parts) {
                String k = p.trim();
                if (!k.isEmpty()) out.add(k);
            }
        }
        return out;
    }

    public boolean hasKeys() {
        return keys != null && !keys.isEmpty();
    }

    public String nextKey() {
        if (!hasKeys()) return null;
        int i = Math.abs(idx.getAndIncrement());
        return keys.get(i % keys.size());
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    public void release() {
        semaphore.release();
    }
}
