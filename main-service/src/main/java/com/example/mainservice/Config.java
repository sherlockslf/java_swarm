package com.example.mainservice;

import com.example.common.llm.LlmClient;
import com.example.common.llm.LlmClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Bean
    public LlmClient llmClient() {
        return LlmClientFactory.createQwenOnly();
    }
}
