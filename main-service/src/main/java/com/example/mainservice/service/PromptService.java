package com.example.mainservice.service;

import com.example.common.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class PromptService {
    public String readBuyerPrompt(String profileId) {
        try {
            Path dataRoot = Path.of(System.getProperty("user.dir"), "data");
            return JsonUtils.readString(dataRoot.resolve(Path.of("prompts","buyer", profileId + ".txt")));
        } catch (Exception e) {
            return "";
        }
    }
    public Map readBuyerProfile(String profileId) {
        try {
            Path dataRoot = Path.of(System.getProperty("user.dir"), "data");
            return JsonUtils.readObject(dataRoot.resolve(Path.of("profiles","buyer", profileId + ".json")), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
