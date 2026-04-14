package com.example.psu.service;

import com.example.psu.entity.PromptFragment;
import com.example.psu.entity.PsuUnit;
import com.example.psu.entity.User;
import com.example.psu.repository.PromptFragmentRepository;
import com.example.psu.repository.PsuRepository;
import com.example.psu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt管理服务
 */
@Service
public class PromptService {
    
    @Autowired
    private PromptFragmentRepository promptFragmentRepository;
    
    @Autowired
    private PsuRepository psuRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 获取Prompt片段
     * @param psuId PSU ID
     * @return Prompt片段列表
     */
    public List<PromptFragment> getPromptFragments(Long psuId) {
        // 检查PSU是否存在
        PsuUnit psu = psuRepository.findById(psuId)
                .orElseThrow(() -> new RuntimeException("PSU not found: " + psuId));
        
        return promptFragmentRepository.findByPsuIdOrderBySortOrderAsc(psuId);
    }
    
    /**
     * 更新Prompt片段（权限分级）
     * 规则：
     * - editable=true：所有人可编辑
     * - editable=false（已定版）：所有人不可修改
     * @param fragmentId 片段ID
     * @param content 内容
     * @param userId 用户ID
     * @return 更新后的片段
     */
    public PromptFragment updatePromptFragment(Long fragmentId, String content, Long userId) {
        // 获取片段
        PromptFragment fragment = promptFragmentRepository.findById(fragmentId)
                .orElseThrow(() -> new RuntimeException("Prompt fragment not found: " + fragmentId));
        
        // 检查用户权限
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        // 已定版的Prompt（editable=false）所有人不可修改
        if (!fragment.getEditable()) {
            throw new RuntimeException("Prompt已定版，不允许修改");
        }
        
        // editable=true时，所有人可编辑
        fragment.setContent(content);
        
        return promptFragmentRepository.save(fragment);
    }
    
    /**
     * 定版Prompt片段（仅运营可操作）
     * @param fragmentId 片段ID
     * @param userId 用户ID
     * @return 定版后的片段
     */
    public PromptFragment finalizePromptFragment(Long fragmentId, Long userId) {
        // 获取片段
        PromptFragment fragment = promptFragmentRepository.findById(fragmentId)
                .orElseThrow(() -> new RuntimeException("Prompt fragment not found: " + fragmentId));
        
        // 检查用户权限（仅运营可定版）
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (user.getRole() != User.UserRole.BUSINESS) {
            throw new RuntimeException("只有运营人员可以定版Prompt");
        }
        
        // 如果已经定版，不允许重复定版
        if (!fragment.getEditable()) {
            throw new RuntimeException("Prompt已定版，无需重复操作");
        }
        
        // 定版：设置editable=false
        fragment.setEditable(false);
        
        return promptFragmentRepository.save(fragment);
    }
    
    /**
     * 测试Prompt效果
     * @param psuId PSU ID
     * @param inputParams 输入参数
     * @return 测试结果
     */
    public String testPrompt(Long psuId, Map<String, Object> inputParams) {
        // 获取所有Prompt片段
        List<PromptFragment> fragments = getPromptFragments(psuId);
        
        // 组装完整Prompt
        String fullPrompt = assembleFullPrompt(fragments, inputParams);
        
        // 这里应该调用大模型API进行测试
        // 为了演示，我们返回组装后的Prompt
        return "Test result for prompt: " + fullPrompt;
    }
    
    /**
     * 组装完整Prompt
     * @param fragments 片段列表
     * @param variables 变量映射
     * @return 完整Prompt
     */
    public String assembleFullPrompt(List<PromptFragment> fragments, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        
        // 按排序顺序组装
        List<PromptFragment> orderedFragments = fragments.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.toList());
        
        for (PromptFragment fragment : orderedFragments) {
            String content = fragment.getContent();
            
            // 替换变量（简单实现）
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                content = content.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
            }
            
            sb.append(content).append("\n");
        }
        
        return sb.toString().trim();
    }
}