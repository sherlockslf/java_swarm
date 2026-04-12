package com.example.psu.repository;

import com.example.psu.entity.VersionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 版本审核仓库接口
 */
@Repository
public interface VersionReviewRepository extends JpaRepository<VersionReview, Long> {
    
    /**
     * 根据PSU ID查找所有版本
     * @param psuId PSU ID
     * @return 版本列表
     */
    List<VersionReview> findByPsuIdOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(Long psuId);
    
    /**
     * 根据PSU ID和版本号查找
     * @param psuId PSU ID
     * @param majorVersion 主版本
     * @param minorVersion 次版本
     * @param patchVersion 修订版本
     * @return 版本审核记录
     */
    Optional<VersionReview> findByPsuIdAndMajorVersionAndMinorVersionAndPatchVersion(
            Long psuId, Integer majorVersion, Integer minorVersion, Integer patchVersion);
    
    /**
     * 根据状态查找版本
     * @param status 状态
     * @return 版本列表
     */
    List<VersionReview> findByStatus(VersionReview.ReviewStatus status);
}