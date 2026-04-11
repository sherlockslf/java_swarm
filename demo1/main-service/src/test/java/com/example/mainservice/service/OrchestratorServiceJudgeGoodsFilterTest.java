package com.example.mainservice.service;

import com.example.common.dto.ShoppingRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrchestratorServiceJudgeGoodsFilterTest {
    @Test
    void buildJudgeUserInput_filtersGoodsInfoByReplaySku() throws Exception {
        String goodsInfo = "[{\"商品id\":\"1\",\"当前指导价\":100},{\"商品id\":\"2\",\"当前指导价\":200}]";
        String replay = "{\"skuId\":\"2\",\"quote\":199}";
        ShoppingRequest req = new ShoppingRequest();
        req.setRequest("test");

        Method method = OrchestratorService.class.getDeclaredMethod(
                "buildJudgeUserInput", ShoppingRequest.class, String.class, List.class
        );
        method.setAccessible(true);
        String userInput = (String) method.invoke(null, req, goodsInfo, List.of(replay));

        assertTrue(userInput.contains("\"商品id\":\"2\""));
        assertFalse(userInput.contains("\"商品id\":\"1\""));
    }
}

