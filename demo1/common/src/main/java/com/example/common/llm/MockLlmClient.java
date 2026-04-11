package com.example.common.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class MockLlmClient implements LlmClient {
    @Override
    public String generate(String systemPrompt, String userInput) {
        String seed = systemPrompt + "\n" + userInput;
        String hash = sha256(seed).substring(0, 8);
        return "[MOCK-LLM:" + hash + "] " + userInput;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
