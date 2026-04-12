package com.example.psu.controller;

import com.example.psu.dto.request.UpdateSchemaRequest;
import com.example.psu.entity.JsonSchema;
import com.example.psu.service.JsonSchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * JSON Schema管理控制器
 */
@RestController
@RequestMapping("/api/schemas")
public class JsonSchemaController {
    
    @Autowired
    private JsonSchemaService jsonSchemaService;
    
    /**
     * 获取Schema（研发可编辑，业务只读）
     * @param psuId PSU ID
     * @return Schema实体
     */
    @GetMapping("/{psuId}")
    public ResponseEntity<JsonSchema> getSchemaByPsuId(@PathVariable Long psuId) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        JsonSchema schema = jsonSchemaService.getSchemaByPsuId(psuId, currentUserId);
        return ResponseEntity.ok(schema);
    }
    
    /**
     * 更新Schema（仅研发）
     * @param psuId PSU ID
     * @param requestBody Schema内容和变更日志
     * @return 更新后的Schema
     */
    @PutMapping("/{psuId}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<JsonSchema> updateSchema(@PathVariable Long psuId, @RequestBody UpdateSchemaRequest requestBody) {
        // 从SecurityContext获取当前用户ID
        Long currentUserId = getCurrentUserId();
        JsonSchema schema = jsonSchemaService.updateSchema(psuId, requestBody.getSchemaContent(), currentUserId, requestBody.getChangeLog());
        return ResponseEntity.ok(schema);
    }
    
    /**
     * 获取Schema版本历史
     * @param psuId PSU ID
     * @return Schema版本列表
     */
    @GetMapping("/{psuId}/versions")
    public ResponseEntity<List<JsonSchema>> getSchemaVersions(@PathVariable Long psuId) {
        List<JsonSchema> versions = jsonSchemaService.getSchemaVersions(psuId);
        return ResponseEntity.ok(versions);
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