package com.example.subagentservice.repo;

import com.example.common.dto.SubAgentInfo;
import com.example.common.util.JsonUtils;
import com.example.subagentservice.model.SubAgentDef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SubAgentRepository {
    private final Path dataRoot;
    private volatile List<SubAgentDef> cache;

    public SubAgentRepository(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public synchronized void load() throws IOException {
        Path json = dataRoot.resolve("subagents.json");
        if (Files.exists(json)) {
            this.cache = JsonUtils.readArray(json, SubAgentDef.class);
        } else {
            this.cache = Collections.emptyList();
        }
    }

    public List<SubAgentDef> listAll() {
        return cache == null ? Collections.emptyList() : cache;
    }

    public List<SubAgentInfo> listByType(String type) {
        return listAll().stream()
                .filter(SubAgentDef::isEnabled)
                .filter(sa -> type == null || type.isEmpty() || type.equals(sa.getType()))
                .map(sa -> {
                    SubAgentInfo info = new SubAgentInfo();
                    info.setId(sa.getId());
                    info.setName(sa.getName());
                    info.setType(sa.getType());
                    info.setEnabled(sa.isEnabled());
                    return info;
                })
                .collect(Collectors.toList());
    }

    public Optional<SubAgentDef> findById(String id) {
        return listAll().stream().filter(sa -> sa.getId().equals(id)).findFirst();
    }

    public String readPrompt(SubAgentDef def) throws IOException {
        return JsonUtils.readString(dataRoot.resolve(def.getPromptPath()));
    }

    public String readGoodsInfo(SubAgentDef def) {
        // 根据 promptPath 推导同一场景目录下的 goodsinfo/goos.txt 并读取
        try {
            Path p = resolveGoodsInfoPath(def);
            if (p != null && Files.exists(p)) {
                return JsonUtils.readString(p);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private Path resolveGoodsInfoPath(SubAgentDef def) {
        // 优先按“{scene}/prompts/*”推导为“{scene}/goodsinfo/goos.txt”，否则回退到 dataRoot/goodsinfo/goos.txt
        if (def == null || def.getPromptPath() == null || def.getPromptPath().isBlank()) {
            return dataRoot.resolve(Path.of("goodsinfo", "goos.txt"));
        }
        String pp = def.getPromptPath().replace('\\', '/');
        int idx = pp.indexOf("/prompts/");
        String rel = idx > 0 ? (pp.substring(0, idx) + "/goodsinfo/goos.txt") : "goodsinfo/goos.txt";
        return dataRoot.resolve(rel);
    }
}
