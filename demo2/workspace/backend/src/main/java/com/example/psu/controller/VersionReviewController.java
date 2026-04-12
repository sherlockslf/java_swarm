package com.example.psu.controller;

import com.example.psu.dto.request.ReviewRequest;
import com.example.psu.entity.VersionReview;
import com.example.psu.service.VersionReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 版本审核控制器
 */
@RestController
@RequestMapping("/api/versions")
public class VersionReviewController {
    
    @Autowired
    private VersionReviewService versionReviewService;
    
    /**
     * 提交版本审核（业务）
     * @param psuId PSU ID
     * @return 提交的版本审核记录
     */
    @PostMapping("/{psuId}/submit")
    public ResponseEntity<VersionReview> submitVersionForReview(@PathVariable Long psuId) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        VersionReview review = versionReviewService.submitVersionForReview(psuId, currentUserId);
        return ResponseEntity.ok(review);
    }
    
    /**
     * 审核版本（研发）
     * @param reviewId 审核ID
     * @param requestBody 审核请求
     * @return 审核后的记录
     */
    @PostMapping("/{reviewId}/review")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<VersionReview> reviewVersion(@PathVariable Long reviewId, @RequestBody ReviewRequest requestBody) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        VersionReview review = versionReviewService.reviewVersion(reviewId, requestBody.isApproved(), currentUserId, requestBody.getRejectionReason());
        return ResponseEntity.ok(review);
    }
    
    /**
     * 获取生成的代码
     * @param psuId PSU ID
     * @return 生成的代码内容
     */
    @GetMapping("/{psuId}/code")
    public ResponseEntity<String> getCode(@PathVariable Long psuId) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        String code = versionReviewService.generateBusinessCode(psuId, 1L); // 简化实现
        return ResponseEntity.ok(code);
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