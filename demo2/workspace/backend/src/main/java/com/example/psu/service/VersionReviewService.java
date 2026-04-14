package com.example.psu.service;

import com.example.psu.entity.PsuUnit;
import com.example.psu.entity.User;
import com.example.psu.entity.VersionReview;
import com.example.psu.repository.PsuRepository;
import com.example.psu.repository.UserRepository;
import com.example.psu.repository.VersionReviewRepository;
import com.example.psu.service.CodeGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本审核服务
 */
@Service
public class VersionReviewService {
    
    @Autowired
    private VersionReviewRepository versionReviewRepository;
    
    @Autowired
    private PsuRepository psuRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CodeGeneratorService codeGeneratorService;
    
    /**
     * 获取所有版本审核记录
     * @return 版本审核列表
     */
    public List<VersionReview> getAllVersionReviews() {
        return versionReviewRepository.findAll();
    }
    
    /**
     * 根据PSU ID获取版本审核记录
     * @param psuId PSU ID
     * @return 版本审核列表
     */
    public List<VersionReview> getVersionReviewsByPsuId(Long psuId) {
        return versionReviewRepository.findByPsuIdOrderBySubmittedAtDesc(psuId);
    }
    
    /**
     * 提交版本审核（业务）
     * @param psuId PSU ID
     * @param userId 用户ID
     * @return 提交的版本审核记录
     */
    public VersionReview submitVersionForReview(Long psuId, Long userId) {
        // 检查PSU是否存在
        PsuUnit psu = psuRepository.findById(psuId)
                .orElseThrow(() -> new RuntimeException("PSU not found: " + psuId));
        
        // 检查用户权限（只有业务用户可以提交审核）
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (user.getRole() != User.UserRole.BUSINESS) {
            throw new RuntimeException("Only business users can submit version for review");
        }
        
        // 检查是否已有待审核的版本
        List<VersionReview> pendingReviews = versionReviewRepository.findByStatus(VersionReview.ReviewStatus.PENDING);
        for (VersionReview review : pendingReviews) {
            if (review.getPsuId().equals(psuId)) {
                throw new RuntimeException("There is already a pending review for this PSU");
            }
        }
        
        // 创建新的版本审核记录
        VersionReview review = new VersionReview();
        review.setPsuId(psuId);
        review.setMajorVersion(1); // 简化实现，从1.0.0开始
        review.setMinorVersion(0);
        review.setPatchVersion(0);
        review.setStatus(VersionReview.ReviewStatus.PENDING);
        review.setSubmitterId(userId);
        review.setSubmittedAt(LocalDateTime.now());
        
        return versionReviewRepository.save(review);
    }
    
    /**
     * 审核版本（研发）
     * @param reviewId 审核ID
     * @param approved 是否批准
     * @param userId 用户ID
     * @param rejectionReason 驳回原因（如果不批准）
     * @return 审核后的记录
     */
    public VersionReview reviewVersion(Long reviewId, boolean approved, Long userId, String rejectionReason) {
        // 检查用户权限（只有研发可以审核）
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (user.getRole() != User.UserRole.DEVELOPER) {
            throw new RuntimeException("Only developers can review versions");
        }
        
        // 获取审核记录
        VersionReview review = versionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Version review not found: " + reviewId));
        
        if (review.getStatus() != VersionReview.ReviewStatus.PENDING) {
            throw new RuntimeException("Version review is not in pending status");
        }
        
        // 更新审核状态
        review.setReviewerId(userId);
        review.setReviewedAt(LocalDateTime.now());
        
        if (approved) {
            review.setStatus(VersionReview.ReviewStatus.APPROVED);
        } else {
            review.setStatus(VersionReview.ReviewStatus.REJECTED);
            review.setRejectionReason(rejectionReason);
        }
        
        return versionReviewRepository.save(review);
    }
    
    /**
     * 生成业务代码（研发）
     * @param psuId PSU ID
     * @param userId 用户ID
     * @return 生成的代码内容
     */
    public String generateBusinessCode(Long psuId, Long userId) {
        // 检查用户权限（只有研发可以生成代码）
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (user.getRole() != User.UserRole.DEVELOPER) {
            throw new RuntimeException("Only developers can generate business code");
        }
        
        // 使用代码生成服务生成完整业务代码
        String code = codeGeneratorService.generateCompleteBusinessCode(psuId);
        
        // 查找该PSU的最新版本记录并更新代码内容
        List<VersionReview> reviews = versionReviewRepository.findByPsuIdOrderBySubmittedAtDesc(psuId);
        if (!reviews.isEmpty()) {
            VersionReview review = reviews.get(0);
            review.setCodeContent(code);
            versionReviewRepository.save(review);
        }
        
        return code;
    }
    
    /**
     * 记录Git提交信息
     * @param reviewId 审核ID
     * @param commitHash 提交哈希
     */
    public void recordGitCommit(Long reviewId, String commitHash) {
        VersionReview review = versionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Version review not found: " + reviewId));
        
        review.setGitCommitHash(commitHash);
        versionReviewRepository.save(review);
    }
}