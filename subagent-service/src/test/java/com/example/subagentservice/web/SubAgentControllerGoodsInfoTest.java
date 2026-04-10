package com.example.subagentservice.web;

import com.example.common.dto.SubAgentQuery;
import com.example.common.dto.SubAgentReply;
import com.example.common.llm.LlmClient;
import com.example.subagentservice.repo.SubAgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubAgentControllerGoodsInfoTest {
    @Test
    void reply_includesGoodsInfoBlock() throws Exception {
        Path dataRoot = Files.createTempDirectory("subagent-data");
        Files.writeString(
                dataRoot.resolve("subagents.json"),
                "[{\"id\":\"seller_a\",\"name\":\"A\",\"type\":\"shopping\",\"promptPath\":\"baonuanneiyi/prompts/seller_a.txt\",\"enabled\":true}]",
                StandardCharsets.UTF_8
        );
        Files.createDirectories(dataRoot.resolve(Path.of("baonuanneiyi", "prompts")));
        Files.writeString(
                dataRoot.resolve(Path.of("baonuanneiyi", "prompts", "seller_a.txt")),
                "你是一名导购，结合<GoodsInfo>推荐商品",
                StandardCharsets.UTF_8
        );
        Path goodsFile = Files.createTempFile("goods-mapped", ".json");
        Files.writeString(
                goodsFile,
                "[{\"商品id\":\"1\",\"商品名称\":\"X\",\"当前指导价\":99.9}]",
                StandardCharsets.UTF_8
        );

        SubAgentRepository repo = new SubAgentRepository(dataRoot, goodsFile);
        repo.load();

        AtomicReference<String> capturedUserInput = new AtomicReference<>("");
        LlmClient llm = (systemPrompt, userInput) -> {
            // 捕获 merged 输入用于断言
            capturedUserInput.set(userInput == null ? "" : userInput);
            return "ok";
        };

        SubAgentController controller = new SubAgentController(repo, llm);

        SubAgentQuery q = new SubAgentQuery();
        q.setQuestion("想买保暖内衣，预算200");
        q.setContext(Map.of("price", 200));

        ResponseEntity<?> resp = controller.reply("seller_a", q);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());

        String merged = capturedUserInput.get();
        assertTrue(merged.contains("<GoodsInfo>"));
        assertTrue(merged.contains("</GoodsInfo>"));
        assertTrue(merged.contains("\"商品id\""));
    }

    @Test
    void reply_quoteAlignsWithModelAnswer() throws Exception {
        Path dataRoot = Files.createTempDirectory("subagent-data");
        Files.writeString(
                dataRoot.resolve("subagents.json"),
                "[{\"id\":\"seller_a\",\"name\":\"A\",\"type\":\"shopping\",\"promptPath\":\"baonuanneiyi/prompts/seller_a.txt\",\"enabled\":true}]",
                StandardCharsets.UTF_8
        );
        Files.createDirectories(dataRoot.resolve(Path.of("baonuanneiyi", "prompts")));
        Files.writeString(
                dataRoot.resolve(Path.of("baonuanneiyi", "prompts", "seller_a.txt")),
                "你是一名导购",
                StandardCharsets.UTF_8
        );
        Path goodsFile = Files.createTempFile("goods-mapped", ".json");
        Files.writeString(
                goodsFile,
                "[{\"商品id\":\"1\",\"当前指导价\":99.9}]",
                StandardCharsets.UTF_8
        );

        SubAgentRepository repo = new SubAgentRepository(dataRoot, goodsFile);
        repo.load();
        LlmClient llm = (systemPrompt, userInput) -> "推荐：报价=89.9；说明：轻薄保暖";
        SubAgentController controller = new SubAgentController(repo, llm);

        SubAgentQuery q = new SubAgentQuery();
        q.setQuestion("想买保暖内衣，预算200");
        q.setContext(Map.of("price", 200));

        ResponseEntity<?> resp = controller.reply("seller_a", q);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        SubAgentReply reply = (SubAgentReply) resp.getBody();
        assertNotNull(reply);
        assertTrue(reply.getAnswer().contains("\"quote\":89.9"));
        assertTrue(reply.getAnswer().contains("\"product_id\""));
        assertEquals("89.9", String.valueOf(reply.getMeta().get("quote")));
    }
}
