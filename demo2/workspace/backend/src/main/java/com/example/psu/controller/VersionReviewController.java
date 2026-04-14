package com.example.psu.controller;

import com.example.psu.dto.request.ReviewRequest;
import com.example.psu.entity.User;
import com.example.psu.entity.VersionReview;
import com.example.psu.repository.UserRepository;
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
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 提交版本审核（业务）
     * @param psuId PSU ID
     * @return 提交的版本审核记录
     */
    @PostMapping("/{psuId}/submit")
    public ResponseEntity<VersionReview> submitVersionForReview(@PathVariable Long psuId) {
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
        Long currentUserId = getCurrentUserId();
        String code = versionReviewService.generateBusinessCode(psuId, currentUserId);
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
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
            return user.getId();
        }
        return null;
    }
}