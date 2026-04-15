package com.example.psu.controller;

import com.example.psu.dto.PsuCreateRequest;
import com.example.psu.entity.PsuUnit;
import com.example.psu.entity.User;
import com.example.psu.repository.UserRepository;
import com.example.psu.service.PsuService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 创建PSU（仅研发）
     * @param request 创建请求
     * @return 创建的PSU
     */
    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<PsuUnit> createPsu(@Valid @RequestBody PsuCreateRequest request) {
        Long currentUserId = getCurrentUserId();
        PsuUnit psu = psuService.createPsu(request, currentUserId);
        return ResponseEntity.ok(psu);
    }
    
    /**
     * 分页查询PSU列表（所有登录用户可访问）
     * @param page 页码（默认1）
     * @param size 每页大小（默认10）
     * @return 分页结果
     */
    @GetMapping
    public ResponseEntity<Page<PsuUnit>> getPsus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<PsuUnit> psus = psuService.getPsus(page, size);
        return ResponseEntity.ok(psus);
    }
    
    /**
     * 更新PSU信息（仅研发）
     * @param id PSU数据库ID
     * @param request 更新请求
     * @return 更新后的PSU
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<PsuUnit> updatePsu(@PathVariable Long id, @Valid @RequestBody PsuCreateRequest request) {
        PsuUnit psu = psuService.updatePsu(id, request);
        return ResponseEntity.ok(psu);
    }
    
    /**
     * 根据数据库ID获取PSU信息
     * @param id PSU数据库ID
     * @return PSU信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<PsuUnit> getPsuById(@PathVariable Long id) {
        PsuUnit psu = psuService.getPsuById(id);
        return ResponseEntity.ok(psu);
    }
    
    /**
     * 根据PSU ID（全局唯一ID）获取PSU信息
     * @param psuId PSU全局唯一ID
     * @return PSU信息
     */
    @GetMapping("/by-psu-id/{psuId}")
    public ResponseEntity<PsuUnit> getPsuByPsuId(@PathVariable String psuId) {
        PsuUnit psu = psuService.getPsuByPsuId(psuId);
        return ResponseEntity.ok(psu);
    }
    
    /**
     * 删除PSU（归档，仅研发）
     * @param id PSU数据库ID
     * @return 无内容
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<Void> deletePsu(@PathVariable Long id) {
        psuService.deletePsu(id);
        return ResponseEntity.noContent().build();
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