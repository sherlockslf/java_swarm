package com.example.psu.controller;

import com.example.psu.dto.request.UpdatePromptRequest;
import com.example.psu.entity.PromptFragment;
import com.example.psu.service.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Prompt管理控制器
 */
@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    
    @Autowired
    private PromptService promptService;
    
    /**
     * 获取Prompt片段
     * @param psuId PSU ID
     * @return Prompt片段列表
     */
    @GetMapping("/{psuId}")
    public ResponseEntity<List<PromptFragment>> getPromptFragments(@PathVariable Long psuId) {
        List<PromptFragment> fragments = promptService.getPromptFragments(psuId);
        return ResponseEntity.ok(fragments);
    }
    
    /**
     * 更新Prompt片段（权限分级）
     * @param fragmentId 片段ID
     * @param requestBody 更新请求
     * @return 更新后的片段
     */
    @PutMapping("/{fragmentId}")
    public ResponseEntity<PromptFragment> updatePromptFragment(@PathVariable Long fragmentId, @RequestBody UpdatePromptRequest requestBody) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        PromptFragment fragment = promptService.updatePromptFragment(fragmentId, requestBody.getContent(), currentUserId);
        return ResponseEntity.ok(fragment);
    }
    
    /**
     * 测试Prompt效果
     * @param psuId PSU ID
     * @param requestBody 测试参数
     * @return 测试结果
     */
    @PostMapping("/{psuId}/test")
    public ResponseEntity<String> testPrompt(@PathVariable Long psuId, @RequestBody Map<String, Object> requestBody) {
        String result = promptService.testPrompt(psuId, requestBody);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 从SecurityContext获取当前用户ID
     * @return 用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            String username = authentication.getName();
            // 在实际应用中，这里需要通过UserService查询数据库获取用户ID
            // 为了简化，我们返回一个固定的ID，实际应用中需要查询数据库
            return 1L; // 这里应该通过用户名查询数据库获取真实用户ID
        }
        return null;
    }
}