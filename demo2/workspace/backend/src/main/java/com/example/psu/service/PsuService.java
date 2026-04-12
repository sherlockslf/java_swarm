package com.example.psu.service;

import com.example.psu.dto.PsuCreateRequest;
import com.example.psu.entity.PsuUnit;
import com.example.psu.repository.PsuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
            throw new RuntimeException("PSU ID already exists: " + request.getPsuId());
        }
        
        PsuUnit psu = new PsuUnit();
        psu.setPsuId(request.getPsuId());
        psu.setName(request.getName());
        psu.setDescription(request.getDescription());
        psu.setStatus(PsuUnit.PsuStatus.ACTIVE);
        psu.setCreatorId(creatorId);
        
        return psuRepository.save(psu);
    }
}