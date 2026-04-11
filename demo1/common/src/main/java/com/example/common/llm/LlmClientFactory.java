package com.example.common.llm;

import com.example.common.llm.qwen.QwenKeyPool;

public class LlmClientFactory {
    public static LlmClient createDefault() {
        return new MockLlmClient();
    }

    public static LlmClient createQwenOnly() {
        try {
            QwenKeyPool pool = QwenKeyPool.loadDefault();
            if (!pool.hasKeys()) {
                throw new IllegalStateException("No Qwen API keys found");
            }
            String base = defaultString(System.getenv("QWEN_BASE_URL"), "https://dashscope.aliyuncs.com/compatible-mode/v1");
            // String model = defaultString(System.getenv("QWEN_MODEL"), "qwen3.5-plus");
            String model = defaultString(System.getenv("QWEN_MODEL"), "qwen-plus");
            double temp = parseDouble(System.getenv("QWEN_TEMPERATURE"), 0.2);
            int timeout = parseInt(System.getenv("QWEN_TIMEOUT_MS"), 15000);
            return new RealQwenLlmClient(base, model, temp, timeout, pool);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Qwen LLM client", e);
        }
    }

    private static String defaultString(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }
    private static double parseDouble(String v, double d) {
        try { return v == null ? d : Double.parseDouble(v); } catch (Exception e) { return d; }
    }
    private static int parseInt(String v, int d) {
        try { return v == null ? d : Integer.parseInt(v); } catch (Exception e) { return d; }
    }
}
