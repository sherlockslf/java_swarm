package com.example.subagentservice.repo;

import com.example.common.dto.SubAgentInfo;
import com.example.common.util.DataPathResolver;
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
    private final Path goodsInfoPath;
    private volatile List<SubAgentDef> cache;

    public SubAgentRepository(Path dataRoot) {
        this(dataRoot, resolveDefaultGoodsInfoPath());
    }

    public SubAgentRepository(Path dataRoot, Path goodsInfoPath) {
        this.dataRoot = dataRoot;
        this.goodsInfoPath = goodsInfoPath;
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

    public String readJudgePrompt() {
        try {
            Path p1 = dataRoot.resolve("prompts").resolve("judge.txt");
            if (Files.exists(p1)) {
                return JsonUtils.readString(p1);
            }
            Path p2 = dataRoot.resolve("baonuanneiyi").resolve("prompts").resolve("judge.txt");
            if (Files.exists(p2)) {
                return JsonUtils.readString(p2);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public String readGoodsInfo() {
        return readGoodsInfo(null);
    }

    public String readGoodsInfo(SubAgentDef def) {
        try {
            Path p = goodsInfoPath;
            if (p != null && Files.exists(p)) {
                return JsonUtils.readString(p);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static Path resolveDefaultGoodsInfoPath() {
        return DataPathResolver.resolveFile(
                        "GOODS_INFO_FILE",
                        Path.of("common", "goodsInfo", "data-mapped.json").toString())
                .orElse(Path.of(System.getProperty("user.dir"), "common", "goodsInfo", "data-mapped.json"));
    }
}
