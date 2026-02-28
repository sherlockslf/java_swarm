package com.example.subagentservice;

import com.example.common.llm.LlmClient;
import com.example.common.llm.LlmClientFactory;
import com.example.subagentservice.repo.SubAgentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class Config {
    @Bean
    public SubAgentRepository subAgentRepository() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        String env = System.getenv("SUBAGENT_DATA_DIR");
        Path prefer = (env != null && !env.isBlank()) ? Path.of(env) : null;
        Path candidateModule = projectRoot.resolve("subagent-service").resolve("data");
        Path candidateRoot = projectRoot.resolve("data");
        Path dataRoot;
        if (prefer != null && Files.isDirectory(prefer)) dataRoot = prefer;
        else if (Files.isDirectory(candidateModule)) dataRoot = candidateModule;
        else if (Files.isDirectory(candidateRoot)) dataRoot = candidateRoot;
        else dataRoot = candidateModule;
        SubAgentRepository repo = new SubAgentRepository(dataRoot);
        repo.load();
        return repo;
    }

    @Bean
    public LlmClient llmClient() {
        return LlmClientFactory.createQwenOnly();
    }
}
