package com.example.psu.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.psu.dto.request.CreatePromptRequest;
import com.example.psu.dto.request.UpdatePromptRequest;
import com.example.psu.dto.response.PromptFragmentResponse;
import com.example.psu.entity.PromptFragment;
import com.example.psu.entity.User;
import com.example.psu.repository.UserRepository;
import com.example.psu.service.PromptService;

import jakarta.validation.Valid;

/**
 * Prompt管理控制器
 */
@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    
    @Autowired
    private PromptService promptService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 获取Prompt片段
     * @param psuId PSU ID
     * @return Prompt片段列表
     */
    @GetMapping("/{psuId}")
    public ResponseEntity<List<PromptFragmentResponse>> getPromptFragments(@PathVariable Long psuId) {
        List<PromptFragment> fragments = promptService.getPromptFragments(psuId);
        List<PromptFragmentResponse> responses = fragments.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * 创建Prompt片段（仅研发可操作）
     * @param request 创建请求
     * @return 创建的片段
     */
    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<PromptFragmentResponse> createPromptFragment(@Valid @RequestBody CreatePromptRequest request) {
        Long currentUserId = getCurrentUserId();
        PromptFragment fragment = promptService.createPromptFragment(request, currentUserId);
        PromptFragmentResponse response = convertToResponse(fragment);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 更新Prompt片段（权限分级）
     * 规则：editable=true时所有人可编辑，editable=false（已定版）所有人不可修改
     * @param fragmentId 片段ID
     * @param requestBody 更新请求
     * @return 更新后的片段
     */
    @PutMapping("/{fragmentId}")
    public ResponseEntity<PromptFragmentResponse> updatePromptFragment(
            @PathVariable Long fragmentId, 
            @Valid @RequestBody UpdatePromptRequest requestBody) {
        Long currentUserId = getCurrentUserId();
        PromptFragment fragment = promptService.updatePromptFragment(fragmentId, requestBody.getContent(), currentUserId);
        PromptFragmentResponse response = convertToResponse(fragment);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除Prompt片段（仅研发可操作）
     * @param fragmentId 片段ID
     */
    @DeleteMapping("/{fragmentId}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<Void> deletePromptFragment(@PathVariable Long fragmentId) {
        Long currentUserId = getCurrentUserId();
        promptService.deletePromptFragment(fragmentId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 定版Prompt片段（仅运营可操作）
     * @param fragmentId 片段ID
     * @return 定版后的片段
     */
    @PostMapping("/{fragmentId}/finalize")
    public ResponseEntity<PromptFragmentResponse> finalizePromptFragment(@PathVariable Long fragmentId) {
        Long currentUserId = getCurrentUserId();
        PromptFragment fragment = promptService.finalizePromptFragment(fragmentId, currentUserId);
        PromptFragmentResponse response = convertToResponse(fragment);
        return ResponseEntity.ok(response);
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
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
            return user.getId();
        }
        return null;
    }
    
    /**
     * 将Prompt片段实体转换为响应DTO
     */
    private PromptFragmentResponse convertToResponse(PromptFragment fragment) {
        PromptFragmentResponse response = new PromptFragmentResponse();
        BeanUtils.copyProperties(fragment, response);
        response.setType(fragment.getType().getCode());
        return response;
    }
}