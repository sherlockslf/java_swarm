package com.example.subagentservice.web;

import com.alibaba.fastjson2.JSON;
import com.example.common.dto.SubAgentInfo;
import com.example.common.dto.SubAgentQuery;
import com.example.common.dto.SubAgentReply;
import com.example.common.llm.LlmClient;
import com.example.subagentservice.model.SubAgentDef;
import com.example.subagentservice.repo.SubAgentRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@RestController
@RequestMapping("/api")
@Validated
public class SubAgentController {
    private final SubAgentRepository repository;
    private final LlmClient llmClient;

    public SubAgentController(SubAgentRepository repository, LlmClient llmClient) {
        this.repository = repository;
        this.llmClient = llmClient;
    }

    @GetMapping("/subagents")
    public List<SubAgentInfo> list(@RequestParam(value = "type", required = false) String type) {
        return repository.listByType(type);
    }

    @PostMapping("/subagents/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable("id") String id, @RequestBody SubAgentQuery query) throws IOException {
        // 读取子 agent 定义与对应提示词
        Optional<SubAgentDef> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SubAgentDef def = opt.get();
        String prompt = repository.readPrompt(def);
        // 注入商品信息（<GoodsInfo>）并拼接 question/context，供提示词引用
        String goodsInfo = repository.readGoodsInfo(def);
        String merged = "PROMPT:\n" + prompt
                + "\n\n<GoodsInfo>\n" + goodsInfo + "\n</GoodsInfo>\n\nQUESTION:\n" + query.getQuestion()
                + "\n\nCONTEXT:\n" + JSON.toJSONString(query.getContext());
        try {
            log.info("SubAgent '{}' handle request: questionLen={}, ctxKeys={}", def.getId(),
                    merged.length(), query.getContext() == null ? 0 : query.getContext().size());
        } catch (Exception ignored) {}
        String base = llmClient.generate(prompt, merged);
        int budget = extractPrice(query.getContext());
        int delta = Math.abs(def.getId().hashCode()) % 500 + 50;
        int quote = Math.max(100, budget - delta);
        String answer = def.getName() + " 推荐方案：报价=" + quote + "；说明：" + base;
        Map<String, Object> meta = new HashMap<>();
        meta.put("model", "mock");
        meta.put("quote", quote);
        SubAgentReply reply = new SubAgentReply();
        reply.setAgentId(def.getId());
        reply.setAnswer(answer);
        reply.setMeta(meta);
        try {
            log.info("SubAgent '{}' reply success: quote={}", def.getId(), quote);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(reply);
    }

    private int extractPrice(Map<String, Object> ctx) {
        if (ctx == null) return 0;
        Object v = ctx.get("price");
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return 0;
    }
}
