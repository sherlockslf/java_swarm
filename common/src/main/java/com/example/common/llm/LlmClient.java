package com.example.common.llm;

public interface LlmClient {
    String generate(String systemPrompt, String userInput);
}
