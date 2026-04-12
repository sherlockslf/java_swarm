package com.example.psu.controller;

import com.example.psu.dto.PsuCreateRequest;
import com.example.psu.entity.PsuUnit;
import com.example.psu.service.PsuService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * PSU管理控制器
 */
@RestController
@RequestMapping("/api/psus")
public class PsuController {
    
    @Autowired
    private PsuService psuService;
    
    /**
     * 创建PSU（仅研发）
     * @param request 创建请求
     * @return 创建的PSU
     */
    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<PsuUnit> createPsu(@Valid @RequestBody PsuCreateRequest request) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        PsuUnit psu = psuService.createPsu(request, currentUserId);
        return ResponseEntity.ok(psu);
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