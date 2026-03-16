package com.example.subagentservice;

import com.example.common.llm.LlmClient;
import com.example.common.llm.LlmClientFactory;
import com.example.common.util.DataPathResolver;
import com.example.subagentservice.repo.SubAgentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class Config {
    @Bean
    public SubAgentRepository subAgentRepository() throws Exception {
        Path dataRoot = DataPathResolver.resolveDirectory("SUBAGENT_DATA_DIR",
                Path.of("subagent-service", "data").toString(),
                "data"
        ).orElse(Path.of(System.getProperty("user.dir"), "subagent-service", "data"));
        SubAgentRepository repo = new SubAgentRepository(dataRoot);
        repo.load();
        return repo;
    }

    @Bean
    public LlmClient llmClient() {
        return LlmClientFactory.createQwenOnly();
    }
}
