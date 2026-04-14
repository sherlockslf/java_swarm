package com.example.psu.controller;

import com.example.psu.entity.AuditLog;
import com.example.psu.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审计日志控制器（仅管理员使用）
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    
    @Autowired
    private AuditLogService auditLogService;
    
    /**
     * 获取所有审计日志（仅管理员）
     * @return 审计日志列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        List<AuditLog> logs = auditLogService.getAllAuditLogs();
        return ResponseEntity.ok(logs);
    }
    
    /**
     * 根据用户ID获取审计日志（仅管理员）
     * @param userId 用户ID
     * @return 审计日志列表
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getAuditLogsByUserId(@PathVariable Long userId) {
        List<AuditLog> logs = auditLogService.getAuditLogsByUserId(userId);
        return ResponseEntity.ok(logs);
    }
}
