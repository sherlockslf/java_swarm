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
     * 开发侧Prompt管理页面获取指定PSU的Prompt片段列表
     * 参数：psuId-PSU数据库ID
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
     * 开发侧Prompt管理页面创建新Prompt片段
     * 参数：psuId-PSU数据库ID，fragmentKey-片段标识，type-类型(CORE_RULES/MESSAGE_TEMPLATE)，content-内容
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
     * 开发侧Prompt管理页面编辑更新Prompt片段内容
     * 参数：fragmentId-片段ID，content-新内容
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
     * 开发侧Prompt管理页面删除Prompt片段
     * 参数：fragmentId-片段ID
     */
    @DeleteMapping("/{fragmentId}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<Void> deletePromptFragment(@PathVariable Long fragmentId) {
        Long currentUserId = getCurrentUserId();
        promptService.deletePromptFragment(fragmentId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 业务侧Prompt管理页面定版Prompt片段（标记为不可编辑）
     * 参数：fragmentId-片段ID
     */
    @PostMapping("/{fragmentId}/finalize")
    public ResponseEntity<PromptFragmentResponse> finalizePromptFragment(@PathVariable Long fragmentId) {
        Long currentUserId = getCurrentUserId();
        PromptFragment fragment = promptService.finalizePromptFragment(fragmentId, currentUserId);
        PromptFragmentResponse response = convertToResponse(fragment);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 开发侧/业务侧Prompt测试页面测试Prompt效果
     * 参数：psuId-PSU数据库ID，requestBody-测试输入参数
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