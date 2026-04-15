package com.example.psu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 版本审核实体类
 */
@Entity
@Table(name = "ai_prompt_version_reviews", 
       indexes = {
           @Index(name = "idx_version_reviews_psu_id", columnList = "psuId"),
           @Index(name = "idx_version_reviews_status", columnList = "status"),
           @Index(name = "idx_version_reviews_submitter", columnList = "submitterId"),
           @Index(name = "idx_version_reviews_reviewer", columnList = "reviewerId"),
           @Index(name = "idx_version_reviews_submitted", columnList = "submittedAt"),
           @Index(name = "idx_version_reviews_git_hash", columnList = "gitCommitHash")
       },
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"psuId", "majorVersion", "minorVersion", "patchVersion"})
       })
@Data
public class VersionReview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long psuId;                // 关联PSU ID
    
    @Column(nullable = false)
    private Integer majorVersion;      // 主版本
    
    @Column(nullable = false)
    private Integer minorVersion;      // 次版本  
    
    @Column(nullable = false)
    private Integer patchVersion;      // 修订版本
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;       // 状态：PENDING/APPROVED/REJECTED
    
    @Column(nullable = false)
    private Long submitterId;          // 提交者ID（业务）
    
    private Long reviewerId;           // 审核者ID（研发）
    
    private String rejectionReason;    // 驳回原因
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt; // 提交时间
    
    private LocalDateTime reviewedAt;  // 审核时间
    
    private String gitCommitHash;      // Git提交哈希
    
    @Column(columnDefinition = "LONGTEXT")
    private String codeContent;        // 生成的代码内容
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;   // 创建时间
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;   // 更新时间
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        submittedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * 审核状态枚举
     */
    public enum ReviewStatus {
        PENDING,     // 待审核
        APPROVED,    // 已通过
        REJECTED     // 已驳回
    }
}