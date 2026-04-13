package com.example.psu.service;

import com.example.psu.dto.PsuCreateRequest;
import com.example.psu.entity.PsuUnit;
import com.example.psu.repository.PsuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * PSU管理服务
 */
@Service
public class PsuService {
    
    @Autowired
    private PsuRepository psuRepository;
    
    /**
     * 创建PSU（仅研发可操作）
     * @param request 创建请求
     * @param creatorId 创建者ID
     * @return 创建的PSU
     */
    public PsuUnit createPsu(PsuCreateRequest request, Long creatorId) {
        // 检查PSU ID是否已存在
        if (psuRepository.existsByPsuId(request.getPsuId())) {
            // 唯一键冲突时返回409，便于前端给出可理解提示
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PSU ID already exists: " + request.getPsuId());
        }
        
        PsuUnit psu = new PsuUnit();
        psu.setPsuId(request.getPsuId());
        psu.setName(request.getName());
        psu.setDescription(request.getDescription());
        psu.setStatus(PsuUnit.PsuStatus.ACTIVE);
        psu.setCreatorId(creatorId);
        
        return psuRepository.save(psu);
    }
    
    /**
     * 分页查询PSU列表
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<PsuUnit> getPsus(int page, int size) {
        // 页码从0开始，所以减1
        int pageNum = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageNum, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return psuRepository.findAll(pageable);
    }
    
    /**
     * 更新PSU信息
     * @param id PSU数据库ID
     * @param request 更新请求
     * @return 更新后的PSU
     */
    public PsuUnit updatePsu(Long id, PsuCreateRequest request) {
        PsuUnit psu = psuRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSU not found with id: " + id));
        
        // 更新字段
        psu.setName(request.getName());
        psu.setDescription(request.getDescription());
        
        return psuRepository.save(psu);
    }
    
    /**
     * 删除PSU（归档）
     * @param id PSU数据库ID
     */
    public void deletePsu(Long id) {
        PsuUnit psu = psuRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSU not found with id: " + id));
        
        // 软删除：将状态改为ARCHIVED
        psu.setStatus(PsuUnit.PsuStatus.ARCHIVED);
        psuRepository.save(psu);
    }
}
